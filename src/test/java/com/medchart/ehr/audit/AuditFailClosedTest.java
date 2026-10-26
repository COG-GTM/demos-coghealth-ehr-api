package com.medchart.ehr.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuditFailClosedTest extends AuditPersistenceTestSupport {

    @MockBean
    AuditService failingAuditService;

    @BeforeEach
    void failAuditWrites() {
        when(failingAuditService.recordAuditEvent(any()))
                .thenThrow(new AuditWriteException("Audit event could not be recorded", new RuntimeException("db down")));
    }

    @Test
    void readFailsInsteadOfReturningUnauditedData() {
        assertThatThrownBy(() -> operations.getPatientById(42L)).isInstanceOf(AuditWriteException.class);
    }

    @Test
    void writeIsRolledBack() {
        assertThatThrownBy(() -> operations.updatePatient(42L)).isInstanceOf(AuditWriteException.class);

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void failedCallReportsTheAuditFailure() {
        assertThatThrownBy(() -> operations.getPatientByMrn("MRN1"))
                .isInstanceOf(AuditWriteException.class)
                .satisfies(e -> assertThat(e.getSuppressed()).hasOnlyElementsOfType(EntityNotFoundException.class));
    }
}
