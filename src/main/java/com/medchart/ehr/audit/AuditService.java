package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for persisting HIPAA audit trail events.
 *
 * Saves audit records asynchronously in a separate transaction to ensure
 * audit logging does not interfere with the primary business operation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAuditEventAsync(AuditEvent event) {
        try {
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to save audit event", e);
        }
    }
}
