package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EncounterService")
class EncounterServiceTest {

    @Mock
    private EncounterRepository encounterRepository;

    @InjectMocks
    private EncounterService encounterService;

    private Encounter sampleEncounter;
    private Patient samplePatient;

    @BeforeEach
    void setUp() {
        samplePatient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .build();

        sampleEncounter = Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000101")
                .patient(samplePatient)
                .encounterType(EncounterType.OUTPATIENT)
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 3, 18, 10, 0))
                .build();
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return encounter when found")
        void shouldReturnEncounterWhenFound() {
            when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));

            Optional<Encounter> result = encounterService.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getEncounterNumber()).isEqualTo("ENC-2026-000101");
            verify(encounterRepository).findById(1L);
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(encounterRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Encounter> result = encounterService.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdWithDetails")
    class FindByIdWithDetails {

        @Test
        @DisplayName("should return encounter with details")
        void shouldReturnEncounterWithDetails() {
            when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(sampleEncounter));

            Optional<Encounter> result = encounterService.findByIdWithDetails(1L);

            assertThat(result).isPresent();
            verify(encounterRepository).findByIdWithDetails(1L);
        }
    }

    @Nested
    @DisplayName("findByEncounterNumber")
    class FindByEncounterNumber {

        @Test
        @DisplayName("should return encounter by number")
        void shouldReturnEncounterByNumber() {
            when(encounterRepository.findByEncounterNumber("ENC-2026-000101"))
                    .thenReturn(Optional.of(sampleEncounter));

            Optional<Encounter> result = encounterService.findByEncounterNumber("ENC-2026-000101");

            assertThat(result).isPresent();
            assertThat(result.get().getEncounterNumber()).isEqualTo("ENC-2026-000101");
        }

        @Test
        @DisplayName("should return empty for unknown encounter number")
        void shouldReturnEmptyForUnknownNumber() {
            when(encounterRepository.findByEncounterNumber("INVALID"))
                    .thenReturn(Optional.empty());

            Optional<Encounter> result = encounterService.findByEncounterNumber("INVALID");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByPatientId")
    class FindByPatientId {

        @Test
        @DisplayName("should return encounters for patient")
        void shouldReturnEncountersForPatient() {
            when(encounterRepository.findByPatientId(1L)).thenReturn(List.of(sampleEncounter));

            List<Encounter> result = encounterService.findByPatientId(1L);

            assertThat(result).hasSize(1);
            verify(encounterRepository).findByPatientId(1L);
        }

        @Test
        @DisplayName("should return paged encounters for patient")
        void shouldReturnPagedEncountersForPatient() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Encounter> page = new PageImpl<>(List.of(sampleEncounter), pageable, 1);
            when(encounterRepository.findByPatientId(1L, pageable)).thenReturn(page);

            Page<Encounter> result = encounterService.findByPatientId(1L, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(encounterRepository).findByPatientId(1L, pageable);
        }
    }

    @Nested
    @DisplayName("findByProviderId")
    class FindByProviderId {

        @Test
        @DisplayName("should return encounters for provider")
        void shouldReturnEncountersForProvider() {
            when(encounterRepository.findByAttendingProviderId(10L)).thenReturn(List.of(sampleEncounter));

            List<Encounter> result = encounterService.findByProviderId(10L);

            assertThat(result).hasSize(1);
            verify(encounterRepository).findByAttendingProviderId(10L);
        }
    }

    @Nested
    @DisplayName("getProviderSchedule")
    class GetProviderSchedule {

        @Test
        @DisplayName("should return schedule for provider on given date")
        void shouldReturnScheduleForProviderOnDate() {
            LocalDate date = LocalDate.of(2026, 3, 18);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

            when(encounterRepository.findTodaysSchedule(10L, startOfDay, endOfDay))
                    .thenReturn(List.of(sampleEncounter));

            List<Encounter> result = encounterService.getProviderSchedule(10L, date);

            assertThat(result).hasSize(1);
            verify(encounterRepository).findTodaysSchedule(10L, startOfDay, endOfDay);
        }
    }

    @Nested
    @DisplayName("findByDateRange")
    class FindByDateRange {

        @Test
        @DisplayName("should return encounters in date range")
        void shouldReturnEncountersInDateRange() {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);

            when(encounterRepository.findByDateRange(
                    start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(sampleEncounter));

            List<Encounter> result = encounterService.findByDateRange(start, end);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {

        @Test
        @DisplayName("should return encounters with given status")
        void shouldReturnEncountersWithStatus() {
            when(encounterRepository.findByStatus(EncounterStatus.SCHEDULED))
                    .thenReturn(List.of(sampleEncounter));

            List<Encounter> result = encounterService.findByStatus(EncounterStatus.SCHEDULED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(EncounterStatus.SCHEDULED);
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create encounter with generated number and SCHEDULED status")
        void shouldCreateEncounterWithGeneratedNumber() {
            Encounter newEncounter = Encounter.builder()
                    .patient(samplePatient)
                    .encounterType(EncounterType.OUTPATIENT)
                    .encounterDateTime(LocalDateTime.now())
                    .build();

            when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> {
                Encounter saved = invocation.getArgument(0);
                saved.setId(2L);
                return saved;
            });

            Encounter result = encounterService.create(newEncounter);

            assertThat(result.getEncounterNumber()).startsWith("ENC-");
            assertThat(result.getStatus()).isEqualTo(EncounterStatus.SCHEDULED);
            verify(encounterRepository).save(newEncounter);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update encounter")
        void shouldUpdateEncounter() {
            when(encounterRepository.save(sampleEncounter)).thenReturn(sampleEncounter);

            Encounter result = encounterService.update(sampleEncounter);

            assertThat(result).isEqualTo(sampleEncounter);
            verify(encounterRepository).save(sampleEncounter);
        }
    }

    @Nested
    @DisplayName("status transitions")
    class StatusTransitions {

        @Test
        @DisplayName("checkIn should set status to CHECKED_IN")
        void checkInShouldSetStatusToCheckedIn() {
            when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));

            encounterService.checkIn(1L);

            assertThat(sampleEncounter.getStatus()).isEqualTo(EncounterStatus.CHECKED_IN);
            verify(encounterRepository).save(sampleEncounter);
        }

        @Test
        @DisplayName("startEncounter should set status to IN_PROGRESS")
        void startEncounterShouldSetStatusToInProgress() {
            when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));

            encounterService.startEncounter(1L);

            assertThat(sampleEncounter.getStatus()).isEqualTo(EncounterStatus.IN_PROGRESS);
            verify(encounterRepository).save(sampleEncounter);
        }

        @Test
        @DisplayName("completeEncounter should set status to COMPLETED")
        void completeEncounterShouldSetStatusToCompleted() {
            when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));

            encounterService.completeEncounter(1L, "Visit completed successfully");

            assertThat(sampleEncounter.getStatus()).isEqualTo(EncounterStatus.COMPLETED);
            assertThat(sampleEncounter.getNotes()).isEqualTo("Visit completed successfully");
            verify(encounterRepository).save(sampleEncounter);
        }

        @Test
        @DisplayName("completeEncounter should not set notes when null")
        void completeEncounterShouldNotSetNotesWhenNull() {
            when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));

            encounterService.completeEncounter(1L, null);

            assertThat(sampleEncounter.getStatus()).isEqualTo(EncounterStatus.COMPLETED);
            assertThat(sampleEncounter.getNotes()).isNull();
            verify(encounterRepository).save(sampleEncounter);
        }

        @Test
        @DisplayName("cancelEncounter should set status to CANCELLED")
        void cancelEncounterShouldSetStatusToCancelled() {
            when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));

            encounterService.cancelEncounter(1L);

            assertThat(sampleEncounter.getStatus()).isEqualTo(EncounterStatus.CANCELLED);
            verify(encounterRepository).save(sampleEncounter);
        }

        @Test
        @DisplayName("markNoShow should set status to NO_SHOW")
        void markNoShowShouldSetStatusToNoShow() {
            when(encounterRepository.findById(1L)).thenReturn(Optional.of(sampleEncounter));

            encounterService.markNoShow(1L);

            assertThat(sampleEncounter.getStatus()).isEqualTo(EncounterStatus.NO_SHOW);
            verify(encounterRepository).save(sampleEncounter);
        }

        @Test
        @DisplayName("status transitions should do nothing when encounter not found")
        void statusTransitionsShouldDoNothingWhenNotFound() {
            when(encounterRepository.findById(999L)).thenReturn(Optional.empty());

            encounterService.checkIn(999L);

            verify(encounterRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getPatientEncounterCount")
    class GetPatientEncounterCount {

        @Test
        @DisplayName("should return encounter count for patient")
        void shouldReturnEncounterCount() {
            when(encounterRepository.countByPatientId(1L)).thenReturn(5L);

            long count = encounterService.getPatientEncounterCount(1L);

            assertThat(count).isEqualTo(5L);
        }
    }
}
