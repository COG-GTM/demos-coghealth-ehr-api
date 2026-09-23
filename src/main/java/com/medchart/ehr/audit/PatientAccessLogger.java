package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HIPAA-compliant patient access logging.
 * 
 * PATTERN: Audit Logging
 * All PHI access must be logged with:
 * - Who accessed (user ID, role)
 * - What was accessed (patient ID, data type)
 * - When (timestamp)
 * - Why (action, reason)
 * - From where (IP, session)
 * 
 * This pattern MUST be followed for any new service that accesses PHI.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PatientAccessLogger {

    private final AuditEventRepository auditEventRepository;

    /**
     * PATTERN: Log PHI access
     * 
     * Call this method whenever patient data is accessed.
     * Required fields: userId, patientId, action, resourceType
     * Optional but recommended: reason, ipAddress, sessionId
     */
    public void logAccess(
            Long userId,
            String userRole,
            Long patientId,
            String patientMrn,
            AuditAction action,
            String resourceType,
            String reason,
            String ipAddress,
            String sessionId) {
        
        AuditEvent event = new AuditEvent();
        event.setUserId(String.valueOf(userId));
        event.setUserName(userRole);
        event.setPatientId(patientId);
        event.setPatientMrn(patientMrn);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setDescription(reason);
        event.setIpAddress(ipAddress);
        event.setSessionId(sessionId);
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess(true);
        
        auditEventRepository.save(event);
        
        log.info("AUDIT: User {} ({}) accessed {} for patient {} - Action: {}", 
            userId, userRole, resourceType, patientMrn, action);
    }

    /**
     * Log failed access attempt (e.g., unauthorized access).
     */
    public void logFailedAccess(
            Long userId,
            String userRole,
            Long patientId,
            AuditAction action,
            String resourceType,
            String reason,
            String ipAddress) {
        
        AuditEvent event = new AuditEvent();
        event.setUserId(String.valueOf(userId));
        event.setUserName(userRole);
        event.setPatientId(patientId);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setDescription(reason);
        event.setIpAddress(ipAddress);
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess(false);
        
        auditEventRepository.save(event);
        
        log.warn("AUDIT FAILURE: User {} ({}) denied access to {} for patient {} - Reason: {}", 
            userId, userRole, resourceType, patientId, reason);
    }

    /**
     * Log bulk data access (e.g., reports, exports).
     * HIPAA requires special logging for bulk PHI access.
     */
    public void logBulkAccess(
            Long userId,
            String userRole,
            AuditAction action,
            String resourceType,
            int recordCount,
            String reason,
            String ipAddress) {
        
        logBulkAccess(String.valueOf(userId), userRole, action, resourceType, null,
            recordCount, reason, ipAddress, null);
    }

    /**
     * Log bulk data access for a named principal.
     * Used by report and export flows, where the actor is resolved from the security context.
     */
    public void logBulkAccess(
            String userId,
            String userRole,
            AuditAction action,
            String resourceType,
            Long patientId,
            int recordCount,
            String reason,
            String ipAddress,
            String sessionId) {
        
        log.warn("AUDIT BULK: User {} ({}) accessed {} {} records - Reason: {}", 
            userId, userRole, recordCount, resourceType, reason);
        
        auditEventRepository.save(
            bulkEvent(userId, userRole, action, resourceType, patientId, reason, ipAddress, sessionId,
                " [BULK: " + recordCount + " records]", true, null));
    }

    /**
     * Log a bulk access attempt that failed, so aborted exports stay reconstructable.
     */
    public void logFailedBulkAccess(
            String userId,
            String userRole,
            AuditAction action,
            String resourceType,
            Long patientId,
            String reason,
            String ipAddress,
            String sessionId,
            String errorMessage) {
        
        log.warn("AUDIT BULK FAILURE: User {} ({}) failed bulk {} of {} - Reason: {}", 
            userId, userRole, action, resourceType, reason);
        
        auditEventRepository.save(
            bulkEvent(userId, userRole, action, resourceType, patientId, reason, ipAddress, sessionId,
                " [BULK: failed]", false, errorMessage));
    }

    private AuditEvent bulkEvent(
            String userId,
            String userRole,
            AuditAction action,
            String resourceType,
            Long patientId,
            String reason,
            String ipAddress,
            String sessionId,
            String descriptionSuffix,
            boolean success,
            String errorMessage) {
        
        AuditEvent event = new AuditEvent();
        event.setUserId(userId);
        event.setUserName(userRole);
        event.setPatientId(patientId);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setDescription(truncate(reason + descriptionSuffix, 500));
        event.setIpAddress(ipAddress);
        event.setSessionId(sessionId);
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess(success);
        event.setErrorMessage(truncate(errorMessage, 500));
        
        return event;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Generate audit report for compliance review.
     */
    public Map<String, Object> generateAccessReport(Long patientId, LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> report = new HashMap<>();
        report.put("patientId", patientId);
        report.put("startDate", startDate);
        report.put("endDate", endDate);
        report.put("generatedAt", LocalDateTime.now());
        
        // In production: query audit events and aggregate
        report.put("totalAccesses", 0);
        report.put("uniqueUsers", 0);
        report.put("accessByType", Map.of());
        
        return report;
    }
}
