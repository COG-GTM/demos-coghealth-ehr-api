package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.EncounterDTO;
import com.medchart.ehr.mapper.EncounterMapper;
import com.medchart.ehr.repository.EncounterRepository;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EncounterServiceTest {

    private EncounterRepository encounterRepository;
    private EncounterService service;
    private Patient patient;
    private Patient otherPatient;

    @BeforeEach
    void setUp() {
        encounterRepository = mock(EncounterRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        ProviderRepository providerRepository = mock(ProviderRepository.class);
        service = new EncounterService(encounterRepository, patientRepository, providerRepository, new EncounterMapper());
        when(encounterRepository.saveAndFlush(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));

        patient = Patient.builder().id(1L).mrn("MRN-1").firstName("Bob").lastName("Smith").ssn("123-45-6789").build();
        otherPatient = Patient.builder().id(2L).mrn("MRN-2").build();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
    }

    private Encounter stored(EncounterStatus status) {
        Encounter enc = Encounter.builder()
                .id(42L)
                .encounterNumber("ENC-2026-000042")
                .patient(patient)
                .encounterType(EncounterType.OUTPATIENT)
                .status(status)
                .encounterDateTime(LocalDateTime.of(2026, 1, 1, 9, 0))
                .notes("original notes")
                .createdBy("dr.alice")
                .version(3L)
                .build();
        when(encounterRepository.findById(42L)).thenReturn(Optional.of(enc));
        return enc;
    }

    @Test
    void happyPathFollowsTransitionTable() {
        Encounter enc = stored(EncounterStatus.SCHEDULED);
        assertEquals(EncounterStatus.CHECKED_IN, service.checkIn(42L).getStatus());
        assertEquals(EncounterStatus.IN_PROGRESS, service.startEncounter(42L).getStatus());
        EncounterDTO done = service.completeEncounter(42L, "visit notes");
        assertEquals(EncounterStatus.COMPLETED, done.getStatus());
        assertEquals("visit notes", enc.getNotes());
    }

    @ParameterizedTest
    @EnumSource(value = EncounterStatus.class, names = {"COMPLETED", "CANCELLED", "NO_SHOW"})
    void terminalEncountersRejectEveryTransition(EncounterStatus terminal) {
        Encounter enc = stored(terminal);
        assertThrows(InvalidEncounterStateException.class, () -> service.checkIn(42L));
        assertThrows(InvalidEncounterStateException.class, () -> service.startEncounter(42L));
        assertThrows(InvalidEncounterStateException.class, () -> service.completeEncounter(42L, "overwrite"));
        assertThrows(InvalidEncounterStateException.class, () -> service.cancelEncounter(42L));
        assertThrows(InvalidEncounterStateException.class, () -> service.markNoShow(42L));
        assertEquals(terminal, enc.getStatus());
        assertEquals("original notes", enc.getNotes());
        verify(encounterRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotCompleteWithoutStarting() {
        Encounter enc = stored(EncounterStatus.SCHEDULED);
        assertThrows(InvalidEncounterStateException.class, () -> service.completeEncounter(42L, "x"));
        assertEquals(EncounterStatus.SCHEDULED, enc.getStatus());
    }

    @Test
    void inProgressCannotBeCancelledOrNoShow() {
        stored(EncounterStatus.IN_PROGRESS);
        assertThrows(InvalidEncounterStateException.class, () -> service.cancelEncounter(42L));
        assertThrows(InvalidEncounterStateException.class, () -> service.markNoShow(42L));
    }

    @Test
    void missingEncounterIsNotFound() {
        when(encounterRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.cancelEncounter(99L));
    }

    @Test
    void updateCopiesOnlyEditableFields() {
        Encounter enc = stored(EncounterStatus.SCHEDULED);
        EncounterDTO request = EncounterDTO.builder()
                .id(7L)
                .encounterNumber("FORGED")
                .status(EncounterStatus.COMPLETED)
                .room("12B")
                .chiefComplaint("cough")
                .version(3L)
                .build();

        service.update(42L, request);

        assertEquals("12B", enc.getRoom());
        assertEquals("cough", enc.getChiefComplaint());
        assertEquals(42L, enc.getId());
        assertEquals("ENC-2026-000042", enc.getEncounterNumber());
        assertEquals(EncounterStatus.SCHEDULED, enc.getStatus());
        assertEquals("dr.alice", enc.getCreatedBy());
        assertSame(patient, enc.getPatient());
        assertEquals("original notes", enc.getNotes());
    }

    @Test
    void updateRejectsPatientChange() {
        stored(EncounterStatus.SCHEDULED);
        EncounterDTO request = EncounterDTO.builder()
                .patient(EncounterDTO.PatientSummary.builder().id(otherPatient.getId()).build())
                .build();
        assertThrows(IllegalArgumentException.class, () -> service.update(42L, request));
    }

    @Test
    void updateRejectsStaleVersion() {
        stored(EncounterStatus.SCHEDULED);
        EncounterDTO request = EncounterDTO.builder().room("1").version(1L).build();
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.update(42L, request));
    }

    @Test
    void updateRejectedOnceEncounterIsClosed() {
        Encounter enc = stored(EncounterStatus.COMPLETED);
        EncounterDTO request = EncounterDTO.builder().notes("rewritten").build();
        assertThrows(InvalidEncounterStateException.class, () -> service.update(42L, request));
        assertEquals("original notes", enc.getNotes());
    }

    @Test
    void createIgnoresClientStatusAndNumber() {
        EncounterDTO request = EncounterDTO.builder()
                .encounterNumber("FORGED")
                .status(EncounterStatus.COMPLETED)
                .patient(EncounterDTO.PatientSummary.builder().id(1L).build())
                .encounterType(EncounterType.OUTPATIENT)
                .encounterDateTime(LocalDateTime.of(2026, 2, 1, 10, 0))
                .build();

        EncounterDTO created = service.create(request);

        assertEquals(EncounterStatus.SCHEDULED, created.getStatus());
        assertNotEquals("FORGED", created.getEncounterNumber());
        assertEquals("MRN-1", created.getPatient().getMrn());
    }
}
