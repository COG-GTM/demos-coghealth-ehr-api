package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Records HIPAA bulk-disclosure audit events for report and export flows.
 *
 * Reasons and resource types must describe the disclosure only; never pass
 * SSN, MRN or other identifier values.
 */
@Component
@RequiredArgsConstructor
public class BulkExportAuditor {

    private final PatientAccessLogger patientAccessLogger;
    private final AuditContext auditContext;

    public void recordExport(String resourceType, Long patientId, int recordCount, String reason) {
        patientAccessLogger.logBulkAccess(
                auditContext.getUserId(),
                auditContext.getUserRole(),
                AuditAction.EXPORT,
                resourceType,
                patientId,
                recordCount,
                reason,
                auditContext.getIpAddress(),
                auditContext.getSessionId());
    }

    public void recordFailedExport(String resourceType, Long patientId, String reason, Exception error) {
        patientAccessLogger.logFailedBulkAccess(
                auditContext.getUserId(),
                auditContext.getUserRole(),
                AuditAction.EXPORT,
                resourceType,
                patientId,
                reason,
                auditContext.getIpAddress(),
                auditContext.getSessionId(),
                error != null ? error.toString() : null);
    }
}
