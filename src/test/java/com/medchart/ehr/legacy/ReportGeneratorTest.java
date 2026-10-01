package com.medchart.ehr.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ReportGenerator.class)
class ReportGeneratorTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ReportGenerator reportGenerator;

    private final List<Path> generatedFiles = new ArrayList<>();

    @BeforeEach
    void seed() {
        Long providerId = LegacyExportTestData.insertProvider(entityManager, "1000000002", "Ben", "Clark");
        Long activePatient = LegacyExportTestData.insertPatient(entityManager, "MRN-RPT-1", "Alice", "Ng",
                "111-22-3333", LocalDate.of(1990, 7, 8), true);
        LegacyExportTestData.insertPatient(entityManager, "MRN-RPT-2", "Bob", "Gone",
                "444-55-6666", LocalDate.of(1970, 1, 2), false);
        LegacyExportTestData.insertCoverage(entityManager, activePatient, "Acme Health", "MEM-1", true);

        LegacyExportTestData.insertEncounter(entityManager, "RPT-IN-RANGE", activePatient, providerId,
                LocalDateTime.of(2024, 3, 5, 9, 30), "OUTPATIENT", "FINISHED");
        LegacyExportTestData.insertEncounter(entityManager, "RPT-LOWER-BOUND", activePatient, providerId,
                LocalDateTime.of(2024, 3, 1, 0, 0), "OUTPATIENT", "FINISHED");
        LegacyExportTestData.insertEncounter(entityManager, "RPT-UPPER-BOUND", activePatient, providerId,
                LocalDateTime.of(2024, 3, 31, 23, 59), "INPATIENT", "FINISHED");
        LegacyExportTestData.insertEncounter(entityManager, "RPT-OUT-OF-RANGE", activePatient, providerId,
                LocalDateTime.of(2024, 4, 1, 0, 1), "EMERGENCY", "PLANNED");
        entityManager.flush();
    }

    @AfterEach
    void cleanUpGeneratedFiles() throws Exception {
        for (Path path : generatedFiles) {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void patientRosterContainsOnlyActivePatientsWithCoverage() throws Exception {
        List<String> lines = readReport(reportGenerator.generatePatientRoster());

        assertThat(lines.get(0)).isEqualTo(
                "ID,MRN,SSN,FirstName,LastName,DOB,PhoneHome,PhoneMobile,Email,Address,City,State,Zip,Insurance,MemberID");
        assertThat(lines).anyMatch(line -> line.contains("MRN-RPT-1") && line.contains("Acme Health")
                && line.endsWith("MEM-1"));
        assertThat(lines).noneMatch(line -> line.contains("MRN-RPT-2"));
    }

    @Test
    void patientRosterFileNameIsTimestampedCsvInTempDir() {
        String filePath = track(reportGenerator.generatePatientRoster());

        assertThat(Path.of(filePath).getFileName().toString())
                .matches("patient_roster_\\d{8}_\\d{6}\\.csv");
        assertThat(Path.of(filePath).getParent())
                .isEqualTo(Path.of(System.getProperty("java.io.tmpdir")));
    }

    @Test
    void encounterSummaryRangeIsInclusiveOfBothEndpoints() throws Exception {
        String report = String.join("\n", readReport(reportGenerator.generateEncounterSummary(
                LocalDateTime.of(2024, 3, 1, 0, 0), LocalDateTime.of(2024, 3, 31, 23, 59))));

        assertThat(report).contains("Total Encounters: 3");
        assertThat(report).contains("RPT-LOWER-BOUND", "RPT-IN-RANGE", "RPT-UPPER-BOUND");
        assertThat(report).doesNotContain("RPT-OUT-OF-RANGE");
    }

    @Test
    void encounterSummaryForEmptyRangeReportsZeroEncounters() throws Exception {
        String report = String.join("\n", readReport(reportGenerator.generateEncounterSummary(
                LocalDateTime.of(2023, 1, 1, 0, 0), LocalDateTime.of(2023, 1, 31, 0, 0))));

        assertThat(report).contains("ENCOUNTER SUMMARY REPORT");
        assertThat(report).contains("Total Encounters: 0");
    }

    @Test
    void dailyReportReturnsRosterContentAsBytes() throws Exception {
        byte[] daily = reportGenerator.generateDailyReport();

        String content = new String(daily, StandardCharsets.UTF_8);
        assertThat(content).startsWith("ID,MRN,SSN,FirstName,LastName,DOB");
        assertThat(content).contains("MRN-RPT-1");

        trackTempReports("patient_roster_");
    }

    private List<String> readReport(String filePath) throws Exception {
        return Files.readAllLines(Path.of(track(filePath)));
    }

    private String track(String filePath) {
        generatedFiles.add(Path.of(filePath));
        return filePath;
    }

    private void trackTempReports(String prefix) throws Exception {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (var stream = Files.list(tempDir)) {
            stream.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .forEach(generatedFiles::add);
        }
    }
}
