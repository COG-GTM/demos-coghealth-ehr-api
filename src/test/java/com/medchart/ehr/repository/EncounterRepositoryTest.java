package com.medchart.ehr.repository;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EncounterRepositoryTest {

    private static final LocalDate START_DATE = LocalDate.of(2024, 3, 1);
    private static final LocalDate END_DATE = LocalDate.of(2024, 3, 31);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EncounterRepository encounterRepository;

    private Patient patient;
    private Provider provider;

    @BeforeEach
    void setUp() {
        patient = entityManager.persist(Patient.builder()
                .mrn("MRN-1001")
                .firstName("Ada")
                .lastName("Lovelace")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .build());
        provider = entityManager.persist(Provider.builder()
                .npi("1234567890")
                .firstName("Grace")
                .lastName("Hopper")
                .providerType(ProviderType.PHYSICIAN)
                .build());
    }

    @Test
    void findByDateRangeIncludesBothBoundaries() {
        Encounter dayBefore = persistEncounter("ENC-1", START_DATE.minusDays(1).atTime(23, 59), EncounterStatus.SCHEDULED);
        Encounter atStart = persistEncounter("ENC-2", START_DATE.atStartOfDay(), EncounterStatus.SCHEDULED);
        Encounter lastMomentOfEndDate = persistEncounter("ENC-3", END_DATE.atTime(23, 59, 59), EncounterStatus.SCHEDULED);
        Encounter midnightAfterEndDate = persistEncounter("ENC-4", END_DATE.plusDays(1).atStartOfDay(), EncounterStatus.SCHEDULED);
        Encounter dayAfter = persistEncounter("ENC-5", END_DATE.plusDays(1).atTime(0, 1), EncounterStatus.SCHEDULED);
        entityManager.flush();

        List<Encounter> found = encounterRepository.findByDateRange(
                START_DATE.atStartOfDay(), END_DATE.plusDays(1).atStartOfDay());

        assertThat(found).extracting(Encounter::getEncounterNumber)
                .containsExactlyInAnyOrder(
                        atStart.getEncounterNumber(),
                        lastMomentOfEndDate.getEncounterNumber(),
                        midnightAfterEndDate.getEncounterNumber())
                .doesNotContain(dayBefore.getEncounterNumber(), dayAfter.getEncounterNumber());
    }

    @Test
    void findTodaysScheduleExcludesTheUpperBoundary() {
        LocalDate day = LocalDate.of(2024, 3, 15);
        Encounter atStartOfDay = persistEncounter("ENC-10", day.atStartOfDay(), EncounterStatus.SCHEDULED);
        Encounter duringDay = persistEncounter("ENC-11", day.atTime(13, 30), EncounterStatus.CHECKED_IN);
        Encounter atNextMidnight = persistEncounter("ENC-12", day.plusDays(1).atStartOfDay(), EncounterStatus.SCHEDULED);
        entityManager.flush();

        List<Encounter> found = encounterRepository.findTodaysSchedule(
                provider.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        assertThat(found).extracting(Encounter::getEncounterNumber)
                .containsExactlyInAnyOrder(atStartOfDay.getEncounterNumber(), duringDay.getEncounterNumber())
                .doesNotContain(atNextMidnight.getEncounterNumber());
    }

    @Test
    void findTodaysScheduleOnlyReturnsActiveStatuses() {
        LocalDate day = LocalDate.of(2024, 4, 2);
        Encounter scheduled = persistEncounter("ENC-20", day.atTime(9, 0), EncounterStatus.SCHEDULED);
        Encounter inProgress = persistEncounter("ENC-21", day.atTime(10, 0), EncounterStatus.IN_PROGRESS);
        persistEncounter("ENC-22", day.atTime(11, 0), EncounterStatus.COMPLETED);
        persistEncounter("ENC-23", day.atTime(12, 0), EncounterStatus.CANCELLED);
        persistEncounter("ENC-24", day.atTime(13, 0), EncounterStatus.NO_SHOW);
        entityManager.flush();

        List<Encounter> found = encounterRepository.findTodaysSchedule(
                provider.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        assertThat(found).extracting(Encounter::getEncounterNumber)
                .containsExactlyInAnyOrder(scheduled.getEncounterNumber(), inProgress.getEncounterNumber());
    }

    @Test
    void countByPatientIdCountsOnlyThatPatientsEncounters() {
        Patient other = entityManager.persist(Patient.builder()
                .mrn("MRN-1002")
                .firstName("Alan")
                .lastName("Turing")
                .dateOfBirth(LocalDate.of(1912, 6, 23))
                .build());
        persistEncounter("ENC-30", LocalDateTime.of(2024, 5, 1, 9, 0), EncounterStatus.SCHEDULED);
        persistEncounter("ENC-31", LocalDateTime.of(2024, 5, 2, 9, 0), EncounterStatus.SCHEDULED);
        entityManager.persist(newEncounter("ENC-32", LocalDateTime.of(2024, 5, 3, 9, 0), EncounterStatus.SCHEDULED, other));
        entityManager.flush();

        assertThat(encounterRepository.countByPatientId(patient.getId())).isEqualTo(2);
        assertThat(encounterRepository.countByPatientId(other.getId())).isEqualTo(1);
    }

    private Encounter persistEncounter(String number, LocalDateTime when, EncounterStatus status) {
        return entityManager.persist(newEncounter(number, when, status, patient));
    }

    private Encounter newEncounter(String number, LocalDateTime when, EncounterStatus status, Patient owner) {
        return Encounter.builder()
                .encounterNumber(number)
                .patient(owner)
                .attendingProvider(provider)
                .encounterType(EncounterType.OFFICE_VISIT)
                .status(status)
                .encounterDateTime(when)
                .build();
    }
}
