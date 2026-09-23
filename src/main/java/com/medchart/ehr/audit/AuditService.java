package com.medchart.ehr.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    static final String PERSIST_COUNTER = "audit_event_persist";
    private static final Logger AUDIT_FAILURE_LOG = LoggerFactory.getLogger("com.medchart.ehr.audit.AuditPersistFailure");
    private static final String UNKNOWN = "unknown";

    private final AuditEventRepository auditEventRepository;
    private final MeterRegistry meterRegistry;

    public AuditService(AuditEventRepository auditEventRepository, MeterRegistry meterRegistry) {
        this.auditEventRepository = auditEventRepository;
        this.meterRegistry = meterRegistry;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAuditEventAsync(AuditEvent event) {
        String action = event.getAction() == null ? UNKNOWN : event.getAction().name();
        String resourceType = event.getResourceType() == null ? UNKNOWN : event.getResourceType();
        try {
            auditEventRepository.save(event);
            counter("success", action, resourceType, "none").increment();
        } catch (Exception e) {
            counter("failure", action, resourceType, e.getClass().getSimpleName()).increment();
            AUDIT_FAILURE_LOG.error(
                    "event=audit_persist_failed action={} resourceType={} resourceId={} patientId={} exception={}",
                    action, resourceType, event.getResourceId(), event.getPatientId(), e.getClass().getName(), e);
        }
    }

    private Counter counter(String outcome, String action, String resourceType, String exception) {
        return Counter.builder(PERSIST_COUNTER)
                .description("Audit event persistence attempts, tagged by outcome")
                .tag("outcome", outcome)
                .tag("action", action)
                .tag("resource_type", resourceType)
                .tag("exception", exception)
                .register(meterRegistry);
    }
}
