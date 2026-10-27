package com.medchart.ehr.service;

import com.medchart.ehr.audit.PhiLogMasker;
import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderNotificationService {

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
     * Attempts multiple channels and escalates if not acknowledged.
     */
    @Async
    @AuditAccess(action = AuditAction.CREATE, resourceType = "CriticalAlert", description = "Send critical alert")
    public CompletableFuture<Void> sendCriticalAlert(
            Long providerId,
            Long patientId,
            String patientMrn,
            String alertType,
            String alertMessage,
            String severity) {
        
        log.warn("CRITICAL ALERT for patient {}: {} - {}", PhiLogMasker.maskMrn(patientMrn), alertType, alertMessage);
        
        // Step 1: Send immediate notification
        // Step 2: If not acknowledged in 15 minutes, escalate to supervisor
        // Step 3: If still not acknowledged, page on-call provider
        
        // For demo: just log the alert
        // In production: implement escalation chain
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Notify all providers in a care team about patient update.
     */
    @Async
    public CompletableFuture<List<NotificationResult>> notifyCareTeam(
            Long patientId,
            List<Long> providerIds,
            String subject,
            String message) {
        
        log.info("Notifying care team ({} providers) for patient {}", providerIds.size(), patientId);
        
        // In production: batch notifications for efficiency
        return CompletableFuture.completedFuture(List.of());
    }

    private void sendEmail(String email, String subject, String message) {
        // Mock email sending
        log.debug("Sending email to {}: {}", email, subject);
    }

    private void sendSms(String phone, String message) {
        // Mock SMS sending
        log.debug("Sending SMS to {}: {}", phone, message.substring(0, Math.min(50, message.length())));
    }

    private void createInAppNotification(Long providerId, String subject, String message, Map<String, Object> metadata) {
        // Mock in-app notification
        log.debug("Creating in-app notification for provider {}: {}", providerId, subject);
    }

    public enum NotificationType {
        EMAIL, SMS, IN_APP, ALL
    }

    @lombok.Data
    public static class NotificationResult {
        private Long providerId;
        private NotificationType type;
        private LocalDateTime sentAt;
        private boolean success;
        private String errorMessage;
    }
}
