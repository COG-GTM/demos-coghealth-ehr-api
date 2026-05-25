package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
                .encounterNumber("ENC-2026-000101")
                .patient(patient)
                .encounterType(EncounterType.OUTPATIENT)
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.now())
                .build();
    }

    @Test
    void findById_shouldReturnEncounter_whenExists() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));

        Optional<Encounter> result = encounterService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("ENC-2026-000101", result.get().getEncounterNumber());
    }

    @Test
    void findById_shouldReturnEmpty_whenNotExists() {
        when(encounterRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Encounter> result = encounterService.findById(99L);

        assertFalse(result.isPresent());
    }

    @Test
    void findByIdWithDetails_shouldReturnEncounterWithDetails() {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter));

        Optional<Encounter> result = encounterService.findByIdWithDetails(1L);

        assertTrue(result.isPresent());
        verify(encounterRepository).findByIdWithDetails(1L);
    }

    @Test
    void findByEncounterNumber_shouldReturnEncounter() {
        when(encounterRepository.findByEncounterNumber("ENC-2026-000101"))
                .thenReturn(Optional.of(encounter));

        Optional<Encounter> result = encounterService.findByEncounterNumber("ENC-2026-000101");

        assertTrue(result.isPresent());
        assertEquals("ENC-2026-000101", result.get().getEncounterNumber());
    }

    @Test
    void findByPatientId_shouldReturnList() {
        when(encounterRepository.findByPatientId(1L)).thenReturn(List.of(encounter));

        List<Encounter> result = encounterService.findByPatientId(1L);

        assertEquals(1, result.size());
    }

    @Test
    void findByPatientId_paged_shouldReturnPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Encounter> page = new PageImpl<>(List.of(encounter));
        when(encounterRepository.findByPatientId(1L, pageable)).thenReturn(page);

        Page<Encounter> result = encounterService.findByPatientId(1L, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void findByProviderId_shouldReturnList() {
        when(encounterRepository.findByAttendingProviderId(1L)).thenReturn(List.of(encounter));

        List<Encounter> result = encounterService.findByProviderId(1L);

        assertEquals(1, result.size());
    }

    @Test
    void getProviderSchedule_shouldReturnScheduleForDate() {
        LocalDate date = LocalDate.of(2026, 5, 25);
        when(encounterRepository.findTodaysSchedule(eq(1L), any(), any()))
                .thenReturn(List.of(encounter));

        List<Encounter> result = encounterService.getProviderSchedule(1L, date);

        assertEquals(1, result.size());
        verify(encounterRepository).findTodaysSchedule(
                eq(1L),
                eq(date.atStartOfDay()),
                eq(date.plusDays(1).atStartOfDay()));
    }

    @Test
    void findByDateRange_shouldReturnEncountersInRange() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        when(encounterRepository.findByDateRange(any(), any())).thenReturn(List.of(encounter));

        List<Encounter> result = encounterService.findByDateRange(start, end);

        assertEquals(1, result.size());
        verify(encounterRepository).findByDateRange(
                eq(start.atStartOfDay()),
                eq(end.plusDays(1).atStartOfDay()));
    }

    @Test
    void findByStatus_shouldReturnFilteredEncounters() {
        when(encounterRepository.findByStatus(EncounterStatus.SCHEDULED))
                .thenReturn(List.of(encounter));

        List<Encounter> result = encounterService.findByStatus(EncounterStatus.SCHEDULED);

        assertEquals(1, result.size());
        assertEquals(EncounterStatus.SCHEDULED, result.get(0).getStatus());
    }

    @Test
    void create_shouldSetEncounterNumberAndStatus() {
        Encounter newEncounter = Encounter.builder()
                .patient(patient)
                .encounterType(EncounterType.OUTPATIENT)
                .encounterDateTime(LocalDateTime.now())
                .build();

        when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> {
            Encounter saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        Encounter result = encounterService.create(newEncounter);

        assertNotNull(result.getEncounterNumber());
        assertTrue(result.getEncounterNumber().startsWith("ENC-"));
        assertEquals(EncounterStatus.SCHEDULED, result.getStatus());
    }

    @Test
    void create_shouldGenerateUniqueEncounterNumbers() {
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));

        Encounter enc1 = Encounter.builder().patient(patient).encounterDateTime(LocalDateTime.now()).build();
        Encounter enc2 = Encounter.builder().patient(patient).encounterDateTime(LocalDateTime.now()).build();

        encounterService.create(enc1);
        encounterService.create(enc2);

        assertNotEquals(enc1.getEncounterNumber(), enc2.getEncounterNumber());
    }

    @Test
    void update_shouldSaveAndReturn() {
        when(encounterRepository.save(encounter)).thenReturn(encounter);

        Encounter result = encounterService.update(encounter);

        assertNotNull(result);
        verify(encounterRepository).save(encounter);
    }

    @Test
    void checkIn_shouldUpdateStatus() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(encounter)).thenReturn(encounter);

        encounterService.checkIn(1L);

        assertEquals(EncounterStatus.CHECKED_IN, encounter.getStatus());
        verify(encounterRepository).save(encounter);
    }

    @Test
    void checkIn_shouldDoNothing_whenNotFound() {
        when(encounterRepository.findById(99L)).thenReturn(Optional.empty());

        encounterService.checkIn(99L);

        verify(encounterRepository, never()).save(any());
    }

    @Test
    void startEncounter_shouldUpdateStatus() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(encounter)).thenReturn(encounter);

        encounterService.startEncounter(1L);

        assertEquals(EncounterStatus.IN_PROGRESS, encounter.getStatus());
    }

    @Test
    void completeEncounter_shouldUpdateStatusAndNotes() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(encounter)).thenReturn(encounter);

        encounterService.completeEncounter(1L, "Visit completed normally");

        assertEquals(EncounterStatus.COMPLETED, encounter.getStatus());
        assertEquals("Visit completed normally", encounter.getNotes());
    }

    @Test
    void completeEncounter_shouldNotSetNotes_whenNull() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(encounter)).thenReturn(encounter);

        encounterService.completeEncounter(1L, null);

        assertEquals(EncounterStatus.COMPLETED, encounter.getStatus());
        assertNull(encounter.getNotes());
    }

    @Test
    void cancelEncounter_shouldUpdateStatus() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(encounter)).thenReturn(encounter);

        encounterService.cancelEncounter(1L);

        assertEquals(EncounterStatus.CANCELLED, encounter.getStatus());
    }

    @Test
    void markNoShow_shouldUpdateStatus() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(encounter)).thenReturn(encounter);

        encounterService.markNoShow(1L);

        assertEquals(EncounterStatus.NO_SHOW, encounter.getStatus());
    }

    @Test
    void getPatientEncounterCount_shouldReturnCount() {
        when(encounterRepository.countByPatientId(1L)).thenReturn(5L);

        long count = encounterService.getPatientEncounterCount(1L);

        assertEquals(5L, count);
    }
}
