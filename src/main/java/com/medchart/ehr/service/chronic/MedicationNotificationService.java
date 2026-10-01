package com.medchart.ehr.service.chronic;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.service.ProviderNotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Notification service for chronic medication management alerts.
 * 
 * Sends alerts for:
 * - Medication non-adherence
 * - Care gaps (overdue labs, screenings)
 * - Critical lab results (HbA1c > 9%)
 * - Refill reminders
 * 
 * PATTERN: Follow ProviderNotificationService async pattern
 * - Use @Async for non-blocking
 * - Support multiple channels
 * - Log all notifications for audit
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MedicationNotificationService {

    private static final String ALERT_METRIC = "chronic.alert";

    private final ProviderNotificationService providerNotificationService;
    private final PatientAccessLogger accessLogger;
    private final MeterRegistry meterRegistry;

    /**
     * Alert provider about medication non-adherence.
     */
    @Async
    @AuditAccess(action = AuditAction.CREATE, resourceType = "Alert",
                 description = "Send non-adherence alert")
    public CompletableFuture<Void> sendNonAdherenceAlert(Long providerId, Long patientId,
                                                          String patientMrn, String patientName,
                                                          String medicationName, double pdcScore) {
        log.warn("ALERT: Patient {} ({}) non-adherent to {} - PDC: {}%", 
                patientName, patientMrn, medicationName, pdcScore * 100);
        
        String subject = "Medication Non-Adherence Alert: " + patientName;
        String message = String.format(
            "Patient %s (MRN: %s) has a PDC score of %.1f%% for %s. " +
            "Consider outreach to discuss barriers to medication adherence.",
            patientName, patientMrn, pdcScore * 100, medicationName
        );
        
        return providerNotificationService.notifyProvider(
            providerId, null, null,
            ProviderNotificationService.NotificationType.IN_APP,
            subject, message,
            Map.of(
                "alertType", "NON_ADHERENCE",
                "patientId", patientId,
                "patientMrn", patientMrn,
                "medicationName", medicationName,
                "pdcScore", pdcScore
            )
        ).thenAccept(result -> {
            log.info("Non-adherence alert sent to provider {}: success={}", providerId, result.isSuccess());
            countAlert("NON_ADHERENCE", result.isSuccess() ? "success" : "failure");
        });
    }

    /**
     * Alert provider about care gap.
     */
    @Async
    @AuditAccess(action = AuditAction.CREATE, resourceType = "Alert",
                 description = "Send care gap alert")
    public CompletableFuture<Void> sendCareGapAlert(Long providerId, Long patientId,
                                                     String patientMrn, String patientName,
                                                     String gapType, String gapDescription) {
        log.info("CARE GAP: Patient {} ({}) - {}: {}", 
                patientName, patientMrn, gapType, gapDescription);
        
        String subject = "Care Gap Alert: " + patientName + " - " + gapType;
        String message = String.format(
            "Patient %s (MRN: %s) has an identified care gap:\n\n%s\n\n" +
            "Please address during next visit or schedule follow-up.",
            patientName, patientMrn, gapDescription
        );
        
        return providerNotificationService.notifyProvider(
            providerId, null, null,
            ProviderNotificationService.NotificationType.IN_APP,
            subject, message,
            Map.of(
                "alertType", "CARE_GAP",
                "patientId", patientId,
                "patientMrn", patientMrn,
                "gapType", gapType
            )
        ).thenAccept(result -> {
            log.info("Care gap alert sent to provider {}: success={}", providerId, result.isSuccess());
            countAlert("CARE_GAP", result.isSuccess() ? "success" : "failure");
        });
    }

    /**
     * Alert provider about critical lab result (e.g., HbA1c > 9%).
     * 
     * PATTERN: Follow critical alert escalation pattern
     */
    @Async
    @AuditAccess(action = AuditAction.CREATE, resourceType = "Alert",
                 description = "Send critical lab alert")
    public CompletableFuture<Void> sendCriticalLabAlert(Long providerId, Long patientId,
                                                         String patientMrn, String patientName,
                                                         String labName, String labValue,
                                                         String criticalThreshold) {
        log.warn("CRITICAL LAB: Patient {} ({}) - {} = {} (threshold: {})", 
                patientName, patientMrn, labName, labValue, criticalThreshold);
        
        return providerNotificationService.sendCriticalAlert(
            providerId, patientId, patientMrn,
            "CRITICAL_LAB",
            String.format("%s result %s exceeds critical threshold %s", labName, labValue, criticalThreshold),
            "HIGH"
        ).whenComplete((ignored, error) -> {
            if (error == null) {
                log.info("Critical lab alert sent to provider {} for patient {}: success=true",
                        providerId, patientMrn);
                countAlert("CRITICAL_LAB", "success");
            } else {
                log.error("Critical lab alert failed for provider {} and patient {}",
                        providerId, patientMrn, error);
                countAlert("CRITICAL_LAB", "failure");
            }
        });
    }

    /**
     * Send refill reminder to patient (via patient portal or SMS).
     */
    @Async
    public CompletableFuture<Void> sendRefillReminder(Long patientId, String patientMrn,
                                                       String patientPhone, String medicationName,
                                                       java.time.LocalDate refillDueDate) {
        log.info("Sending refill reminder to patient {} for {} due {}", 
                patientMrn, medicationName, refillDueDate);
        
        // TODO: Implement patient notification
        // 1. Check patient communication preferences
        // 2. Send via preferred channel (SMS, email, portal)
        // 3. Record the delivery outcome below instead of the not_delivered signal
        
        log.warn("Refill reminder for patient {} and {} was not delivered: patient notification is not implemented",
                patientMrn, medicationName);
        countAlert("REFILL_REMINDER", "not_delivered");
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Send daily digest of chronic care alerts to provider.
     */
    @Async
    public CompletableFuture<Void> sendDailyDigest(Long providerId, String providerEmail,
                                                    int nonAdherenceCount, int careGapCount,
                                                    int criticalLabCount) {
        log.info("Sending daily digest to provider {}: {} non-adherence, {} care gaps, {} critical labs",
                providerId, nonAdherenceCount, careGapCount, criticalLabCount);
        
        if (nonAdherenceCount == 0 && careGapCount == 0 && criticalLabCount == 0) {
            log.debug("No alerts for provider {} - skipping digest", providerId);
            return CompletableFuture.completedFuture(null);
        }
        
        String subject = String.format("Daily Chronic Care Digest: %d items need attention",
                nonAdherenceCount + careGapCount + criticalLabCount);
        
        String message = String.format(
            "Your daily chronic care summary:\n\n" +
            "- %d patients with medication adherence issues\n" +
            "- %d care gaps identified\n" +
            "- %d critical lab results\n\n" +
            "Please review in the Chronic Care Dashboard.",
            nonAdherenceCount, careGapCount, criticalLabCount
        );
        
        return providerNotificationService.notifyProvider(
            providerId, providerEmail, null,
            ProviderNotificationService.NotificationType.EMAIL,
            subject, message,
            Map.of("alertType", "DAILY_DIGEST")
        ).thenAccept(result -> {
            log.info("Daily digest sent to provider {}: success={}", providerId, result.isSuccess());
            countAlert("DAILY_DIGEST", result.isSuccess() ? "success" : "failure");
        });
    }

    private void countAlert(String alertType, String outcome) {
        meterRegistry.counter(ALERT_METRIC, "type", alertType, "outcome", outcome).increment();
    }
}
