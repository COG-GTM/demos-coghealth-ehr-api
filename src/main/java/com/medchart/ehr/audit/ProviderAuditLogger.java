package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Audit trail for provider directory state changes (create, update, deactivate).
 *
 * Provider records are not PHI, so only administrative identifiers (id, NPI) are recorded.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderAuditLogger {

    private static final String RESOURCE_TYPE = "Provider";

    private final AuditEventRepository auditEventRepository;
    private final RequestAuditContext auditContext;

    public void logChange(AuditAction action, Long providerId, String npi, String description) {
        record(action, providerId, npi, description, true, null);
    }

    public void logFailedChange(AuditAction action, Long providerId, String npi, String description,
                                String errorMessage) {
        record(action, providerId, npi, description, false, errorMessage);
    }

    private void record(AuditAction action, Long providerId, String npi, String description,
                        boolean success, String errorMessage) {
        AuditEvent event = AuditEvent.builder()
                .userId(auditContext.getUserId())
                .userName(auditContext.getUserId())
                .action(action)
                .resourceType(RESOURCE_TYPE)
                .resourceId(providerId)
                .description(description)
                .ipAddress(auditContext.getIpAddress())
                .userAgent(auditContext.getUserAgent())
                .sessionId(auditContext.getSessionId())
                .success(success)
                .errorMessage(errorMessage)
                .build();

        try {
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to persist provider audit event action={} providerId={}", action, providerId, e);
        }

        if (success) {
            log.info("AUDIT: user={} action={} resource={} providerId={} npi={} detail={}",
                    event.getUserId(), action, RESOURCE_TYPE, providerId, npi, description);
        } else {
            log.warn("AUDIT FAILURE: user={} action={} resource={} providerId={} npi={} detail={} error={}",
                    event.getUserId(), action, RESOURCE_TYPE, providerId, npi, description, errorMessage);
        }
    }
}
