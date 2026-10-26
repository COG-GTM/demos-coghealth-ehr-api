package com.medchart.ehr.audit;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditService auditService = new AuditService(repository);

    @Test
    void recordPersistsSynchronously() {
        AuditEvent event = event();

        auditService.record(event);

        verify(repository).saveAndFlush(event);
    }

    @Test
    void recordPropagatesPersistenceFailure() {
        DataIntegrityViolationException dbError = new DataIntegrityViolationException("insert failed");
        when(repository.saveAndFlush(any())).thenThrow(dbError);

        AuditPersistenceException thrown = assertThrows(AuditPersistenceException.class,
                () -> auditService.record(event()));
        assertSame(dbError, thrown.getCause());
    }

    @Test
    void recordInNewTransactionPropagatesPersistenceFailure() {
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("db down"));

        assertThrows(AuditPersistenceException.class, () -> auditService.recordInNewTransaction(event()));
    }

    @Test
    void oversizedValuesAreTruncatedToColumnLengths() {
        AuditEvent event = event();
        event.setUserAgent(repeat('u', 10_000));
        event.setIpAddress(repeat('9', 300));
        event.setErrorMessage(repeat('e', 2_000));
        event.setDescription(repeat('d', 600));
        event.setPatientMrn(repeat('m', 40));

        auditService.record(event);

        verify(repository).saveAndFlush(event);
        assertEquals(200, event.getUserAgent().length());
        assertEquals(50, event.getIpAddress().length());
        assertEquals(500, event.getErrorMessage().length());
        assertEquals(500, event.getDescription().length());
        assertEquals(20, event.getPatientMrn().length());
        assertEquals("system", event.getUserId());
    }

    private static AuditEvent event() {
        return AuditEvent.builder()
                .userId("system")
                .action(AuditAction.READ)
                .resourceType("Patient")
                .patientId(1L)
                .build();
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
