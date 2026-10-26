package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists audit events synchronously. Any persistence failure is raised as
 * {@link AuditPersistenceException} so the audited operation fails closed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Writes the event in the caller's transaction, so a failed write rolls back the audited work.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEvent event) {
        persist(event);
    }

    /**
     * Writes the event in its own transaction, for failed operations whose transaction is rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTransaction(AuditEvent event) {
        persist(event);
    }

    private void persist(AuditEvent event) {
        fitToColumns(event);
        try {
            auditEventRepository.saveAndFlush(event);
        } catch (RuntimeException e) {
            log.error("Audit event persistence failed; rejecting audited operation (action={}, resourceType={})",
                    event.getAction(), event.getResourceType(), e);
            throw new AuditPersistenceException("Failed to persist audit event", e);
        }
    }

    static void fitToColumns(AuditEvent event) {
        event.setUserId(truncate(event.getUserId(), 100));
        event.setUserName(truncate(event.getUserName(), 100));
        event.setPatientMrn(truncate(event.getPatientMrn(), 20));
        event.setResourceType(truncate(event.getResourceType(), 100));
        event.setDescription(truncate(event.getDescription(), 500));
        event.setIpAddress(truncate(event.getIpAddress(), 50));
        event.setUserAgent(truncate(event.getUserAgent(), 200));
        event.setSessionId(truncate(event.getSessionId(), 100));
        event.setErrorMessage(truncate(event.getErrorMessage(), 500));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
