package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@Import(EncounterExportService.class)
@Sql(scripts = "classpath:db/legacy-export-schema.sql")
class EncounterExportServiceTest {

    @Autowired
    private EncounterExportService service;

    @Autowired
    private EntityManager entityManager;

    private static final LocalDate START = LocalDate.of(2024, 3, 10);
    private static final LocalDate END = LocalDate.of(2024, 3, 12);

    private void insertPatient(long id, String mrn, String first, String last, LocalDate dob) {
        entityManager.createNativeQuery(
                "INSERT INTO patients (id, mrn, ssn, first_name, last_name, date_of_birth, email, " +
                "phone_home, phone_mobile, street1, city, state, zip_code, active) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, TRUE)")
                .setParameter(1, id)
                .setParameter(2, mrn)
                .setParameter(3, "123-45-6789")
                .setParameter(4, first)
                .setParameter(5, last)
                .setParameter(6, dob)
                .setParameter(7, first.toLowerCase() + "@example.com")
                .setParameter(8, "555-0100")
                .setParameter(9, "555-0101")
                .setParameter(10, "1 Main St")
                .setParameter(11, "Springfield")
                .setParameter(12, "IL")
                .setParameter(13, "62701")
                .executeUpdate();
    }

    private void insertEncounter(long id, long patientId, String number, LocalDateTime when,
                                 String type, String status) {
        entityManager.createNativeQuery(
                "INSERT INTO encounters (id, encounter_number, patient_id, provider_id, " +
                "attending_provider_id, encounter_type, status, encounter_date, encounter_date_time) " +
                "VALUES (?1, ?2, ?3, 900, 901, ?4, ?5, ?6, ?7)")
                .setParameter(1, id)
                .setParameter(2, number)
                .setParameter(3, patientId)
                .setParameter(4, type)
                .setParameter(5, status)
                .setParameter(6, when.toLocalDate())
                .setParameter(7, when)
                .executeUpdate();
    }

    private List<String> csvLines(byte[] export) {
        return List.of(new String(export, StandardCharsets.UTF_8).split("\n"));
    }

    @Test
    void exportEncountersForDateRangeIncludesBoundaryDaysAndExcludesTheDayAfter() {
        insertPatient(1L, "MRN001", "Ada", "Lovelace", LocalDate.of(1980, 1, 2));
        insertEncounter(10L, 1L, "ENC-BEFORE", START.minusDays(1).atTime(23, 59), "OFFICE", "FINISHED");
        insertEncounter(11L, 1L, "ENC-START", START.atStartOfDay(), "OFFICE", "FINISHED");
        insertEncounter(12L, 1L, "ENC-END-LATE", END.atTime(23, 59, 59), "INPATIENT", "IN_PROGRESS");
        insertEncounter(13L, 1L, "ENC-AFTER", END.plusDays(1).atStartOfDay(), "OFFICE", "PLANNED");

        List<String> lines = csvLines(service.exportEncountersForDateRange(START, END));

        assertThat(lines).hasSize(3);
        assertThat(lines.get(0))
                .isEqualTo("EncounterId,EncounterNumber,PatientMRN,PatientName,DOB,EncounterDate,Type,Status");
        assertThat(lines.subList(1, lines.size()))
                .anyMatch(line -> line.contains("ENC-START"))
                .anyMatch(line -> line.contains("ENC-END-LATE"))
                .noneMatch(line -> line.contains("ENC-BEFORE"))
                .noneMatch(line -> line.contains("ENC-AFTER"));
    }

    @Test
    void exportEncountersForDateRangeAlignsColumnsWithTheHeader() {
        insertPatient(1L, "MRN001", "Ada", "Lovelace", LocalDate.of(1980, 1, 2));
        insertEncounter(11L, 1L, "ENC-1", START.atTime(9, 30), "OFFICE", "FINISHED");

        List<String> lines = csvLines(service.exportEncountersForDateRange(START, END));

        assertThat(lines).hasSize(2);
        String[] header = lines.get(0).split(",");
        String[] row = lines.get(1).split(",");
        assertThat(row).hasSameSizeAs(header);
        assertThat(row[0]).isEqualTo("11");
        assertThat(row[1]).isEqualTo("ENC-1");
        assertThat(row[2]).isEqualTo("MRN001");
        assertThat(row[3]).isEqualTo("Ada Lovelace");
        assertThat(row[4]).isEqualTo("1980-01-02");
        assertThat(row[5]).contains("2024-03-10 09:30");
        assertThat(row[6]).isEqualTo("OFFICE");
        assertThat(row[7]).isEqualTo("FINISHED");
    }

    @Test
    void exportEncountersForDateRangeReturnsHeaderOnlyWhenNoEncountersMatch() {
        insertPatient(1L, "MRN001", "Ada", "Lovelace", LocalDate.of(1980, 1, 2));

        List<String> lines = csvLines(service.exportEncountersForDateRange(START, END));

        assertThat(lines).containsExactly(
                "EncounterId,EncounterNumber,PatientMRN,PatientName,DOB,EncounterDate,Type,Status");
    }

    @Test
    void exportPatientEncounterHistoryReportsEncountersNewestFirstWithLabelledFields() {
        insertPatient(1L, "MRN001", "Ada", "Lovelace", LocalDate.of(1980, 1, 2));
        insertEncounter(11L, 1L, "ENC-OLD", START.atTime(8, 0), "OFFICE", "FINISHED");
        insertEncounter(12L, 1L, "ENC-NEW", END.atTime(8, 0), "INPATIENT", "IN_PROGRESS");

        String report = new String(service.exportPatientEncounterHistory(1L), StandardCharsets.UTF_8);

        assertThat(report).contains("Patient: Ada Lovelace", "MRN: MRN001", "DOB: 1980-01-02");
        assertThat(report.indexOf("ENC-NEW")).isLessThan(report.indexOf("ENC-OLD"));
        assertThat(report).contains("Encounter #: ENC-NEW");
        assertThat(report).contains("Type: INPATIENT");
        assertThat(report).contains("Status: IN_PROGRESS");
        assertThat(report).contains("Date: 2024-03-12 08:00");
    }

    @Test
    void exportPatientEncounterHistoryRendersPatientWithoutEncounters() {
        insertPatient(1L, "MRN001", "Ada", "Lovelace", LocalDate.of(1980, 1, 2));

        String report = new String(service.exportPatientEncounterHistory(1L), StandardCharsets.UTF_8);

        assertThat(report).contains("Encounters:");
        assertThat(report).doesNotContain("Encounter #:");
    }

    @Test
    void exportPatientEncounterHistoryFailsWithEntityNotFoundForUnknownPatient() {
        assertThatThrownBy(() -> service.exportPatientEncounterHistory(4242L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("4242");
    }

    @Test
    void exportAllPatientsToFileWritesActivePatientsInHeaderOrder() throws Exception {
        insertPatient(1L, "MRN001", "Ada", "Lovelace", LocalDate.of(1980, 1, 2));

        File file = File.createTempFile("patients-export", ".csv");
        file.deleteOnExit();
        service.exportAllPatientsToFile(file.getAbsolutePath());

        List<String> lines = Files.readAllLines(Path.of(file.getAbsolutePath()));
        assertThat(lines.get(0))
                .isEqualTo("ID,MRN,FirstName,LastName,DOB,Email,PhoneHome,PhoneMobile,Street,City,State,Zip");
        assertThat(lines.get(1))
                .isEqualTo("1,MRN001,Ada,Lovelace,1980-01-02,ada@example.com,555-0100,555-0101,1 Main St,Springfield,IL,62701");
    }
}
