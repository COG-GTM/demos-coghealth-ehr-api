package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    private static final Long ENCOUNTER_ID = 42L;

    @Mock
    private EncounterRepository encounterRepository;

    @Captor
    private ArgumentCaptor<Encounter> encounterCaptor;

    @Captor
    private ArgumentCaptor<LocalDateTime> startCaptor;

    @Captor
    private ArgumentCaptor<LocalDateTime> endCaptor;

    private EncounterService encounterService;

    @BeforeEach
    void setUp() {
        encounterService = new EncounterService(encounterRepository);
    }

    private Encounter existingEncounter() {
        Encounter encounter = new Encounter();
        encounter.setId(ENCOUNTER_ID);
        encounter.setEncounterNumber("ENC-2024-000101");
        encounter.setStatus(EncounterStatus.SCHEDULED);
        when(encounterRepository.findById(ENCOUNTER_ID)).thenReturn(Optional.of(encounter));
        return encounter;
    }

    private void noEncounter() {
        when(encounterRepository.findById(ENCOUNTER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void checkInSetsCheckedInStatusAndSaves() {
        Encounter encounter = existingEncounter();

        encounterService.checkIn(ENCOUNTER_ID);

        verify(encounterRepository).save(encounter);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.CHECKED_IN);
    }

    @Test
    void startEncounterSetsInProgressStatusAndSaves() {
        Encounter encounter = existingEncounter();

        encounterService.startEncounter(ENCOUNTER_ID);

        verify(encounterRepository).save(encounter);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.IN_PROGRESS);
    }

    @Test
    void completeEncounterSetsCompletedStatusAndStoresNotes() {
        Encounter encounter = existingEncounter();

        encounterService.completeEncounter(ENCOUNTER_ID, "Follow up in two weeks");

        verify(encounterRepository).save(encounter);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.COMPLETED);
        assertThat(encounter.getNotes()).isEqualTo("Follow up in two weeks");
    }

    @Test
    void completeEncounterWithNullNotesLeavesExistingNotesUntouched() {
        Encounter encounter = existingEncounter();
        encounter.setNotes("Original notes");

        encounterService.completeEncounter(ENCOUNTER_ID, null);

        verify(encounterRepository).save(encounter);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.COMPLETED);
        assertThat(encounter.getNotes()).isEqualTo("Original notes");
    }

    @Test
    void cancelEncounterSetsCancelledStatusAndSaves() {
        Encounter encounter = existingEncounter();

        encounterService.cancelEncounter(ENCOUNTER_ID);

        verify(encounterRepository).save(encounter);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.CANCELLED);
    }

    @Test
    void markNoShowSetsNoShowStatusAndSaves() {
        Encounter encounter = existingEncounter();

        encounterService.markNoShow(ENCOUNTER_ID);

        verify(encounterRepository).save(encounter);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.NO_SHOW);
    }

    /**
     * Pins the current contract: transitions on an unknown id are a silent no-op rather than
     * an error, so nothing is persisted.
     */
    @Test
    void transitionsOnMissingEncounterAreSilentNoOps() {
        noEncounter();

        encounterService.checkIn(ENCOUNTER_ID);
        encounterService.startEncounter(ENCOUNTER_ID);
        encounterService.completeEncounter(ENCOUNTER_ID, "notes");
        encounterService.cancelEncounter(ENCOUNTER_ID);
        encounterService.markNoShow(ENCOUNTER_ID);

        verify(encounterRepository, never()).save(any(Encounter.class));
    }

    @Test
    void createAssignsScheduledStatusAndGeneratedEncounterNumber() {
        Patient patient = new Patient();
        patient.setMrn("MRN-0001");
        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));

        Encounter created = encounterService.create(encounter);

        verify(encounterRepository).save(encounterCaptor.capture());
        assertThat(created).isSameAs(encounterCaptor.getValue());
        assertThat(created.getStatus()).isEqualTo(EncounterStatus.SCHEDULED);
        assertThat(created.getEncounterNumber())
                .matches("ENC-" + LocalDate.now().getYear() + "-\\d{6}");
    }

    @Test
    void createGeneratesMonotonicallyIncreasingEncounterNumbers() {
        Patient patient = new Patient();
        patient.setMrn("MRN-0001");
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));

        Encounter first = new Encounter();
        first.setPatient(patient);
        Encounter second = new Encounter();
        second.setPatient(patient);

        String firstNumber = encounterService.create(first).getEncounterNumber();
        String secondNumber = encounterService.create(second).getEncounterNumber();

        assertThat(firstNumber).isNotEqualTo(secondNumber);
        assertThat(sequenceOf(secondNumber)).isEqualTo(sequenceOf(firstNumber) + 1);
    }

    @Test
    void updateDelegatesToRepository() {
        Encounter encounter = new Encounter();
        encounter.setEncounterNumber("ENC-2024-000101");
        Encounter persisted = new Encounter();
        when(encounterRepository.save(encounter)).thenReturn(persisted);

        assertThat(encounterService.update(encounter)).isSameAs(persisted);
    }

    @Test
    void findByDateRangeSpansStartOfStartDateToStartOfDayAfterEndDate() {
        LocalDate start = LocalDate.of(2024, 3, 1);
        LocalDate end = LocalDate.of(2024, 3, 31);
        List<Encounter> expected = Arrays.asList(new Encounter());
        when(encounterRepository.findByDateRange(any(), any())).thenReturn(expected);

        assertThat(encounterService.findByDateRange(start, end)).isSameAs(expected);

        verify(encounterRepository).findByDateRange(startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(LocalDateTime.of(2024, 3, 1, 0, 0));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2024, 4, 1, 0, 0));
    }

    @Test
    void getProviderScheduleSpansTheRequestedDayOnly() {
        LocalDate date = LocalDate.of(2024, 3, 15);
        List<Encounter> expected = Arrays.asList(new Encounter());
        when(encounterRepository.findTodaysSchedule(anyLong(), any(), any())).thenReturn(expected);

        assertThat(encounterService.getProviderSchedule(7L, date)).isSameAs(expected);

        verify(encounterRepository).findTodaysSchedule(org.mockito.ArgumentMatchers.eq(7L),
                startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(LocalDateTime.of(2024, 3, 15, 0, 0));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2024, 3, 16, 0, 0));
    }

    private static long sequenceOf(String encounterNumber) {
        return Long.parseLong(encounterNumber.substring(encounterNumber.lastIndexOf('-') + 1));
    }
}
