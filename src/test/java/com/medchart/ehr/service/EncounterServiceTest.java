package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    @Mock
    private EncounterRepository encounterRepository;

    @InjectMocks
    private EncounterService encounterService;

    private Patient samplePatient;
    private Encounter sampleEncounter;

    @BeforeEach
    void setUp() {
        samplePatient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build();

        sampleEncounter = Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000001")
                .patient(samplePatient)
                .encounterType(EncounterType.OUTPATIENT)
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 4, 30, 9, 0))
                .build();
    }

    @Test
    void findById_shouldReturnEncounter() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));

        Optional<Encounter> result = encounterService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("ENC-2026-000001", result.get().getEncounterNumber());
    }

    @Test
    void findById_shouldReturnEmptyForNonexistent() {
        when(encounterRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Encounter> result = encounterService.findById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void findByEncounterNumber_shouldReturnEncounter() {
        when(encounterRepository.findByEncounterNumber("ENC-2026-000001"))
                .thenReturn(Optional.of(sampleEncounter));

        Optional<Encounter> result = encounterService.findByEncounterNumber("ENC-2026-000001");

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void findByPatientId_shouldReturnEncountersList() {
        when(encounterRepository.findByPatientId(1L))
                .thenReturn(Collections.singletonList(sampleEncounter));

        List<Encounter> results = encounterService.findByPatientId(1L);

        assertEquals(1, results.size());
        assertEquals("ENC-2026-000001", results.get(0).getEncounterNumber());
    }

    @Test
    void findByStatus_shouldReturnMatchingEncounters() {
        when(encounterRepository.findByStatus(EncounterStatus.SCHEDULED))
                .thenReturn(Collections.singletonList(sampleEncounter));

        List<Encounter> results = encounterService.findByStatus(EncounterStatus.SCHEDULED);

        assertEquals(1, results.size());
        assertEquals(EncounterStatus.SCHEDULED, results.get(0).getStatus());
    }

    @Test
    void findByDateRange_shouldQueryCorrectDateBounds() {
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);

        when(encounterRepository.findByDateRange(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()))
                .thenReturn(Collections.singletonList(sampleEncounter));

        List<Encounter> results = encounterService.findByDateRange(startDate, endDate);

        assertEquals(1, results.size());
        verify(encounterRepository).findByDateRange(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 5, 1, 0, 0));
    }

    @Test
    void create_shouldSetStatusToScheduledAndGenerateNumber() {
        Encounter input = Encounter.builder()
                .patient(samplePatient)
                .encounterType(EncounterType.OUTPATIENT)
                .encounterDateTime(LocalDateTime.of(2026, 5, 1, 10, 0))
                .build();

        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> {
            Encounter enc = invocation.getArgument(0);
            enc.setId(2L);
            return enc;
        });

        Encounter result = encounterService.create(input);

        assertEquals(EncounterStatus.SCHEDULED, result.getStatus());
        assertNotNull(result.getEncounterNumber());
        assertTrue(result.getEncounterNumber().startsWith("ENC-"));
        verify(encounterRepository).save(input);
    }

    @Test
    void create_encounterNumbersShouldBeUnique() {
        Encounter input1 = Encounter.builder()
                .patient(samplePatient)
                .encounterType(EncounterType.OUTPATIENT)
                .encounterDateTime(LocalDateTime.now())
                .build();
        Encounter input2 = Encounter.builder()
                .patient(samplePatient)
                .encounterType(EncounterType.TELEHEALTH)
                .encounterDateTime(LocalDateTime.now())
                .build();

        when(encounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Encounter enc1 = encounterService.create(input1);
        Encounter enc2 = encounterService.create(input2);

        assertNotEquals(enc1.getEncounterNumber(), enc2.getEncounterNumber());
    }

    // --- State transition tests ---

    @Test
    void checkIn_shouldTransitionToCheckedIn() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));
        when(encounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        encounterService.checkIn(1L);

        assertEquals(EncounterStatus.CHECKED_IN, sampleEncounter.getStatus());
        verify(encounterRepository).save(sampleEncounter);
    }

    @Test
    void checkIn_shouldDoNothingForNonexistentEncounter() {
        when(encounterRepository.findById(999L)).thenReturn(Optional.empty());

        encounterService.checkIn(999L);

        verify(encounterRepository, never()).save(any());
    }

    @Test
    void startEncounter_shouldTransitionToInProgress() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));
        when(encounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        encounterService.startEncounter(1L);

        assertEquals(EncounterStatus.IN_PROGRESS, sampleEncounter.getStatus());
        verify(encounterRepository).save(sampleEncounter);
    }

    @Test
    void completeEncounter_shouldTransitionToCompletedWithNotes() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));
        when(encounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        encounterService.completeEncounter(1L, "Patient discharged");

        assertEquals(EncounterStatus.COMPLETED, sampleEncounter.getStatus());
        assertEquals("Patient discharged", sampleEncounter.getNotes());
        verify(encounterRepository).save(sampleEncounter);
    }

    @Test
    void completeEncounter_shouldTransitionWithoutNotesWhenNull() {
        sampleEncounter.setNotes("Existing notes");
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));
        when(encounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        encounterService.completeEncounter(1L, null);

        assertEquals(EncounterStatus.COMPLETED, sampleEncounter.getStatus());
        assertEquals("Existing notes", sampleEncounter.getNotes());
    }

    @Test
    void cancelEncounter_shouldTransitionToCancelled() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));
        when(encounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        encounterService.cancelEncounter(1L);

        assertEquals(EncounterStatus.CANCELLED, sampleEncounter.getStatus());
    }

    @Test
    void markNoShow_shouldTransitionToNoShow() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));
        when(encounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        encounterService.markNoShow(1L);

        assertEquals(EncounterStatus.NO_SHOW, sampleEncounter.getStatus());
    }

    @Test
    void fullWorkflow_scheduledToCompleted() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));
        when(encounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(EncounterStatus.SCHEDULED, sampleEncounter.getStatus());

        encounterService.checkIn(1L);
        assertEquals(EncounterStatus.CHECKED_IN, sampleEncounter.getStatus());

        encounterService.startEncounter(1L);
        assertEquals(EncounterStatus.IN_PROGRESS, sampleEncounter.getStatus());

        encounterService.completeEncounter(1L, "Visit completed successfully");
        assertEquals(EncounterStatus.COMPLETED, sampleEncounter.getStatus());
        assertEquals("Visit completed successfully", sampleEncounter.getNotes());

        verify(encounterRepository, times(3)).save(sampleEncounter);
    }

    @Test
    void getPatientEncounterCount_shouldDelegateToRepository() {
        when(encounterRepository.countByPatientId(1L)).thenReturn(5L);

        long count = encounterService.getPatientEncounterCount(1L);

        assertEquals(5L, count);
    }

    @Test
    void getProviderSchedule_shouldQueryCorrectDayBounds() {
        LocalDate date = LocalDate.of(2026, 4, 30);
        when(encounterRepository.findTodaysSchedule(eq(5L), any(), any()))
                .thenReturn(Collections.singletonList(sampleEncounter));

        List<Encounter> schedule = encounterService.getProviderSchedule(5L, date);

        assertEquals(1, schedule.size());
        verify(encounterRepository).findTodaysSchedule(
                eq(5L),
                eq(LocalDateTime.of(2026, 4, 30, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 1, 0, 0)));
    }

    @Test
    void update_shouldSaveAndReturnEncounter() {
        when(encounterRepository.save(sampleEncounter)).thenReturn(sampleEncounter);

        Encounter result = encounterService.update(sampleEncounter);

        assertEquals(sampleEncounter, result);
        verify(encounterRepository).save(sampleEncounter);
    }
}
