package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Persists the event synchronously in the caller's transaction, so it commits or rolls back
     * with the audited operation. Throws {@link AuditWriteException} if it cannot be written.
     */
    @Transactional
    public AuditEvent recordAuditEvent(AuditEvent event) {
        return persist(event);
    }

    /**
     * Persists the event of a failed or rolled-back operation in its own transaction.
     * Throws {@link AuditWriteException} if it cannot be written.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordFailedAuditEvent(AuditEvent event) {
        return persist(event);
    }

    private AuditEvent persist(AuditEvent event) {
        try {
            return auditEventRepository.saveAndFlush(event);
        } catch (RuntimeException e) {
            // Database exception messages can echo bound values (e.g. MRNs), so only log their types.
            log.error("Failed to save audit event action={} resourceType={} cause={}",
                    event.getAction(), event.getResourceType(), describe(e));
            throw new AuditWriteException("Audit event could not be recorded");
        }
    }

    private static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String sqlState = root instanceof SQLException ? " sqlState=" + ((SQLException) root).getSQLState() : "";
        return e.getClass().getName() + " root=" + root.getClass().getName() + sqlState;
    }
}
