package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async notification service for provider alerts.
 * 
 * PATTERN: Async Notification
 * - Use @Async for non-blocking notifications
 * - Support multiple notification channels (email, SMS, in-app)
 * - Log all notifications for audit trail
 * - Handle failures gracefully without blocking main flow
 * - Record a per-attempt delivery outcome (result object, metric, log) so a
 *   failed notification is distinguishable from a delivered one
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderNotificationService {

    static final String CRITICAL_ALERT_METRIC = "notification.critical_alert";
    static final String CARE_TEAM_METRIC = "notification.care_team";

    private final MeterRegistry meterRegistry;
    private final AuditService auditService;

    /**
     * PATTERN: Async notification with multiple channels
     * 
     * Send notification to provider without blocking the calling thread.
     * Supports email, SMS, and in-app notifications.
     */
    @Async
    public CompletableFuture<NotificationResult> notifyProvider(
            Long providerId, 
            String providerEmail,
            String providerPhone,
            NotificationType type,
            String subject,
            String message,
            Map<String, Object> metadata) {
        
        log.info("Sending {} notification to provider {}: {}", type, providerId, subject);
        
        NotificationResult result = new NotificationResult();
        result.setProviderId(providerId);
        result.setType(type);
        result.setSentAt(LocalDateTime.now());
        
        try {
            switch (type) {
                case EMAIL:
                    sendEmail(providerEmail, subject, message);
                    break;
                case SMS:
                    sendSms(providerPhone, message);
                    break;
                case IN_APP:
                    createInAppNotification(providerId, subject, message, metadata);
                    break;
                case ALL:
                    sendEmail(providerEmail, subject, message);
                    sendSms(providerPhone, message);
                    createInAppNotification(providerId, subject, message, metadata);
                    break;
            }
            result.setSuccess(true);
        } catch (Exception e) {
            log.error("Failed to send notification to provider {}", providerId, e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return CompletableFuture.completedFuture(result);
    }

    /**
     * PATTERN: Critical alert with escalation
     * 
     * For critical lab results or patient safety alerts.
     * Attempts multiple channels and escalates if the immediate channel fails.
     *
     * Every attempt is recorded three ways so a dropped alert is observable:
     * a {@link CriticalAlertResult} returned to the caller, the
     * {@value #CRITICAL_ALERT_METRIC} counter tagged by channel/outcome, and an
     * AuditEvent whose success flag reflects the actual delivery outcome.
     */
    @Async
    public CompletableFuture<CriticalAlertResult> sendCriticalAlert(
            Long providerId,
            Long patientId,
            String patientMrn,
            String alertType,
            String alertMessage,
            String severity) {
        
        log.warn("CRITICAL ALERT for patient {}: {} - {}", patientMrn, alertType, alertMessage);
        
        long startedAtNanos = System.nanoTime();
        CriticalAlertResult result = new CriticalAlertResult();
        result.setProviderId(providerId);
        result.setPatientId(patientId);
        result.setAlertType(alertType);
        result.setSeverity(severity);
        result.setStartedAt(LocalDateTime.now());

        // Step 1: immediate notification to the ordering provider
        AlertDelivery immediate = attemptDelivery(AlertChannel.IN_APP, EscalationStep.IMMEDIATE,
                () -> createInAppNotification(providerId, alertType, alertMessage,
                        Map.of("severity", severity, "patientMrn", patientMrn)));
        result.getDeliveries().add(immediate);
        result.setEscalationStep(EscalationStep.IMMEDIATE);

        // Step 2: page the on-call provider when the immediate channel fails.
        // Acknowledgement-based escalation (supervisor after 15 minutes) requires an
        // acknowledgement store and is not implemented here; alerts that are delivered
        // but never acknowledged are therefore not escalated.
        if (!immediate.isSuccess()) {
            AlertDelivery paged = attemptDelivery(AlertChannel.PAGER, EscalationStep.ON_CALL_PAGE,
                    () -> pageOnCallProvider(providerId, alertType, alertMessage, severity));
            result.getDeliveries().add(paged);
            result.setEscalationStep(EscalationStep.ON_CALL_PAGE);
        }

        boolean delivered = result.getDeliveries().stream().anyMatch(AlertDelivery::isSuccess);
        result.setDelivered(delivered);
        result.setCompletedAt(LocalDateTime.now());
        if (!delivered) {
            result.setErrorMessage(result.getDeliveries().get(result.getDeliveries().size() - 1).getErrorMessage());
            log.error("CRITICAL ALERT UNDELIVERED for patient {} (patientId={}): type={} severity={} provider={} error={}",
                    patientMrn, patientId, alertType, severity, providerId, result.getErrorMessage());
        }

        meterRegistry.counter(CRITICAL_ALERT_METRIC,
                "channel", "all",
                "outcome", delivered ? "delivered" : "undelivered").increment();
        meterRegistry.timer(CRITICAL_ALERT_METRIC + ".duration",
                "outcome", delivered ? "delivered" : "undelivered")
                .record(System.nanoTime() - startedAtNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

        recordCriticalAlertAudit(patientId, patientMrn, alertType, severity, result);

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Notify all providers in a care team about patient update.
     *
     * Returns one {@link NotificationResult} per provider so partial delivery is visible.
     */
    @Async
    public CompletableFuture<List<NotificationResult>> notifyCareTeam(
            Long patientId,
            List<Long> providerIds,
            String subject,
            String message) {
        
        log.info("Notifying care team ({} providers) for patient {}", providerIds.size(), patientId);
        
        // In production: batch notifications for efficiency
        List<NotificationResult> results = new ArrayList<>();
        for (Long providerId : providerIds) {
            NotificationResult result = new NotificationResult();
            result.setProviderId(providerId);
            result.setType(NotificationType.IN_APP);
            result.setSentAt(LocalDateTime.now());
            try {
                createInAppNotification(providerId, subject, message,
                        Map.of("patientId", patientId));
                result.setSuccess(true);
            } catch (Exception e) {
                log.error("Failed to notify care team provider {} for patient {}", providerId, patientId, e);
                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
            }
            meterRegistry.counter(CARE_TEAM_METRIC,
                    "channel", NotificationType.IN_APP.name(),
                    "outcome", result.isSuccess() ? "delivered" : "failed").increment();
            results.add(result);
        }
        
        return CompletableFuture.completedFuture(results);
    }

    private AlertDelivery attemptDelivery(AlertChannel channel, EscalationStep step, Runnable delivery) {
        AlertDelivery attempt = new AlertDelivery();
        attempt.setChannel(channel);
        attempt.setEscalationStep(step);
        attempt.setAttemptedAt(LocalDateTime.now());
        try {
            delivery.run();
            attempt.setSuccess(true);
        } catch (Exception e) {
            attempt.setSuccess(false);
            attempt.setErrorMessage(e.getMessage());
            log.error("Critical alert delivery failed on channel {} at step {}", channel, step, e);
        }
        meterRegistry.counter(CRITICAL_ALERT_METRIC,
                "channel", channel.name(),
                "outcome", attempt.isSuccess() ? "delivered" : "failed").increment();
        return attempt;
    }

    private void recordCriticalAlertAudit(Long patientId, String patientMrn, String alertType,
                                          String severity, CriticalAlertResult result) {
        auditService.saveAuditEventAsync(AuditEvent.builder()
                .userId("system")
                .userName("System User")
                .patientId(patientId)
                .patientMrn(patientMrn)
                .action(AuditAction.CREATE)
                .resourceType("CriticalAlert")
                .description(String.format("Send critical alert: type=%s severity=%s escalation=%s",
                        alertType, severity, result.getEscalationStep()))
                .success(result.isDelivered())
                .errorMessage(result.getErrorMessage())
                .build());
    }

    private void sendEmail(String email, String subject, String message) {
        // Mock email sending
        log.debug("Sending email to {}: {}", email, subject);
    }

    private void sendSms(String phone, String message) {
        // Mock SMS sending
        log.debug("Sending SMS to {}: {}", phone, message.substring(0, Math.min(50, message.length())));
    }

    protected void createInAppNotification(Long providerId, String subject, String message, Map<String, Object> metadata) {
        // Mock in-app notification
        log.debug("Creating in-app notification for provider {}: {}", providerId, subject);
    }

    protected void pageOnCallProvider(Long providerId, String alertType, String message, String severity) {
        // Mock pager escalation
        log.debug("Paging on-call provider for provider {}: {} ({})", providerId, alertType, severity);
    }

    public enum NotificationType {
        EMAIL, SMS, IN_APP, ALL
    }

    public enum AlertChannel {
        EMAIL, SMS, IN_APP, PAGER
    }

    public enum EscalationStep {
        IMMEDIATE, SUPERVISOR_ESCALATION, ON_CALL_PAGE
    }

    @lombok.Data
    public static class NotificationResult {
        private Long providerId;
        private NotificationType type;
        private LocalDateTime sentAt;
        private boolean success;
        private String errorMessage;
    }

    @lombok.Data
    public static class AlertDelivery {
        private AlertChannel channel;
        private EscalationStep escalationStep;
        private LocalDateTime attemptedAt;
        private boolean success;
        private String errorMessage;
    }

    @lombok.Data
    public static class CriticalAlertResult {
        private Long providerId;
        private Long patientId;
        private String alertType;
        private String severity;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private EscalationStep escalationStep;
        private boolean delivered;
        private String errorMessage;
        private final List<AlertDelivery> deliveries = new ArrayList<>();
    }
}
