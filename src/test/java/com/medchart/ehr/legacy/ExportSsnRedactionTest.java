package com.medchart.ehr.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportSsnRedactionTest {

    private static final String SSN = "123-45-6789";
    private static final String MEMBER_ID = "XWB884412397";

    private EntityManager entityManager;
    private Query query;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    @Test
    void dailyReportHasNoSsnAndMasksMemberId() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, "MRN0001", "Alice", "Smith", LocalDate.of(1980, 1, 2),
                "555-0100", "555-0101", "alice@example.com", "1 Main St", "Springfield", "IL", "62701",
                "Acme Health", MEMBER_ID});
        when(query.getResultList()).thenReturn(rows);
        ReportGenerator generator = reportGenerator();

        String csv = new String(generator.generateDailyReport(), StandardCharsets.UTF_8);

        String header = csv.lines().findFirst().orElseThrow();
        assertThat(header).doesNotContain("SSN");
        assertThat(header.split(",")).hasSize(rows.get(0).length);
        assertThat(csv).doesNotContain(MEMBER_ID).contains(",****2397");
        assertThat(capturedSql()).noneMatch(sql -> sql.toLowerCase().contains("ssn"));
    }

    @Test
    void dailyReportDeletesItsTempFile() {
        when(query.getResultList()).thenReturn(Collections.emptyList());
        List<String> rosterPaths = new ArrayList<>();
        ReportGenerator generator = new ReportGenerator() {
            @Override
            public String generatePatientRoster() {
                String path = super.generatePatientRoster();
                rosterPaths.add(path);
                return path;
            }
        };
        ReflectionTestUtils.setField(generator, "entityManager", entityManager);

        generator.generateDailyReport();

        assertThat(rosterPaths).hasSize(1);
        assertThat(Path.of(rosterPaths.get(0))).doesNotExist();
    }

    @Test
    void dailyReportDoesNotDeleteAConcurrentRoster() throws Exception {
        when(query.getResultList()).thenReturn(Collections.emptyList());
        ReportGenerator generator = reportGenerator();

        Path roster = Path.of(generator.generatePatientRoster());
        try {
            Path second = Path.of(generator.generatePatientRoster());
            Files.deleteIfExists(second);
            generator.generateDailyReport();

            assertThat(second).isNotEqualTo(roster);
            assertThat(roster).exists();
        } finally {
            Files.deleteIfExists(roster);
        }
    }

    @Test
    void encounterSummaryNoLongerSelectsSsnAndPrintsRealDob() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, "ENC-1", LocalDateTime.of(2024, 3, 1, 9, 0), "OFFICE", "COMPLETED",
                "MRN0001", "Alice", "Smith", LocalDate.of(1980, 1, 2), "Greg", "House"});
        when(query.getResultList()).thenReturn(rows);

        Path file = Path.of(reportGenerator().generateEncounterSummary(
                LocalDateTime.of(2024, 1, 1, 0, 0), LocalDateTime.of(2024, 12, 31, 0, 0)));
        try {
            String text = Files.readString(file);
            assertThat(text).contains("  DOB: 1980-01-02").contains("  Provider: Greg House");
            assertThat(capturedSql()).noneMatch(sql -> sql.toLowerCase().contains("ssn"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void patientEncounterHistoryHasNoSsn() {
        when(query.getSingleResult()).thenReturn(new Object[]{"MRN0001", "Alice", "Smith", LocalDate.of(1980, 1, 2)});
        when(query.getResultList()).thenReturn(Collections.emptyList());
        EncounterExportService service = new EncounterExportService();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        String text = new String(service.exportPatientEncounterHistory(1L), StandardCharsets.UTF_8);

        assertThat(text).doesNotContain("SSN").doesNotContain(SSN).contains("DOB: 1980-01-02");
        assertThat(capturedSql()).noneMatch(sql -> sql.toLowerCase().contains("ssn"));
    }

    @Test
    void maskMemberIdKeepsOnlyLastFour() {
        assertThat(ReportGenerator.maskMemberId(MEMBER_ID)).isEqualTo("****2397");
        assertThat(ReportGenerator.maskMemberId("1234")).isEqualTo("****");
        assertThat(ReportGenerator.maskMemberId(null)).isNull();
    }

    private ReportGenerator reportGenerator() {
        ReportGenerator generator = new ReportGenerator();
        ReflectionTestUtils.setField(generator, "entityManager", entityManager);
        return generator;
    }

    private List<String> capturedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, atLeastOnce()).createNativeQuery(sql.capture());
        return sql.getAllValues();
    }
}
