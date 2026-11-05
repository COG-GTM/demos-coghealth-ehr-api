package com.medchart.ehr.legacy;

import org.junit.jupiter.api.DisplayName;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGeneratorTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;
    @Mock
    private Query deptQuery;
    @Mock
    private Query countQuery;
    @InjectMocks
    private ReportGenerator generator;

    private static List<Object[]> rows(Object[]... r) {
        return new ArrayList<>(List.of(r));
    }

    @Test
    @DisplayName("BR-LEG-09: the daily report returns the active-patient roster and deletes the PHI temp file")
    void brLeg09_dailyReportCleansTempFile() throws Exception {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("WHERE p.active = true")))).thenReturn(query);
        Object[] row = {1L, "MRN-1", "000-00-0000", "Test", "Patient", "2000-01-01", null, null, null,
                "1 Test St, Apt 2", "Testville", "NY", "00000", "Payer", "MEM-1"};
        when(query.getResultList()).thenReturn(rows(row));

        try (var tmp = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            long before = tmp.filter(p -> p.getFileName().toString().startsWith("patient_roster_")).count();
            String csv = new String(generator.generateDailyReport(), StandardCharsets.UTF_8);
            assertTrue(csv.startsWith("ID,MRN,SSN,FirstName"));
            assertTrue(csv.contains("1 Test St; Apt 2"), "embedded commas are neutralised");
            assertTrue(csv.contains("1,MRN-1,000-00-0000,Test,Patient,2000-01-01,,,,"));
            try (var after = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
                assertEquals(before, after.filter(p -> p.getFileName().toString().startsWith("patient_roster_")).count());
            }
        }
    }

    @Test
    @DisplayName("BR-LEG-06: the encounter summary prints DOB and provider name and never the SSN")
    void brLeg06_encounterSummaryColumns() throws Exception {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("encounter_date_time BETWEEN")))).thenReturn(query);
        Object[] row = {1L, "ENC-2026-000001", "2026-01-31T10:00", "OUTPATIENT", "COMPLETED",
                "MRN-1", "Test", "Patient", "000-00-0000", "2000-01-01", "Doc", "Tor"};
        when(query.getResultList()).thenReturn(rows(row));
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);

        Path file = Path.of(generator.generateEncounterSummary(start, end));
        try {
            String out = Files.readString(file);
            verify(query).setParameter(1, start);
            verify(query).setParameter(2, end);
            assertTrue(out.contains("Total Encounters: 1"));
            assertTrue(out.contains("  Patient: Test Patient (MRN: MRN-1)"));
            assertTrue(out.contains("  DOB: 2000-01-01"));
            assertTrue(out.contains("  Provider: Doc Tor"));
            assertFalse(out.contains("000-00-0000"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("BR-LEG-10: department productivity binds department and provider as parameters and counts encounters")
    void brLeg10_departmentProductivity() {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.startsWith("SELECT DISTINCT department")))).thenReturn(deptQuery);
        when(deptQuery.getResultList()).thenReturn(List.of("Cardiology' OR '1'='1"));
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("pr.department = :department")))).thenReturn(query);
        when(query.setParameter("department", "Cardiology' OR '1'='1")).thenReturn(query);
        when(query.getResultList()).thenReturn(rows(new Object[] {"Tor", 7L}, new Object[] {"Nil", null}));
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.startsWith("SELECT COUNT(*)")))).thenReturn(countQuery);
        when(countQuery.setParameter(eq("pid"), anyLong())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(3L, 0L);

        String report = generator.generateDepartmentProductivityReport();

        verify(countQuery).setParameter("pid", 7L);
        verify(countQuery).setParameter("pid", -1L);
        verify(entityManager, never()).createNativeQuery(argThat((String s) -> s != null && s.contains("'Cardiology")));
        assertTrue(report.contains("Department: Cardiology' OR '1'='1"));
        assertTrue(report.contains("  Tor: 3 encounters"));
        assertTrue(report.contains("  Nil: 0 encounters"));
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            files.filter(p -> p.getFileName().toString().startsWith("department_productivity_")).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
            // best-effort cleanup of the report copy
        }
    }
}
