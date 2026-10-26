package com.medchart.ehr.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    public static class Records {
        int calls;

        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient record")
        public String read(Long patientId) {
            calls++;
            return "record-" + patientId;
        }

        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient record")
        public String missing(Long patientId) {
            throw new IllegalArgumentException("Patient not found");
        }
    }

    private AuditEventRepository repository;
    private RecordingTransactionManager transactionManager;
    private Records target;
    private Records proxy;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        transactionManager = new RecordingTransactionManager();
        target = new Records();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(transactionManager.transactional(new AuditService(repository)), transactionManager));
        proxy = factory.getProxy();
    }

    @Test
    void successfulAccessIsAuditedBeforeResultIsReturned() {
        assertEquals("record-7", proxy.read(7L));

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).saveAndFlush(saved.capture());
        assertTrue(saved.getValue().getSuccess());
        assertEquals(7L, saved.getValue().getPatientId());
        assertEquals(List.of("commit"), transactionManager.outcomes);
    }

    @Test
    void auditFailureFailsTheRequestClosedAndRollsBack() {
        when(repository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(AuditPersistenceException.class, () -> proxy.read(7L));

        assertEquals(1, target.calls);
        assertEquals(List.of("rollback"), transactionManager.outcomes);
    }

    @Test
    void failedAccessIsAuditedInSeparateTransactionAndOriginalErrorRethrown() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> proxy.missing(9L));

        assertEquals("Patient not found", thrown.getMessage());
        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).saveAndFlush(saved.capture());
        assertFalse(saved.getValue().getSuccess());
        assertEquals("Patient not found", saved.getValue().getErrorMessage());
        assertEquals(Arrays.asList("rollback", "commit"), transactionManager.outcomes);
    }

    @Test
    void failedAccessWhoseAuditAlsoFailsCarriesTheAuditFailure() {
        when(repository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> proxy.missing(9L));

        assertEquals(1, thrown.getSuppressed().length);
        assertSame(AuditPersistenceException.class, thrown.getSuppressed()[0].getClass());
        verify(repository, times(1)).saveAndFlush(any());
    }
}
