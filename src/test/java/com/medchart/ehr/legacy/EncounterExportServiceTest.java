package com.medchart.ehr.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(EncounterExportService.class)
class EncounterExportServiceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EncounterExportService service;

    private Long patientId;

    @BeforeEach
    void seed() {
        Long providerId = LegacyExportTestData.insertProvider(entityManager, "1000000001", "Ada", "Stone");
        patientId = LegacyExportTestData.insertPatient(entityManager, "MRN-EXP-1", "Jane", "Doe",
                "123-45-6789", LocalDate.of(1980, 5, 4), true);

        // One encounter on each boundary of the 2024-02-10 .. 2024-02-12 range, plus one just outside each end.
        LegacyExportTestData.insertEncounter(entityManager, "ENC-BEFORE", patientId, providerId,
                LocalDateTime.of(2024, 2, 9, 23, 59, 59), "OUTPATIENT", "FINISHED");
        LegacyExportTestData.insertEncounter(entityManager, "ENC-START", patientId, providerId,
                LocalDateTime.of(2024, 2, 10, 0, 0, 0), "OUTPATIENT", "FINISHED");
        LegacyExportTestData.insertEncounter(entityManager, "ENC-MIDDLE", patientId, providerId,
                LocalDateTime.of(2024, 2, 11, 12, 0, 0), "EMERGENCY", "IN_PROGRESS");
        LegacyExportTestData.insertEncounter(entityManager, "ENC-END", patientId, providerId,
                LocalDateTime.of(2024, 2, 12, 23, 59, 59), "INPATIENT", "FINISHED");
        LegacyExportTestData.insertEncounter(entityManager, "ENC-AFTER", patientId, providerId,
                LocalDateTime.of(2024, 2, 13, 0, 0, 0), "OUTPATIENT", "PLANNED");
        entityManager.flush();
    }

    @Test
    void dateRangeIncludesBothBoundaryDaysAndExcludesTheDayAfter() {
        List<String> rows = csvRows(service.exportEncountersForDateRange(
                LocalDate.of(2024, 2, 10), LocalDate.of(2024, 2, 12)));

        assertThat(rows.get(0))
                .isEqualTo("EncounterId,EncounterNumber,PatientMRN,PatientName,DOB,EncounterDate,Type,Status");
        assertThat(rows.subList(1, rows.size()))
                .extracting(row -> row.split(",")[1])
                .containsExactlyInAnyOrder("ENC-START", "ENC-MIDDLE", "ENC-END");
    }

    @Test
    void midnightOnTheDayAfterTheRangeIsNotExported() {
        List<String> rows = csvRows(service.exportEncountersForDateRange(
                LocalDate.of(2024, 2, 12), LocalDate.of(2024, 2, 12)));

        assertThat(rows.subList(1, rows.size()))
                .extracting(row -> row.split(",")[1])
                .containsExactly("ENC-END");
    }

    @Test
    void singleDayRangeExportsOnlyThatDay() {
        List<String> rows = csvRows(service.exportEncountersForDateRange(
                LocalDate.of(2024, 2, 11), LocalDate.of(2024, 2, 11)));

        assertThat(rows.subList(1, rows.size()))
                .extracting(row -> row.split(",")[1])
                .containsExactly("ENC-MIDDLE");
    }

    @Test
    void emptyRangeStillProducesHeaderOnlyCsv() {
        List<String> rows = csvRows(service.exportEncountersForDateRange(
                LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31)));

        assertThat(rows).containsExactly(
                "EncounterId,EncounterNumber,PatientMRN,PatientName,DOB,EncounterDate,Type,Status");
    }

    @Test
    void invertedRangeExportsNothing() {
        List<String> rows = csvRows(service.exportEncountersForDateRange(
                LocalDate.of(2024, 2, 12), LocalDate.of(2024, 2, 10)));

        assertThat(rows).hasSize(1);
    }

    @Test
    void dateParametersAreBoundNotConcatenated() {
        String sqlInjectionAttempt = "2024-02-11'; DROP TABLE encounters; --";

        assertThatThrownBy(() -> LocalDate.parse(sqlInjectionAttempt))
                .isInstanceOf(java.time.format.DateTimeParseException.class);

        service.exportEncountersForDateRange(LocalDate.of(2024, 2, 10), LocalDate.of(2024, 2, 12));

        Number remaining = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM encounters").getSingleResult();
        assertThat(remaining.intValue()).isEqualTo(5);
    }

    @Test
    void exportedRowCarriesPatientAndEncounterColumnsInHeaderOrder() {
        List<String> rows = csvRows(service.exportEncountersForDateRange(
                LocalDate.of(2024, 2, 11), LocalDate.of(2024, 2, 11)));

        String[] columns = rows.get(1).split(",");
        assertThat(columns[1]).isEqualTo("ENC-MIDDLE");
        assertThat(columns[2]).isEqualTo("MRN-EXP-1");
        assertThat(columns[3]).isEqualTo("Jane Doe");
        assertThat(columns[4]).isEqualTo("1980-05-04");
        assertThat(columns[5]).startsWith("2024-02-11");
        assertThat(columns[6]).isEqualTo("EMERGENCY");
        assertThat(columns[7]).isEqualTo("IN_PROGRESS");
    }

    @Test
    void patientHistoryLabelsEncounterFieldsCorrectly() {
        String history = new String(service.exportPatientEncounterHistory(patientId), StandardCharsets.UTF_8);

        assertThat(history).contains("MRN: MRN-EXP-1");
        assertThat(history).contains("Encounter #: ENC-AFTER");
        assertThat(history).contains("Type: OUTPATIENT");
        assertThat(history).contains("Status: PLANNED");
        assertThat(history).contains("Type: EMERGENCY");
        assertThat(history).contains("Status: IN_PROGRESS");
        assertThat(history).doesNotContain("Type: 1");
        assertThat(history).doesNotContain("Date: OUTPATIENT");
    }

    @Test
    void patientHistoryOrdersEncountersMostRecentFirst() {
        String history = new String(service.exportPatientEncounterHistory(patientId), StandardCharsets.UTF_8);

        assertThat(history.indexOf("ENC-AFTER"))
                .isLessThan(history.indexOf("ENC-END"))
                .isLessThan(history.indexOf("ENC-BEFORE"));
    }

    @Test
    void patientHistoryForUnknownPatientFails() {
        assertThatThrownBy(() -> service.exportPatientEncounterHistory(999_999L))
                .isInstanceOf(NoResultException.class);
    }

    @Test
    void exportAllPatientsToFileWritesOnlyActivePatients() throws Exception {
        LegacyExportTestData.insertPatient(entityManager, "MRN-EXP-2", "Inactive", "Patient",
                "987-65-4321", LocalDate.of(1975, 3, 2), false);
        entityManager.flush();

        File target = File.createTempFile("patients_export", ".csv");
        target.deleteOnExit();
        try {
            service.exportAllPatientsToFile(target.getAbsolutePath());

            List<String> lines = Files.readAllLines(Path.of(target.getAbsolutePath()));
            assertThat(lines.get(0))
                    .isEqualTo("ID,MRN,FirstName,LastName,DOB,Email,PhoneHome,PhoneMobile,Street,City,State,Zip");
            assertThat(lines).anyMatch(line -> line.contains("MRN-EXP-1"));
            assertThat(lines).noneMatch(line -> line.contains("MRN-EXP-2"));
        } finally {
            Files.deleteIfExists(Path.of(target.getAbsolutePath()));
        }
    }

    private List<String> csvRows(byte[] csv) {
        return List.of(new String(csv, StandardCharsets.UTF_8).split("\n"));
    }
}
