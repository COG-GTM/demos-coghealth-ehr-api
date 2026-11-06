package com.medchart.ehr.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private AuditEventRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        auditService = new AuditService(repository, meterRegistry);
    }

    private AuditEvent event() {
        return AuditEvent.builder()
                .userId("user-1")
                .action(AuditAction.READ)
                .resourceType("Patient")
                .patientId(42L)
                .build();
    }

    private double count(String outcome) {
        return meterRegistry.find(AuditService.PERSIST_COUNTER).tag("outcome", outcome).counters()
                .stream().mapToDouble(c -> c.count()).sum();
    }

    @Test
    void countsSuccessfulPersist() {
        when(repository.save(any(AuditEvent.class))).thenAnswer(i -> i.getArgument(0));

        auditService.saveAuditEventAsync(event());

        assertThat(count("success")).isEqualTo(1.0);
        assertThat(count("failure")).isZero();
    }

    @Test
    void countsPersistFailureWithoutPropagating() {
        when(repository.save(any(AuditEvent.class))).thenThrow(new IllegalStateException("db down"));

        auditService.saveAuditEventAsync(event());

        assertThat(count("failure")).isEqualTo(1.0);
        assertThat(meterRegistry.find(AuditService.PERSIST_COUNTER)
                .tag("outcome", "failure")
                .tag("action", "READ")
                .tag("resource_type", "Patient")
                .tag("exception", "IllegalStateException")
                .counter()).isNotNull();
    }

    @Test
    void tagsUnknownActionAndResourceType() {
        when(repository.save(any(AuditEvent.class))).thenThrow(new RuntimeException("boom"));

        auditService.saveAuditEventAsync(AuditEvent.builder().userId("user-1").build());

        assertThat(meterRegistry.find(AuditService.PERSIST_COUNTER)
                .tag("action", "unknown")
                .tag("resource_type", "unknown")
                .counter()).isNotNull();
    }
}
