package com.medchart.ehr.legacy;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Inserts rows through native SQL so the legacy native-SQL exporters are exercised
 * against the Flyway schema rather than a Hibernate-generated one.
 */
final class LegacyExportTestData {

    private LegacyExportTestData() {
    }

    static Long insertPatient(EntityManager em, String mrn, String firstName, String lastName,
                              String ssn, LocalDate dateOfBirth, boolean active) {
        em.createNativeQuery(
                        "INSERT INTO patients (mrn, ssn, first_name, last_name, date_of_birth, " +
                        "email, phone_home, phone_mobile, street1, city, state, zip_code, active) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)")
                .setParameter(1, mrn)
                .setParameter(2, ssn)
                .setParameter(3, firstName)
                .setParameter(4, lastName)
                .setParameter(5, dateOfBirth)
                .setParameter(6, firstName.toLowerCase() + "@example.test")
                .setParameter(7, "555-0100")
                .setParameter(8, "555-0101")
                .setParameter(9, "1 Main St")
                .setParameter(10, "Springfield")
                .setParameter(11, "IL")
                .setParameter(12, "62701")
                .setParameter(13, active)
                .executeUpdate();

        return idOf(em, "SELECT id FROM patients WHERE mrn = ?1", mrn);
    }

    static Long insertProvider(EntityManager em, String npi, String firstName, String lastName) {
        em.createNativeQuery(
                        "INSERT INTO providers (npi, first_name, last_name, provider_type) " +
                        "VALUES (?1, ?2, ?3, 'PHYSICIAN')")
                .setParameter(1, npi)
                .setParameter(2, firstName)
                .setParameter(3, lastName)
                .executeUpdate();

        return idOf(em, "SELECT id FROM providers WHERE npi = ?1", npi);
    }

    static void insertEncounter(EntityManager em, String encounterNumber, Long patientId, Long providerId,
                                LocalDateTime encounterDateTime, String type, String status) {
        em.createNativeQuery(
                        "INSERT INTO encounters (encounter_number, patient_id, attending_provider_id, " +
                        "encounter_type, status, encounter_date_time) VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
                .setParameter(1, encounterNumber)
                .setParameter(2, patientId)
                .setParameter(3, providerId)
                .setParameter(4, type)
                .setParameter(5, status)
                .setParameter(6, encounterDateTime)
                .executeUpdate();
    }

    static void insertCoverage(EntityManager em, Long patientId, String payerName, String memberId, boolean active) {
        em.createNativeQuery(
                        "INSERT INTO insurance_coverages (patient_id, payer_name, payer_id, member_id, " +
                        "coverage_type, coverage_order, effective_date, active) " +
                        "VALUES (?1, ?2, 'PAYER01', ?3, 'MEDICAL', 'PRIMARY', ?4, ?5)")
                .setParameter(1, patientId)
                .setParameter(2, payerName)
                .setParameter(3, memberId)
                .setParameter(4, LocalDate.of(2020, 1, 1))
                .setParameter(5, active)
                .executeUpdate();
    }

    private static Long idOf(EntityManager em, String sql, String key) {
        Object id = em.createNativeQuery(sql).setParameter(1, key).getSingleResult();
        return ((Number) id).longValue();
    }
}
