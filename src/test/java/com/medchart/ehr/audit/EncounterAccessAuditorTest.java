package com.medchart.ehr.audit;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EncounterAccessAuditorTest {

    private AuditService auditService;
    private EncounterAccessAuditor auditor;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        auditor = new EncounterAccessAuditor(auditService);
    }

    @Test
    void encounterAccessIsAttributedToThePatientNotTheEncounter() {
        auditor.logEncounterAccess(encounter(1L, 42L), AuditAction.READ, "View encounter");

        AuditEvent event = captured();
        assertEquals(42L, event.getPatientId());
        assertEquals("MRN0000000042", event.getPatientMrn());
        assertEquals(1L, event.getResourceId());
        assertEquals(AuditAction.READ, event.getAction());
        assertTrue(event.getSuccess());
    }

    @Test
    void patientScopedListIsRecordedAsRead() {
        auditor.logEncounterListAccess(42L, List.of(encounter(1L, 42L), encounter(2L, 42L)), "List patient encounters");

        AuditEvent event = captured();
        assertEquals(AuditAction.READ, event.getAction());
        assertEquals(42L, event.getPatientId());
        assertTrue(event.getDescription().contains("2 encounters, 1 patients"));
    }

    @Test
    void crossPatientListIsRecordedAsSearch() {
        auditor.logEncounterListAccess(null, List.of(encounter(1L, 42L), encounter(2L, 43L)), "List encounters by status");

        AuditEvent event = captured();
        assertEquals(AuditAction.SEARCH, event.getAction());
        assertNull(event.getPatientId());
        assertTrue(event.getDescription().contains("2 encounters, 2 patients"));
    }

    @Test
    void failuresAreRecordedAsUnsuccessful() {
        auditor.logEncounterAccessFailure(encounter(1L, 42L), AuditAction.READ, "View encounter",
                new IllegalStateException("boom"));

        AuditEvent event = captured();
        assertFalse(event.getSuccess());
        assertEquals("IllegalStateException", event.getErrorMessage());
    }

    private AuditEvent captured() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }

    private static Encounter encounter(Long id, Long patientId) {
        return Encounter.builder()
                .id(id)
                .patient(Patient.builder().id(patientId).mrn(String.format("MRN%010d", patientId)).build())
                .build();
    }
}
