package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
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

    private Patient patient;
    private Provider provider;
    private Encounter encounter;

    @BeforeEach
    void setUp() {
        patient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 15))
                .build();

        provider = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .firstName("Dr. Alice")
                .lastName("Smith")
                .providerType(ProviderType.PHYSICIAN)
                .build();

        encounter = Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000001")
                .patient(patient)
                .attendingProvider(provider)
                .encounterType(EncounterType.OFFICE_VISIT)
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 3, 29, 10, 0))
                .department("Medicine")
                .chiefComplaint("Annual checkup")
                .build();
    }

    @Test
    void findById_existingId_returnsEncounter() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));

        Optional<Encounter> result = encounterService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("ENC-2026-000001", result.get().getEncounterNumber());
    }

    @Test
    void findById_nonExistingId_returnsEmpty() {
        when(encounterRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Encounter> result = encounterService.findById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void findByIdWithDetails_returnsEncounterWithDetails() {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter));

        Optional<Encounter> result = encounterService.findByIdWithDetails(1L);

        assertTrue(result.isPresent());
        assertNotNull(result.get().getPatient());
        assertNotNull(result.get().getAttendingProvider());
    }

    @Test
    void findByEncounterNumber_returnsEncounter() {
        when(encounterRepository.findByEncounterNumber("ENC-2026-000001")).thenReturn(Optional.of(encounter));

        Optional<Encounter> result = encounterService.findByEncounterNumber("ENC-2026-000001");

        assertTrue(result.isPresent());
        assertEquals("ENC-2026-000001", result.get().getEncounterNumber());
    }

    @Test
    void findByPatientId_returnsList() {
        when(encounterRepository.findByPatientId(1L)).thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.findByPatientId(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getPatient().getId());
    }

    @Test
    void findByPatientId_paged_returnsPage() {
        PageImpl<Encounter> page = new PageImpl<>(Collections.singletonList(encounter));
        when(encounterRepository.findByPatientId(eq(1L), any())).thenReturn(page);

        Page<Encounter> result = encounterService.findByPatientId(1L, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void findByProviderId_returnsList() {
        when(encounterRepository.findByAttendingProviderId(1L)).thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.findByProviderId(1L);

        assertEquals(1, result.size());
    }

    @Test
    void getProviderSchedule_returnsTodaysEncounters() {
        LocalDate date = LocalDate.of(2026, 3, 29);
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        when(encounterRepository.findTodaysSchedule(1L, startOfDay, endOfDay))
                .thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.getProviderSchedule(1L, date);

        assertEquals(1, result.size());
        verify(encounterRepository).findTodaysSchedule(1L, startOfDay, endOfDay);
    }

    @Test
    void findByDateRange_returnsEncountersInRange() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        when(encounterRepository.findByDateRange(start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.findByDateRange(start, end);

        assertEquals(1, result.size());
    }

    @Test
    void findByStatus_returnsEncountersWithStatus() {
        when(encounterRepository.findByStatus(EncounterStatus.SCHEDULED))
                .thenReturn(Collections.singletonList(encounter));

        List<Encounter> result = encounterService.findByStatus(EncounterStatus.SCHEDULED);

        assertEquals(1, result.size());
        assertEquals(EncounterStatus.SCHEDULED, result.get(0).getStatus());
    }

    @Test
    void create_setsEncounterNumberAndScheduledStatus() {
        Encounter newEncounter = Encounter.builder()
                .patient(patient)
                .attendingProvider(provider)
                .encounterType(EncounterType.OFFICE_VISIT)
                .encounterDateTime(LocalDateTime.now())
                .build();

        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> {
            Encounter saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        Encounter result = encounterService.create(newEncounter);

        assertNotNull(result.getEncounterNumber());
        assertTrue(result.getEncounterNumber().startsWith("ENC-"));
        assertEquals(EncounterStatus.SCHEDULED, result.getStatus());
        verify(encounterRepository).save(any(Encounter.class));
    }

    @Test
    void update_savesEncounter() {
        when(encounterRepository.save(encounter)).thenReturn(encounter);

        Encounter result = encounterService.update(encounter);

        assertNotNull(result);
        verify(encounterRepository).save(encounter);
    }

    @Test
    void checkIn_setsStatusToCheckedIn() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenReturn(encounter);

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
    void startEncounter_setsStatusToInProgress() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenReturn(encounter);

        encounterService.startEncounter(1L);

        assertEquals(EncounterStatus.IN_PROGRESS, encounter.getStatus());
    }

    @Test
    void completeEncounter_setsStatusAndNotes() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenReturn(encounter);

        encounterService.completeEncounter(1L, "Patient stable, follow up in 2 weeks");

        assertEquals(EncounterStatus.COMPLETED, encounter.getStatus());
        assertEquals("Patient stable, follow up in 2 weeks", encounter.getNotes());
    }

    @Test
    void completeEncounter_nullNotes_doesNotOverwriteExistingNotes() {
        encounter.setNotes("Existing notes");
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenReturn(encounter);

        encounterService.completeEncounter(1L, null);

        assertEquals(EncounterStatus.COMPLETED, encounter.getStatus());
        assertEquals("Existing notes", encounter.getNotes());
    }

    @Test
    void cancelEncounter_setsStatusToCancelled() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenReturn(encounter);

        encounterService.cancelEncounter(1L);

        assertEquals(EncounterStatus.CANCELLED, encounter.getStatus());
    }

    @Test
    void markNoShow_setsStatusToNoShow() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenReturn(encounter);

        encounterService.markNoShow(1L);

        assertEquals(EncounterStatus.NO_SHOW, encounter.getStatus());
    }

    @Test
    void getPatientEncounterCount_returnsCount() {
        when(encounterRepository.countByPatientId(1L)).thenReturn(5L);

        long count = encounterService.getPatientEncounterCount(1L);

        assertEquals(5L, count);
    }

    @Test
    void getPatientEncounterCount_noEncounters_returnsZero() {
        when(encounterRepository.countByPatientId(999L)).thenReturn(0L);

        long count = encounterService.getPatientEncounterCount(999L);

        assertEquals(0L, count);
    }

    @Test
    void encounterLifecycle_fullWorkflow() {
        // Create
        Encounter newEnc = Encounter.builder()
                .patient(patient)
                .attendingProvider(provider)
                .encounterType(EncounterType.OFFICE_VISIT)
                .encounterDateTime(LocalDateTime.now())
                .build();

        when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> {
            Encounter e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });

        Encounter created = encounterService.create(newEnc);
        assertEquals(EncounterStatus.SCHEDULED, created.getStatus());

        // Check-in
        when(encounterRepository.findById(10L)).thenReturn(Optional.of(created));
        encounterService.checkIn(10L);
        assertEquals(EncounterStatus.CHECKED_IN, created.getStatus());

        // Start
        encounterService.startEncounter(10L);
        assertEquals(EncounterStatus.IN_PROGRESS, created.getStatus());

        // Complete
        encounterService.completeEncounter(10L, "All good");
        assertEquals(EncounterStatus.COMPLETED, created.getStatus());
        assertEquals("All good", created.getNotes());
    }
}
