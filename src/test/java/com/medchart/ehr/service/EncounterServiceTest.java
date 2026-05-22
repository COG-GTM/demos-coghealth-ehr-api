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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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

    private Encounter encounter;
    private Patient patient;

    @BeforeEach
    void setUp() {
        patient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .build();

        encounter = Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000001")
                .patient(patient)
                .encounterType(EncounterType.OUTPATIENT)
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 5, 22, 10, 0))
                .build();
    }

    @Test
    void findById_existingEncounter_returnsEncounter() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));

        Optional<Encounter> result = encounterService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("ENC-2026-000001", result.get().getEncounterNumber());
    }

    @Test
    void findById_nonExistingEncounter_returnsEmpty() {
        when(encounterRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Encounter> result = encounterService.findById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void findByIdWithDetails_delegatesToRepository() {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter));

        Optional<Encounter> result = encounterService.findByIdWithDetails(1L);

        assertTrue(result.isPresent());
        verify(encounterRepository).findByIdWithDetails(1L);
    }

    @Test
    void findByEncounterNumber_returnsMatchingEncounter() {
        when(encounterRepository.findByEncounterNumber("ENC-2026-000001"))
                .thenReturn(Optional.of(encounter));

        Optional<Encounter> result = encounterService.findByEncounterNumber("ENC-2026-000001");

        assertTrue(result.isPresent());
        assertEquals(EncounterStatus.SCHEDULED, result.get().getStatus());
    }

    @Test
    void findByPatientId_returnsList() {
        when(encounterRepository.findByPatientId(1L))
                .thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.findByPatientId(1L);

        assertEquals(1, result.size());
    }

    @Test
    void findByPatientId_paged_returnsPage() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Encounter> page = new PageImpl<>(Collections.singletonList(encounter));
        when(encounterRepository.findByPatientId(1L, pageable)).thenReturn(page);

        Page<Encounter> result = encounterService.findByPatientId(1L, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void findByProviderId_delegatesToRepository() {
        when(encounterRepository.findByAttendingProviderId(1L))
                .thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.findByProviderId(1L);

        assertEquals(1, result.size());
        verify(encounterRepository).findByAttendingProviderId(1L);
    }

    @Test
    void getProviderSchedule_usesCorrectDateRange() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        when(encounterRepository.findTodaysSchedule(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.getProviderSchedule(1L, date);

        assertEquals(1, result.size());
        verify(encounterRepository).findTodaysSchedule(
                eq(1L),
                eq(date.atStartOfDay()),
                eq(date.plusDays(1).atStartOfDay())
        );
    }

    @Test
    void findByDateRange_usesCorrectBounds() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        when(encounterRepository.findByDateRange(any(), any()))
                .thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.findByDateRange(start, end);

        assertEquals(1, result.size());
        verify(encounterRepository).findByDateRange(
                eq(start.atStartOfDay()),
                eq(end.plusDays(1).atStartOfDay())
        );
    }

    @Test
    void findByStatus_delegatesToRepository() {
        when(encounterRepository.findByStatus(EncounterStatus.SCHEDULED))
                .thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.findByStatus(EncounterStatus.SCHEDULED);

        assertEquals(1, result.size());
    }

    @Test
    void create_setsEncounterNumberAndScheduledStatus() {
        Encounter newEncounter = Encounter.builder()
                .patient(patient)
                .encounterType(EncounterType.OUTPATIENT)
                .encounterDateTime(LocalDateTime.now())
                .build();

        when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));

        Encounter result = encounterService.create(newEncounter);

        assertNotNull(result.getEncounterNumber());
        assertTrue(result.getEncounterNumber().startsWith("ENC-"));
        assertEquals(EncounterStatus.SCHEDULED, result.getStatus());
    }

    @Test
    void update_savesEncounter() {
        when(encounterRepository.save(encounter)).thenReturn(encounter);

        Encounter result = encounterService.update(encounter);

        assertEquals(encounter, result);
        verify(encounterRepository).save(encounter);
    }

    @Test
    void checkIn_updatesStatusToCheckedIn() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));

        encounterService.checkIn(1L);

        assertEquals(EncounterStatus.CHECKED_IN, encounter.getStatus());
        verify(encounterRepository).save(encounter);
    }

    @Test
    void checkIn_nonExistingEncounter_doesNothing() {
        when(encounterRepository.findById(999L)).thenReturn(Optional.empty());

        encounterService.checkIn(999L);

        verify(encounterRepository, never()).save(any());
    }

    @Test
    void startEncounter_updatesStatusToInProgress() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));

        encounterService.startEncounter(1L);

        assertEquals(EncounterStatus.IN_PROGRESS, encounter.getStatus());
        verify(encounterRepository).save(encounter);
    }

    @Test
    void completeEncounter_updatesStatusAndNotes() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));

        encounterService.completeEncounter(1L, "Visit completed successfully");

        assertEquals(EncounterStatus.COMPLETED, encounter.getStatus());
        assertEquals("Visit completed successfully", encounter.getNotes());
        verify(encounterRepository).save(encounter);
    }

    @Test
    void completeEncounter_withNullNotes_doesNotSetNotes() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        encounter.setNotes("Existing notes");

        encounterService.completeEncounter(1L, null);

        assertEquals(EncounterStatus.COMPLETED, encounter.getStatus());
        assertEquals("Existing notes", encounter.getNotes());
    }

    @Test
    void cancelEncounter_updatesStatusToCancelled() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));

        encounterService.cancelEncounter(1L);

        assertEquals(EncounterStatus.CANCELLED, encounter.getStatus());
    }

    @Test
    void markNoShow_updatesStatusToNoShow() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));

        encounterService.markNoShow(1L);

        assertEquals(EncounterStatus.NO_SHOW, encounter.getStatus());
    }

    @Test
    void getPatientEncounterCount_returnsCount() {
        when(encounterRepository.countByPatientId(1L)).thenReturn(5L);

        long count = encounterService.getPatientEncounterCount(1L);

        assertEquals(5L, count);
    }
}
