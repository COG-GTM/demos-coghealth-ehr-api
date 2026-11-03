package com.medchart.ehr.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.persistence.EntityNotFoundException;

/**
 * Runs {@link AuditAspect} against an H2 schema generated from {@link AuditEvent},
 * which enforces the same VARCHAR limits as V1__initial_schema.sql. The pool has a single
 * connection, so an audited call that needed a second connection would time out.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:audit;MODE=PostgreSQL;NON_KEYWORDS=TIMESTAMP,ACTION",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class AuditPersistenceTestSupport {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ImportAutoConfiguration(AopAutoConfiguration.class)
    @Import({AuditAspect.class, AuditService.class, AuditedOperations.class})
    static class Config {
    }

    @Service
    static class AuditedOperations {

        private final AuditEventRepository repository;

        AuditedOperations(AuditEventRepository repository) {
            this.repository = repository;
        }

        @Transactional(readOnly = true)
        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient record")
        public String getPatientById(Long id) {
            return "patient-" + id;
        }

        @Transactional(readOnly = true)
        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient by MRN")
        public String getPatientByMrn(String mrn) {
            throw new EntityNotFoundException("Patient not found with MRN: " + mrn);
        }

        @Transactional
        @AuditAccess(action = AuditAction.UPDATE, resourceType = "Patient", description = "Update patient record")
        public void updatePatient(Long id) {
            repository.save(AuditEvent.builder()
                    .userId("marker").action(AuditAction.UPDATE).resourceType("Marker").build());
        }

        @Transactional
        @AuditAccess(action = AuditAction.UPDATE, resourceType = "Patient", description = "Update patient record")
        public void updatePatientFailingAtCommit(Long id) {
            updatePatient(id);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("deferred constraint violation");
                }
            });
        }
    }

    @Autowired
    protected AuditedOperations operations;

    @Autowired
    protected AuditEventRepository repository;

    protected MockHttpServletRequest request;

    @BeforeEach
    void bindRequest() {
        repository.deleteAll();
        request = new MockHttpServletRequest("GET", "/api/v1/patients/42");
        request.setRemoteAddr("198.51.100.20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }
}
