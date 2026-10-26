package com.medchart.ehr.audit;

import org.junit.jupiter.api.Test;

import javax.persistence.EntityNotFoundException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditAspectPersistenceTest extends AuditPersistenceTestSupport {

    @Test
    void oversizedHeadersStillProduceAnAuditRow() {
        request.addHeader("User-Agent", "U".repeat(300));
        request.addHeader("X-Forwarded-For", "F".repeat(100) + ", 10.0.0.1");

        assertThat(operations.getPatientById(42L)).isEqualTo("patient-42");

        List<AuditEvent> events = repository.findAll();
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getSuccess()).isTrue();
        assertThat(event.getUserAgent()).isEqualTo("U".repeat(200));
        assertThat(event.getIpAddress()).isEqualTo("198.51.100.20");
    }

    @Test
    void validForwardedAddressIsRecorded() {
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");

        operations.getPatientById(7L);

        assertThat(repository.findAll()).extracting(AuditEvent::getIpAddress).containsExactly("203.0.113.9");
    }

    @Test
    void oversizedErrorMessageFromAttackerMrnIsRecorded() {
        String mrn = "M".repeat(600) + "\u0000";

        assertThatThrownBy(() -> operations.getPatientByMrn(mrn)).isInstanceOf(EntityNotFoundException.class);

        List<AuditEvent> events = repository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getSuccess()).isFalse();
        assertThat(events.get(0).getErrorMessage()).hasSize(500).startsWith("Patient not found with MRN: MMM");
    }

    @Test
    void auditedWriteAndItsAuditRowCommitTogether() {
        operations.updatePatient(42L);

        assertThat(repository.findAll()).extracting(AuditEvent::getResourceType)
                .containsExactlyInAnyOrder("Marker", "Patient");
    }
}
