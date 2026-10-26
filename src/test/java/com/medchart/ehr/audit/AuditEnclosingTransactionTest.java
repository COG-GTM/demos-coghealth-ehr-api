package com.medchart.ehr.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A caller-owned transaction holds one connection, so the independent audit write needs a second. */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class AuditEnclosingTransactionTest extends AuditPersistenceTestSupport {

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void enclosingRollbackDoesNotEraseTheAccessAuditRow() {
        String returned = new TransactionTemplate(transactionManager).execute(status -> {
            String patient = operations.getPatientById(42L);
            status.setRollbackOnly();
            return patient;
        });

        assertThat(returned).isEqualTo("patient-42");
        List<AuditEvent> events = repository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPatientId()).isEqualTo(42L);
        assertThat(events.get(0).getSuccess()).isTrue();
    }
}
