package com.medchart.ehr.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportSsnRedactionTest {

    private static final String SSN = "123-45-6789";

    private EntityManager entityManager;
    private Query query;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
    }

    @Test
    void dailyReportOmitsSsnAndDeletesTempFile() throws Exception {
        List<Object[]> rows = Collections.singletonList(new Object[]{
                1L, "MRN1", "Ada", "Lovelace", "1990-01-01", "555-0100", "555-0101", "ada@example.com",
                "1 Main St", "Springfield", "IL", "62701", "Acme Health", "M123"});
        when(query.getResultList()).thenReturn(rows);
        ReportGenerator generator = new ReportGenerator();
        ReflectionTestUtils.setField(generator, "entityManager", entityManager);

        long before = countRosterFiles();
        String csv = new String(generator.generateDailyReport(), StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("ID,MRN,FirstName,LastName,DOB,"));
        assertFalse(csv.contains("SSN"));
        assertTrue(csv.contains("1,MRN1,Ada,Lovelace,1990-01-01,"));
        assertEquals(before, countRosterFiles());
        assertSqlNeverSelectsSsn();
    }

    @Test
    void encounterSummaryOmitsSsnAndLabelsDob() throws Exception {
        List<Object[]> rows = Collections.singletonList(new Object[]{
                10L, "ENC-1", "2026-01-02T09:00", "OUTPATIENT", "COMPLETED",
                "MRN1", "Ada", "Lovelace", "1990-01-01", "Grace", "Hopper"});
        when(query.getResultList()).thenReturn(rows);
        ReportGenerator generator = new ReportGenerator();
        ReflectionTestUtils.setField(generator, "entityManager", entityManager);

        Path file = Path.of(generator.generateEncounterSummary(
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 31, 0, 0)));
        try {
            String text = Files.readString(file);
            assertTrue(text.contains("DOB: 1990-01-01"));
            assertTrue(text.contains("Provider: Grace Hopper"));
        } finally {
            Files.deleteIfExists(file);
        }
        assertSqlNeverSelectsSsn();
    }

    @Test
    void patientEncounterHistoryOmitsSsn() {
        when(query.getSingleResult()).thenReturn(new Object[]{"MRN1", "Ada", "Lovelace", "1990-01-01"});
        when(query.getResultList()).thenReturn(Collections.emptyList());
        EncounterExportService service = new EncounterExportService();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        String text = new String(service.exportPatientEncounterHistory(1L), StandardCharsets.UTF_8);

        assertFalse(text.contains("SSN"));
        assertFalse(text.contains(SSN));
        assertTrue(text.contains("MRN: MRN1"));
        assertTrue(text.contains("DOB: 1990-01-01"));
        assertSqlNeverSelectsSsn();
    }

    private void assertSqlNeverSelectsSsn() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, atLeastOnce()).createNativeQuery(sql.capture());
        for (String statement : sql.getAllValues()) {
            assertFalse(statement.toLowerCase().contains("ssn"), statement);
        }
    }

    private static long countRosterFiles() {
        File[] files = new File(System.getProperty("java.io.tmpdir"))
                .listFiles((dir, name) -> name.startsWith("patient_roster_"));
        return files == null ? 0 : files.length;
    }
}
