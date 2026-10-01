package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
                .patient(patient)
                .build();
    }

    @Test
    void create_setsStatusToScheduled_andGeneratesEncounterNumber() {
        Encounter newEncounter = Encounter.builder()
                .patient(patient)
                .build();

        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> {
            Encounter e = invocation.getArgument(0);
            e.setId(1L);
            return e;
        });

        Encounter result = encounterService.create(newEncounter);

        assertThat(result.getStatus()).isEqualTo(EncounterStatus.SCHEDULED);
        assertThat(result.getEncounterNumber()).isNotNull();
        assertThat(result.getEncounterNumber()).matches("ENC-\\d{4}-\\d{6}");
        verify(encounterRepository).save(any(Encounter.class));
    }

    @Test
    void checkIn_setsStatusToCheckedIn() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        encounterService.checkIn(1L);

        ArgumentCaptor<Encounter> captor = ArgumentCaptor.forClass(Encounter.class);
        verify(encounterRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EncounterStatus.CHECKED_IN);
    }

    @Test
    void startEncounter_setsStatusToInProgress() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        encounterService.startEncounter(1L);

        ArgumentCaptor<Encounter> captor = ArgumentCaptor.forClass(Encounter.class);
        verify(encounterRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EncounterStatus.IN_PROGRESS);
    }

    @Test
    void completeEncounter_setsStatusToCompleted_andSetsNotes() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        encounterService.completeEncounter(1L, "Patient discharged with instructions");

        ArgumentCaptor<Encounter> captor = ArgumentCaptor.forClass(Encounter.class);
        verify(encounterRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EncounterStatus.COMPLETED);
        assertThat(captor.getValue().getNotes()).isEqualTo("Patient discharged with instructions");
    }

    @Test
    void completeEncounter_doesNotOverwriteNotes_whenNotesParamIsNull() {
        encounter.setNotes("Existing notes");
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        encounterService.completeEncounter(1L, null);

        ArgumentCaptor<Encounter> captor = ArgumentCaptor.forClass(Encounter.class);
        verify(encounterRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EncounterStatus.COMPLETED);
        assertThat(captor.getValue().getNotes()).isEqualTo("Existing notes");
    }

    @Test
    void cancelEncounter_setsStatusToCancelled() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        encounterService.cancelEncounter(1L);

        ArgumentCaptor<Encounter> captor = ArgumentCaptor.forClass(Encounter.class);
        verify(encounterRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EncounterStatus.CANCELLED);
    }

    @Test
    void markNoShow_setsStatusToNoShow() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        encounterService.markNoShow(1L);

        ArgumentCaptor<Encounter> captor = ArgumentCaptor.forClass(Encounter.class);
        verify(encounterRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EncounterStatus.NO_SHOW);
    }
}
