package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * HIPAA-compliant audit logging for medication-related actions.
 * 
 * Extends PatientAccessLogger with medication-specific logging:
 * - Prescription creation/modification
 * - Controlled substance access
 * - E-prescribing transactions
 * - Pharmacy communications
 * 
 * PATTERN: Follow PatientAccessLogger pattern
 * Required for DEA compliance (controlled substances) and HIPAA.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MedicationAuditLogger {

    private final AuditEventRepository auditEventRepository;

    /**
     * Log prescription creation.
     */
    public void logPrescriptionCreated(Long userId, String userRole, Long patientId,
                                        String patientMrn, Long medicationOrderId,
                                        String medicationName, boolean isControlled,
                                        String drugSchedule, String ipAddress) {
        
        String action = isControlled ? "PRESCRIBE_CONTROLLED" : "PRESCRIBE";
        String description = String.format("Prescribed %s%s for patient %s",
                medicationName,
                isControlled ? " (Schedule " + drugSchedule + ")" : "",
                patientMrn);
        
        AuditEvent event = new AuditEvent();
        event.setUserId(String.valueOf(userId));
        event.setUserName(userRole);
        event.setPatientId(patientId);
        event.setPatientMrn(patientMrn);
        event.setAction(isControlled ? AuditAction.CREATE : AuditAction.CREATE);
        event.setResourceType("MedicationOrder");
        event.setResourceId(medicationOrderId);
        event.setDescription(description);
        event.setIpAddress(ipAddress);
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess(true);
        
        auditEventRepository.save(event);
        
        if (isControlled) {
            log.warn("CONTROLLED SUBSTANCE: User {} prescribed {} ({}) to patient id={}", 
                    userId, medicationName, drugSchedule, patientId);
        } else {
            log.info("PRESCRIPTION: User {} prescribed {} to patient id={}", 
                    userId, medicationName, patientId);
        }
    }

    /**
     * Log prescription modification.
     */
    public void logPrescriptionModified(Long userId, String userRole, Long patientId,
                                         String patientMrn, Long medicationOrderId,
                                         String medicationName, String changeType,
                                         String oldValue, String newValue, String ipAddress) {
        
        String description = String.format("Modified %s for patient %s: %s changed from '%s' to '%s'",
                medicationName, patientMrn, changeType, oldValue, newValue);
        
        AuditEvent event = new AuditEvent();
        event.setUserId(String.valueOf(userId));
        event.setUserName(userRole);
        event.setPatientId(patientId);
        event.setPatientMrn(patientMrn);
        event.setAction(AuditAction.UPDATE);
        event.setResourceType("MedicationOrder");
        event.setResourceId(medicationOrderId);
        event.setDescription(description);
        event.setIpAddress(ipAddress);
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess(true);
        
        auditEventRepository.save(event);
        
        log.info("PRESCRIPTION MODIFIED: {}", description);
    }

    /**
     * Log prescription cancellation.
     */
    public void logPrescriptionCancelled(Long userId, String userRole, Long patientId,
                                          String patientMrn, Long medicationOrderId,
                                          String medicationName, String cancelReason,
                                          String ipAddress) {
        
        String description = String.format("Cancelled %s for patient %s - Reason: %s",
                medicationName, patientMrn, cancelReason);
        
        AuditEvent event = new AuditEvent();
        event.setUserId(String.valueOf(userId));
        event.setUserName(userRole);
        event.setPatientId(patientId);
        event.setPatientMrn(patientMrn);
        event.setAction(AuditAction.DELETE);
        event.setResourceType("MedicationOrder");
        event.setResourceId(medicationOrderId);
        event.setDescription(description);
        event.setIpAddress(ipAddress);
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess(true);
        
        auditEventRepository.save(event);
        
        log.info("PRESCRIPTION CANCELLED: {}", description);
    }

    /**
     * Log e-prescribing transaction.
     */
    public void logEprescribeTransaction(Long userId, String userRole, Long patientId,
                                          String patientMrn, Long medicationOrderId,
                                          String transactionType, String pharmacyNcpdpId,
                                          String status, String ipAddress) {
        
        String description = String.format("E-prescribe %s to pharmacy %s for patient %s - Status: %s",
                transactionType, pharmacyNcpdpId, patientMrn, status);
        
        AuditEvent event = new AuditEvent();
        event.setUserId(String.valueOf(userId));
        event.setUserName(userRole);
        event.setPatientId(patientId);
        event.setPatientMrn(patientMrn);
        event.setAction(AuditAction.EXPORT);
        event.setResourceType("MedicationOrder");
        event.setResourceId(medicationOrderId);
        event.setDescription(description);
        event.setIpAddress(ipAddress);
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess("SUCCESS".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status));
        
        auditEventRepository.save(event);
        
        log.info("EPRESCRIBE: {}", description);
    }

    /**
     * Log controlled substance verification (PDMP check).
     */
    public void logPdmpCheck(Long userId, String userRole, Long patientId,
                             String patientMrn, String state, String ipAddress) {
        
        String description = String.format("PDMP check for patient %s in state %s", patientMrn, state);
        
        AuditEvent event = new AuditEvent();
        event.setUserId(String.valueOf(userId));
        event.setUserName(userRole);
        event.setPatientId(patientId);
        event.setPatientMrn(patientMrn);
        event.setAction(AuditAction.READ);
        event.setResourceType("Patient");
        event.setDescription(description);
        event.setIpAddress(ipAddress);
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess(true);
        
        auditEventRepository.save(event);
        
        log.info("PDMP CHECK: {}", description);
    }

    /**
     * Log medication history access.
     */
    public void logMedicationHistoryAccess(Long userId, String userRole, Long patientId,
                                            String patientMrn, String source,
                                            int recordCount, String ipAddress) {
        
        String description = String.format("Accessed medication history for patient %s from %s (%d records)",
                patientMrn, source, recordCount);
        
        AuditEvent event = new AuditEvent();
        event.setUserId(String.valueOf(userId));
        event.setUserName(userRole);
        event.setPatientId(patientId);
        event.setPatientMrn(patientMrn);
        event.setAction(AuditAction.READ);
        event.setResourceType("MedicationHistory");
        event.setDescription(description);
        event.setIpAddress(ipAddress);
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess(true);
        
        auditEventRepository.save(event);
        
        log.info("MEDICATION HISTORY: {}", description);
    }
}
