package com.medchart.ehr.audit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    static final String PERSISTED_METRIC = "audit.event.persisted";
    static final String PERSIST_FAILURE_METRIC = "audit.event.persist.failures";
    private static final String UNKNOWN_TAG_VALUE = "unknown";

    private final AuditEventRepository auditEventRepository;
    private final MeterRegistry meterRegistry;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAuditEventAsync(AuditEvent event) {
        try {
            auditEventRepository.save(event);
            meterRegistry.counter(PERSISTED_METRIC, tags(event)).increment();
        } catch (Exception e) {
            meterRegistry.counter(PERSIST_FAILURE_METRIC, tags(event)).increment();
            log.error("Failed to save audit event", e);
        }
    }

    private String[] tags(AuditEvent event) {
        return new String[] {
            "action", event == null || event.getAction() == null
                ? UNKNOWN_TAG_VALUE : event.getAction().name(),
            "resourceType", event == null || event.getResourceType() == null
                ? UNKNOWN_TAG_VALUE : event.getResourceType()
        };
    }
}
