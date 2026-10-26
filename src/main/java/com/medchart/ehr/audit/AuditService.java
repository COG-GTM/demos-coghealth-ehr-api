package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Persists the event synchronously in its own transaction and throws
     * {@link AuditWriteException} if it cannot be written, so audited calls fail closed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordAuditEvent(AuditEvent event) {
        try {
            return auditEventRepository.saveAndFlush(event);
        } catch (RuntimeException e) {
            log.error("Failed to save audit event action={} resourceType={}",
                    event.getAction(), event.getResourceType(), e);
            throw new AuditWriteException("Audit event could not be recorded", e);
        }
    }
}
