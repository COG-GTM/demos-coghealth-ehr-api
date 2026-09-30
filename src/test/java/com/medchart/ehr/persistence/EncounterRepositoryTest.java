package com.medchart.ehr.persistence;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.EncounterRepository;
import com.medchart.ehr.support.AbstractJpaTest;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncounterRepositoryTest extends AbstractJpaTest {

    @Autowired
    private EncounterRepository encounters;

    @Autowired
    private TestEntityManager em;

    @Test
    void findByEncounterNumber() {
        Encounter encounter = encounters.findByEncounterNumber("ENC-2024-000001").orElseThrow();

        assertThat(encounter.getId()).isEqualTo(1L);
        assertThat(encounter.getEncounterType()).isEqualTo(EncounterType.OFFICE_VISIT);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.COMPLETED);
    }

    @Test
    void findByIdWithDetailsFetchJoinsPatientAndProvider() {
        em.clear();
        Encounter encounter = encounters.findByIdWithDetails(1L).orElseThrow();

        assertThat(Hibernate.isInitialized(encounter.getPatient())).isTrue();
        assertThat(Hibernate.isInitialized(encounter.getAttendingProvider())).isTrue();
        assertThat(encounter.getPatient().getMrn()).isEqualTo("MRN-2019-00001");
        assertThat(encounter.getAttendingProvider().getLastName()).isEqualTo("Chen");
    }

    @Test
    void findByIdLeavesAssociationsLazy() {
        em.clear();
        Encounter encounter = encounters.findById(1L).orElseThrow();

        assertThat(Hibernate.isInitialized(encounter.getPatient())).isFalse();
    }

    @Test
    void dateRangeQueryIsInclusiveOfBounds() {
        assertThat(encounters.findByDateRange(
                LocalDateTime.of(2024, 1, 15, 9, 0), LocalDateTime.of(2024, 1, 25, 11, 0)))
                .extracting(Encounter::getEncounterNumber)
                .contains("ENC-2024-000001", "ENC-2024-000002", "ENC-2024-000003", "ENC-2024-000004");
    }

    @Test
    void scheduleQueryOnlyReturnsOpenEncountersForTheDay() {
        Patient patient = em.find(Patient.class, 1L);
        Provider provider = em.find(Provider.class, 1L);
        LocalDate day = LocalDate.of(2031, 1, 6);
        em.persist(encounter("ENC-RT-0001", patient, provider, day.atTime(9, 0), EncounterStatus.SCHEDULED));
        em.persist(encounter("ENC-RT-0002", patient, provider, day.atTime(10, 0), EncounterStatus.CHECKED_IN));
        em.persist(encounter("ENC-RT-0003", patient, provider, day.atTime(11, 0), EncounterStatus.CANCELLED));
        em.persist(encounter("ENC-RT-0004", patient, provider, day.plusDays(1).atTime(0, 0), EncounterStatus.SCHEDULED));
        em.flush();

        assertThat(encounters.findTodaysSchedule(1L, day.atStartOfDay(), day.plusDays(1).atStartOfDay()))
                .extracting(Encounter::getEncounterNumber)
                .containsExactlyInAnyOrder("ENC-RT-0001", "ENC-RT-0002");
    }

    @Test
    void countAndListByPatient() {
        Patient patient = em.persist(Patient.builder().mrn("MRN-RT-ENC01").firstName("Enc").lastName("Counter")
                .dateOfBirth(LocalDate.of(1970, 1, 1)).build());
        Provider provider = em.find(Provider.class, 2L);
        em.persist(encounter("ENC-RT-0010", patient, provider, LocalDateTime.of(2030, 1, 1, 9, 0), EncounterStatus.SCHEDULED));
        em.persist(encounter("ENC-RT-0011", patient, provider, LocalDateTime.of(2030, 1, 2, 9, 0), EncounterStatus.COMPLETED));
        em.flush();

        assertThat(encounters.countByPatientId(patient.getId())).isEqualTo(2);
        assertThat(encounters.findByPatientId(patient.getId())).hasSize(2);
    }

    @Test
    void loadingMisalignedSeedEncounterFailsOnEnumMapping() {
        assertThatThrownBy(() -> encounters.findByEncounterNumber("ENC-2024-000015"))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("No enum constant com.medchart.ehr.domain.encounter.EncounterType.IN_PROGRESS");
    }

    private static Encounter encounter(String number, Patient patient, Provider provider,
                                       LocalDateTime when, EncounterStatus status) {
        Encounter encounter = new Encounter();
        encounter.setEncounterNumber(number);
        encounter.setPatient(patient);
        encounter.setAttendingProvider(provider);
        encounter.setEncounterType(EncounterType.OFFICE_VISIT);
        encounter.setStatus(status);
        encounter.setEncounterDateTime(when);
        return encounter;
    }
}
