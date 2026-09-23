package com.medchart.ehr.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGeneratorTest {

    private static final Path TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"));

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private ReportGenerator reportGenerator;

    private List<String> tempFilesBefore;

    @BeforeEach
    void captureTempDir() throws Exception {
        tempFilesBefore = listReportFiles();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    @Test
    void patientRosterMasksSsnAndLeavesNoPhiOnDisk() throws Exception {
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{
                1L, "MRN001", "123-45-6789", "Ada", "Lovelace", "1980-01-01",
                "555-0100", "555-0101", "ada@example.com",
                "1 Main St", "Boston", "MA", "02101", "Acme Health", "M12345"
        }));

        String csv = new String(reportGenerator.generatePatientRoster(), StandardCharsets.UTF_8);

        assertThat(csv).contains("XXX-XX-6789");
        assertThat(csv).doesNotContain("123-45-6789");
        assertThat(csv).contains("MRN001,XXX-XX-6789,Ada,Lovelace");
        assertThat(listReportFiles()).isEqualTo(tempFilesBefore);
    }

    @Test
    void encounterSummaryOmitsSsnAndLeavesNoPhiOnDisk() throws Exception {
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{
                7L, "ENC-7", LocalDateTime.of(2026, 1, 2, 9, 0), "OFFICE_VISIT", "FINISHED",
                "MRN001", "Ada", "Lovelace", "1980-01-01", "Alan", "Turing"
        }));

        String summary = new String(
                reportGenerator.generateEncounterSummary(
                        LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 3, 0, 0)),
                StandardCharsets.UTF_8);

        assertThat(summary).contains("Encounter: ENC-7");
        assertThat(summary).contains("  Patient: Ada Lovelace (MRN: MRN001)");
        assertThat(summary).contains("  DOB: 1980-01-01");
        assertThat(summary).contains("  Provider: Alan Turing");
        assertThat(summary).doesNotContain("SSN");
        assertThat(listReportFiles()).isEqualTo(tempFilesBefore);
    }

    @Test
    void dailyReportReturnsRosterContentWithoutTempFile() throws Exception {
        when(query.getResultList()).thenReturn(Collections.emptyList());

        String csv = new String(reportGenerator.generateDailyReport(), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("ID,MRN,SSN,");
        assertThat(listReportFiles()).isEqualTo(tempFilesBefore);
    }

    private static List<String> listReportFiles() throws Exception {
        try (Stream<Path> files = Files.list(TEMP_DIR)) {
            return files.map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith("patient_roster_") || name.startsWith("encounter_summary_"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
