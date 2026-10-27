package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterDiagnosis;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.EncounterResponseDTO;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncounterServiceTest {

    private final EncounterRepository repository = mock(EncounterRepository.class);
    private final EncounterService service = new EncounterService(repository);

    @Test
    void dateRangeLongerThanAMonthIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.findByDateRange(LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31)));
        assertThrows(IllegalArgumentException.class,
            () -> service.findByDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)));
        verify(repository, never()).findByDateRange(any(), any());
    }

    @Test
    void reversedDateRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.findByDateRange(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 1)));
    }

    @Test
    void monthLongDateRangeIsAllowed() {
        when(repository.findByDateRange(any(), any())).thenReturn(Collections.emptyList());
        assertTrue(service.findByDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).isEmpty());
    }

    @Test
    void statusChangeOnUnknownEncounterReportsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertFalse(service.cancelEncounter(999L).isPresent());
        verify(repository, never()).save(any());
    }

    @Test
    void statusChangeReturnsEncounterScopedToItsPatient() {
        Encounter encounter = Encounter.builder()
            .id(1L).encounterNumber("ENC-2026-000101").status(EncounterStatus.SCHEDULED)
            .patient(Patient.builder().id(5L).mrn("MRN0000005").ssn("123-45-6789").build())
            .build();
        when(repository.findById(1L)).thenReturn(Optional.of(encounter));
        when(repository.save(encounter)).thenReturn(encounter);

        EncounterResponseDTO result = service.markNoShow(1L).orElseThrow();

        assertEquals(EncounterStatus.NO_SHOW, result.getStatus());
        assertEquals(5L, result.getPatientId());
    }

    @Test
    void oversizedCompletionNotesAreRejected() {
        String notes = "x".repeat(EncounterService.MAX_NOTES_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> service.completeEncounter(1L, notes));
        verify(repository, never()).save(any());
    }

    @Test
    void updateMergesEditableFieldsAndKeepsPatientDiagnosesAndCreator() {
        Patient patient = Patient.builder().id(5L).mrn("MRN0000005").build();
        EncounterDiagnosis diagnosis = new EncounterDiagnosis();
        Encounter existing = Encounter.builder()
            .id(7L).encounterNumber("ENC-2026-000107").status(EncounterStatus.SCHEDULED)
            .patient(patient).room("101").notes("original").createdBy("scheduler")
            .diagnoses(new ArrayList<>(List.of(diagnosis)))
            .build();
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Encounter roundTripped = Encounter.builder().room("202").status(EncounterStatus.COMPLETED).build();
        roundTripped.setDiagnoses(null);

        EncounterResponseDTO result = service.update(7L, roundTripped).orElseThrow();

        assertEquals("202", result.getRoom());
        assertEquals("original", existing.getNotes());
        assertEquals("scheduler", existing.getCreatedBy());
        assertEquals(EncounterStatus.SCHEDULED, existing.getStatus());
        assertSame(patient, existing.getPatient());
        assertEquals(List.of(diagnosis), existing.getDiagnoses());
    }

    @Test
    void updateOfUnknownEncounterReportsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertFalse(service.update(999L, new Encounter()).isPresent());
        verify(repository, never()).save(any());
    }
}
