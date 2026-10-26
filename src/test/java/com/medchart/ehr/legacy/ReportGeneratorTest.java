package com.medchart.ehr.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportGeneratorTest {

    private static final Object[] ROSTER_ROW = {
            1L, "MRN0001", "123-45-6789", "Jane", "Doe", "1980-02-03",
            "555-0100", null, "jane@example.test", "1 Main St, Apt 2", "Springfield", "IL", "62701",
            "Acme Health", "M123"
    };

    private static final Object[] ENCOUNTER_ROW = {
            10L, "ENC-10", "2026-10-01T09:00", "OFFICE_VISIT", "COMPLETED",
            "MRN0001", "Jane", "Doe", "1980-02-03", "Greg", "House"
    };

    private EntityManager entityManager;
    private Query query;
    private ReportGenerator generator;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        generator = new ReportGenerator();
        ReflectionTestUtils.setField(generator, "entityManager", entityManager);
    }

    @Test
    void patientRosterIsBuiltInMemoryWithoutTouchingTempDir() throws IOException {
        when(query.getResultList()).thenReturn(List.<Object[]>of(ROSTER_ROW));
        Set<Path> before = tempDirEntries();

        String csv = new String(generator.generatePatientRoster(), StandardCharsets.UTF_8);

        assertThat(tempDirEntries()).isEqualTo(before);
        assertThat(csv).startsWith("ID,MRN,SSN,FirstName,LastName,DOB,");
        assertThat(csv).contains("1,MRN0001,123-45-6789,Jane,Doe,1980-02-03,555-0100,,jane@example.test,1 Main St; Apt 2,");
        assertThat(csv).doesNotContain(System.getProperty("java.io.tmpdir"));
    }

    @Test
    void dailyReportReturnsRosterWithoutTempFiles() throws IOException {
        when(query.getResultList()).thenReturn(List.<Object[]>of(ROSTER_ROW));
        Set<Path> before = tempDirEntries();

        byte[] daily = generator.generateDailyReport();

        assertThat(tempDirEntries()).isEqualTo(before);
        assertThat(new String(daily, StandardCharsets.UTF_8)).contains("MRN0001");
    }

    @Test
    void encounterSummaryIsBuiltInMemoryAndOmitsSsn() throws IOException {
        when(query.getResultList()).thenReturn(List.<Object[]>of(ENCOUNTER_ROW));
        Set<Path> before = tempDirEntries();

        String text = new String(generator.generateEncounterSummary(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 2, 0, 0)), StandardCharsets.UTF_8);

        assertThat(tempDirEntries()).isEqualTo(before);
        assertThat(text).contains("Total Encounters: 1");
        assertThat(text).contains("  DOB: 1980-02-03");
        assertThat(text).contains("  Provider: Greg House");
        assertThat(text).doesNotContain("123-45-6789");
    }

    @Test
    void emptyResultsProduceHeaderOnly() {
        when(query.getResultList()).thenReturn(Collections.emptyList());

        String csv = new String(generator.generatePatientRoster(), StandardCharsets.UTF_8);

        assertThat(csv.split("\n")).hasSize(1);
    }

    private static Set<Path> tempDirEntries() throws IOException {
        try (Stream<Path> entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return entries.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("patient_roster_") || name.startsWith("encounter_summary_");
            }).collect(Collectors.toSet());
        }
    }
}
