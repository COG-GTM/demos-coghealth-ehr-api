package com.medchart.ehr.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrPtoSyncJobTest {

    @TempDir
    Path outDir;

    private HrPtoSyncJob job;

    @BeforeEach
    void setUp() {
        System.setProperty(HrPtoSyncJob.OUT_DIR_PROPERTY, outDir.toString());
        job = new HrPtoSyncJob();
        ReflectionTestUtils.setField(job, "hrExportReader", new HrExportReader());
        ReflectionTestUtils.setField(job, "departmentMapping", new DepartmentMapping());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(HrPtoSyncJob.OUT_DIR_PROPERTY);
    }

    @Test
    void runForDateRejectsCraftedExportDateWithoutWritingLogs() {
        assertThrows(InvalidExportDateException.class,
                () -> job.runForDate("2026,x:y/../workday_pto_export_20261008"));
        assertFalse(Files.exists(outDir.resolve(HrPtoSyncJob.RUNS_FILE)));
        assertFalse(Files.exists(outDir.resolve(HrPtoSyncJob.ERROR_QUEUE_FILE)));
    }

    @Test
    void runLogRoundTripsThroughJackson() {
        Map<String, Object> result = job.runForDate("20260930");
        List<Map<String, Object>> runs = job.readRuns();
        assertEquals(1, runs.size());
        assertEquals("20260930", runs.get(0).get("exportDate"));
        assertEquals(result.get("records"), runs.get(0).get("records"));
        assertEquals(result.get("status"), runs.get(0).get("status"));
    }

    @Test
    void readRunsParsesValuesContainingCommasAndColonsAndSkipsUnreadableLines() throws IOException {
        Files.write(outDir.resolve(HrPtoSyncJob.RUNS_FILE), Arrays.asList(
                "{\"job\":\"hr-pto-sync\",\"exportDate\":\"2026,x:y/../workday_pto_export_20261008\",\"records\":5}",
                "{\"job\":\"hr-pto-sync\",\"exportDate\":\"20261008\",\"note\":\"a \\\"quoted\\\", b:c\",\"records\":3}",
                "{\"job\":\"hr-pto-sync\",\"exportDate\":\"20261007\"\"broken",
                ""), StandardCharsets.UTF_8);

        List<Map<String, Object>> runs = job.readRuns();

        assertEquals(2, runs.size());
        assertEquals("2026,x:y/../workday_pto_export_20261008", runs.get(0).get("exportDate"));
        assertEquals(5, runs.get(0).get("records"));
        assertEquals("a \"quoted\", b:c", runs.get(1).get("note"));
        assertEquals(3, runs.get(1).get("records"));
    }

    @Test
    void parsesIsoSingleDay() {
        List<LocalDate> days = HrPtoSyncJob.parsePtoDays("2026-10-14", "2026-10-14", 2026);
        assertEquals(1, days.size());
        assertEquals(LocalDate.of(2026, 10, 14), days.get(0));
    }

    @Test
    void parsesFreeTextRange() {
        List<LocalDate> days = HrPtoSyncJob.parsePtoDays("10/13-10/17 vacation", "", 2026);
        assertEquals(5, days.size());
        assertEquals(LocalDate.of(2026, 10, 13), days.get(0));
        assertEquals(LocalDate.of(2026, 10, 17), days.get(4));
    }

    @Test
    void parsesFreeTextSingleDay() {
        List<LocalDate> days = HrPtoSyncJob.parsePtoDays("Thu 10/22, half day AM", "", 2026);
        assertEquals(1, days.size());
        assertEquals(LocalDate.of(2026, 10, 22), days.get(0));
        assertEquals("THU", HrPtoSyncJob.weekdayOf(days.get(0)));
    }

    @Test
    void buildsSiuS15ForBlock() {
        HrPtoRecord r = new HrPtoRecord();
        r.employeeId = "E104422";
        r.lastName = "Marquez";
        r.firstName = "Elena";
        r.npi = "1932405817";
        r.ptoStart = "10/13-10/17 vacation";
        r.ptoEnd = "";
        r.ptoType = "Vacation";
        OrBlock block = new OrBlock(1L, 2130, "TUE", "07:00", "15:00", "OR-4");

        String msg = HrPtoSyncJob.buildSiuS15(r, 2130, block, LocalDate.of(2026, 10, 13), "HRPTO2026100800001");
        String[] segments = msg.split("\r");

        assertTrue(segments[0].startsWith("MSH|^~\\&|COGHEALTH_EHR|HRPTO|IEPROD|OPTIME|"));
        assertTrue(segments[0].contains("||SIU^S15^SIU_S12|HRPTO2026100800001|P|2.5"));
        assertTrue(segments[1].startsWith("SCH|HRPTO2026100800001|"));
        assertTrue(segments[1].contains("^202610130700^202610131500"));
        assertTrue(segments[1].contains("|480|MIN|"));
        assertTrue(segments[3].startsWith("AIS|1||BLOCK^OR block^L|202610130700||480|MIN"));
        assertEquals("AIL|1||OR-4^^^2130|OR^Operating room", segments[4]);
        assertTrue(segments[5].startsWith("AIP|1||E104422^Marquez^Elena^^^Dr.^^^^^^^^NPI|SURGEON^Primary surgeon|202610130700||480|MIN"));
    }
}
