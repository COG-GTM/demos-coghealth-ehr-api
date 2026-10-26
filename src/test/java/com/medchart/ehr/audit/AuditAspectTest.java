package com.medchart.ehr.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Test
    void commitFailureOfAuditedTransactionIsReportedAsAuditFailure() {
        transactionManager.failNextCommit = true;

        assertThrows(AuditPersistenceException.class, () -> proxy.read(7L));

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository, times(2)).saveAndFlush(saved.capture());
        assertTrue(saved.getAllValues().get(0).getSuccess());
        assertFalse(saved.getAllValues().get(1).getSuccess());
        assertEquals(Arrays.asList("commit-failed", "commit"), transactionManager.outcomes);
    }

    @Test
    void accessInsideReadOnlyCallerTransactionIsAuditedInWritableIndependentTransaction() {
        TransactionTemplate caller = new TransactionTemplate(transactionManager);
        caller.setReadOnly(true);

        assertEquals("record-7", caller.execute(status -> proxy.read(7L)));

        verify(repository).saveAndFlush(any());
        assertEquals(Arrays.asList(true, false), transactionManager.readOnlyBegins);
        assertEquals(Arrays.asList("commit", "commit"), transactionManager.outcomes);
    }

    @Test
    void callerRollbackAfterAuditedAccessKeepsAccessRecordAndRecordsRollback() {
        TransactionTemplate caller = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> caller.execute(status -> {
            proxy.read(7L);
            throw new IllegalStateException("caller fails after reading");
        }));

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository, times(2)).saveAndFlush(saved.capture());
        assertTrue(saved.getAllValues().get(0).getSuccess());
        assertFalse(saved.getAllValues().get(1).getSuccess());
        assertEquals("Caller transaction rolled back after audited call", saved.getAllValues().get(1).getErrorMessage());
        assertEquals(Arrays.asList("commit", "rollback", "commit"), transactionManager.outcomes);
    }

    @Test
    void auditFailureInsideCallerTransactionRollsBackTheCaller() {
        when(repository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("db down"));
        TransactionTemplate caller = new TransactionTemplate(transactionManager);

        assertThrows(AuditPersistenceException.class, () -> caller.execute(status -> proxy.read(7L)));

        assertEquals(Arrays.asList("rollback", "rollback"), transactionManager.outcomes);
    }
}
