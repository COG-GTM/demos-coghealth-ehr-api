package com.medchart.ehr.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Time;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrPtoSyncJobRulesTest {

    private static final String DATE = "20261005";

    @TempDir
    Path out;

    @Mock
    private HrExportReader reader;
    @Mock
    private DepartmentMapping mapping;
    @Mock
    private JdbcTemplate jdbc;
    @InjectMocks
    private HrPtoSyncJob job;

    @BeforeEach
    void outDir() {
        System.setProperty(HrPtoSyncJob.OUT_DIR_PROPERTY, out.toString());
    }

    @AfterEach
    void clear() {
        System.clearProperty(HrPtoSyncJob.OUT_DIR_PROPERTY);
    }

    static HrPtoRecord rec(String id, String cc, String start, String end, String status) {
        HrPtoRecord r = new HrPtoRecord();
        r.employeeId = id;
        r.lastName = "Test";
        r.firstName = "Surgeon";
        r.npi = "0000000000";
        r.costCenter = cc;
        r.ptoStart = start;
        r.ptoEnd = end;
        r.ptoType = "Vacation";
        r.status = status;
        r.notes = "";
        return r;
    }

    private static Map<String, Object> blockRow(String weekday) {
        return Map.of("provider_id", 9L, "department_id", 2130, "weekday", weekday,
                "start_time", Time.valueOf("07:30:00"), "end_time", "15:30:00", "room", "OR-1");
    }

    private List<Path> outbound() throws Exception {
        Path dir = out.resolve(HrPtoSyncJob.OUTBOUND_DIR).resolve(DATE);
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (var s = Files.list(dir)) {
            return s.sorted().collect(Collectors.toList());
        }
    }

    @Test
    @DisplayName("BR-LEG-13: one SIU^S15 per PTO day that falls on one of the surgeon's OR block weekdays")
    void brLeg13_messagePerBlockDay() throws Exception {
        when(reader.read(DATE)).thenReturn(List.of(rec("E1", "CC-1", "2026-10-12", "2026-10-16", "Approved")));
        when(mapping.lookup("CC-1")).thenReturn(2130);
        when(jdbc.queryForList(anyString(), eq("E1"))).thenReturn(List.of(blockRow("MON"), blockRow("WED")));

        Map<String, Object> result = job.runForDate(DATE);

        assertEquals(2, result.get("messagesSent"));
        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("records"));
        List<Path> files = outbound();
        assertEquals(List.of("HRPTO" + DATE + "00001.hl7", "HRPTO" + DATE + "00002.hl7"),
                files.stream().map(p -> p.getFileName().toString()).collect(Collectors.toList()));
        String msg = Files.readString(files.get(0));
        assertTrue(msg.startsWith("MSH|^~\\&|COGHEALTH_EHR|HRPTO|IEPROD|OPTIME|"));
        assertTrue(msg.contains("|SIU^S15^SIU_S12|HRPTO" + DATE + "00001|P|2.5\r"));
        assertTrue(msg.contains("^^480^202610120730^202610121530"));
        assertTrue(msg.contains("AIL|1||OR-1^^^2130|"));
        assertTrue(Files.readString(files.get(1)).contains("^202610140730^"));
    }

    @Test
    @DisplayName("BR-LEG-11: an unknown cost center rejects the row (AE error queued) and marks the run PARTIAL")
    void brLeg11_unknownCostCenterRejected() {
        when(reader.read(DATE)).thenReturn(List.of(rec("E2", "CC-404", "2026-10-12", "", "Approved")));
        when(mapping.lookup("CC-404")).thenReturn(null);

        Map<String, Object> result = job.runForDate(DATE);

        assertEquals(1, result.get("recordsRejected"));
        assertEquals("PARTIAL", result.get("status"));
        verify(jdbc, never()).queryForList(anyString(), anyString());
        List<Map<String, Object>> errors = job.readErrors();
        assertEquals(1, errors.size());
        assertEquals("AE", errors.get(0).get("code"));
        assertEquals("E2", errors.get(0).get("employeeId"));
        assertEquals(DATE, errors.get(0).get("exportDate"));
        assertEquals("AE – Unknown department for cost center CC-404", errors.get(0).get("message"));
        assertEquals(Map.of("CC-404", 1), job.errorCountsByCostCenter());
    }

    @Test
    @DisplayName("BR-LEG-12: rows without PTO or not Approved are skipped, not sent or rejected")
    void brLeg12_skipNoPtoOrUnapproved() {
        when(reader.read(DATE)).thenReturn(List.of(
                rec("E3", "CC-1", "", "", "Approved"),
                rec("E4", "CC-1", "2026-10-12", "", "Pending")));
        when(mapping.lookup("CC-1")).thenReturn(2130);

        Map<String, Object> result = job.runForDate(DATE);

        assertEquals(2, result.get("recordsSkipped"));
        assertEquals(0, result.get("messagesSent"));
        assertEquals("OK", result.get("status"));
    }

    @Test
    @DisplayName("BR-LEG-15: every run is appended to the run log and can be read back with typed counts")
    void brLeg15_runLog() {
        when(reader.read(DATE)).thenReturn(List.of());
        assertTrue(job.readRuns().isEmpty());
        assertTrue(job.readErrors().isEmpty());

        job.runForDate(DATE);
        job.runForDate(DATE);

        List<Map<String, Object>> runs = job.readRuns();
        assertEquals(2, runs.size());
        assertEquals("hr-pto-sync", runs.get(0).get("job"));
        assertEquals(DATE, runs.get(0).get("exportDate"));
        assertEquals(0, runs.get(0).get("messagesSent"));
        assertEquals("OK", runs.get(1).get("status"));
    }

    @Test
    @DisplayName("BR-LEG-15: malformed error-queue and run-log lines are ignored; non-AE errors are coded AR")
    void brLeg15_malformedLogLines() throws Exception {
        Files.writeString(out.resolve(HrPtoSyncJob.ERROR_QUEUE_FILE),
                "garbage\n2026-10-05 02:00:00|" + DATE + "|E9|AR – Application reject\n2026-10-05 02:00:00|" + DATE + "|E8|nospace\n");
        Files.writeString(out.resolve(HrPtoSyncJob.RUNS_FILE), "{\"job\":\"hr-pto-sync\",novalue,\"records\":3}\nplain:\"x\"\n");

        List<Map<String, Object>> errors = job.readErrors();
        assertEquals(2, errors.size());
        assertEquals("AR", errors.get(0).get("code"));
        assertEquals(Map.of("reject", 1, "nospace", 1), job.errorCountsByCostCenter());
        List<Map<String, Object>> runs = job.readRuns();
        assertEquals(3, runs.get(0).get("records"));
        assertFalse(runs.get(0).containsKey("novalue"));
        assertEquals("x", runs.get(1).get("plain"));
    }

    @Test
    @DisplayName("BR-LEG-15: an unwritable output directory does not abort the run (failures are logged)")
    void brLeg15_unwritableOutputTolerated() throws Exception {
        Path file = Files.createFile(out.resolve("blocked"));
        System.setProperty(HrPtoSyncJob.OUT_DIR_PROPERTY, file.toString());
        when(reader.read(DATE)).thenReturn(List.of(
                rec("E5", "CC-404", "2026-10-12", "", "Approved"),
                rec("E6", "CC-1", "2026-10-12", "", "Approved")));
        when(mapping.lookup("CC-404")).thenReturn(null);
        when(mapping.lookup("CC-1")).thenReturn(2130);
        when(jdbc.queryForList(anyString(), eq("E6"))).thenReturn(List.of(blockRow("MON")));

        Map<String, Object> result = job.runForDate(DATE);
        assertEquals(1, result.get("messagesSent"));
        assertEquals(1, result.get("recordsRejected"));
    }

    @Test
    @DisplayName("BR-LEG-14: without a datasource no OR blocks are loaded and no messages are sent")
    void brLeg14_noDatasource() {
        ReflectionTestUtils.setField(job, "jdbcTemplate", null);
        assertTrue(job.loadOrBlocks("E7").isEmpty());
        assertEquals(Path.of(out.toString()).toFile(), HrPtoSyncJob.outDir());
    }

    @Test
    @DisplayName("BR-LEG-14: OR block rows map provider, department, weekday, HH:mm times and room")
    void brLeg14_blockRowMapping() {
        when(jdbc.queryForList(anyString(), eq("E8"))).thenReturn(List.of(blockRow("TUE")));
        OrBlock b = job.loadOrBlocks("E8").get(0);
        assertEquals(9L, b.providerId);
        assertEquals(2130, b.departmentId);
        assertEquals("TUE", b.weekday);
        assertEquals("07:30", b.startTime);
        assertEquals("15:30", b.endTime);
        assertEquals("OR-1", b.room);
    }

    @Test
    @DisplayName("BR-LEG-16: '--hr-pto-sync[=date]' runs once for that date (or latest); nightly runs the latest export")
    void brLeg16_runOnceAndNightly() {
        when(reader.resolveExportDate("20261001")).thenReturn("20261001");
        when(reader.resolveExportDate(null)).thenReturn(DATE);
        when(reader.latestExportDate()).thenReturn(DATE);
        when(reader.read(anyString())).thenReturn(List.of());

        job.run("--other", null, "--hr-pto-sync=20261001", "--hr-pto-sync");
        job.nightly();

        verify(reader).read("20261001");
        verify(reader, org.mockito.Mockito.times(2)).read(DATE);
    }

    @Test
    @DisplayName("BR-LEG-17: PTO periods - ISO ranges are inclusive, invalid ISO end means a single day, blank means none")
    void brLeg17_parsePtoDays() {
        assertEquals(3, HrPtoSyncJob.parsePtoDays("2026-12-30", "2027-01-01", 2026).size());
        assertEquals(List.of(LocalDate.of(2026, 10, 12)), HrPtoSyncJob.parsePtoDays("2026-10-12", "soon", 2026));
        assertEquals(List.of(LocalDate.of(2026, 10, 12)), HrPtoSyncJob.parsePtoDays("2026-10-12", null, 2026));
        assertTrue(HrPtoSyncJob.parsePtoDays(null, "", 2026).isEmpty());
        assertTrue(HrPtoSyncJob.parsePtoDays("  ", "", 2026).isEmpty());
        assertTrue(HrPtoSyncJob.parsePtoDays("next week", "", 2026).isEmpty());
        assertEquals(List.of(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 1)),
                HrPtoSyncJob.parsePtoDays("Out 2/28-3/1, back 3/2", "", 2028));
        assertEquals("SAT", HrPtoSyncJob.weekdayOf(LocalDate.of(2026, 10, 17)));
    }

    @Test
    @DisplayName("BR-LEG-18: SIU NTE omits a blank end, AIP carries the NPI marker only when an NPI exists")
    void brLeg18_siuOptionalSegments() {
        HrPtoRecord r = rec("E9", "CC-1", "10/12", "", "Approved");
        r.npi = "";
        OrBlock b = new OrBlock(1L, 2130, "MON", "07:00", "07:00", "OR-2");
        String msg = HrPtoSyncJob.buildSiuS15(r, 2130, b, LocalDate.of(2026, 10, 12), "CTL1");
        assertTrue(msg.contains("NTE|1||Vacation 10/12\r"));
        assertFalse(msg.contains("NPI"));
        assertTrue(msg.contains("||0|MIN|^^0^"));
        r.ptoEnd = "10/13";
        r.npi = "0000000000";
        msg = HrPtoSyncJob.buildSiuS15(r, 2130, b, LocalDate.of(2026, 10, 12), "CTL1");
        assertTrue(msg.contains("NTE|1||Vacation 10/12 - 10/13\r"));
        assertTrue(msg.contains("^^^Dr.^^^^^^^^NPI|"));
    }

    @Test
    @DisplayName("BR-LEG-21: approval is case-insensitive; blank PTO start means no PTO; display name is 'Last, First'")
    void brLeg21_recordRules() {
        HrPtoRecord r = rec("E1", "CC-1", " ", "", "APPROVED");
        assertTrue(r.isApproved());
        assertFalse(r.hasPto());
        r.ptoStart = null;
        assertFalse(r.hasPto());
        r.status = null;
        assertFalse(r.isApproved());
        assertEquals("Test, Surgeon", r.displayName());
        assertEquals("E1|Test|Surgeon|0000000000|CC-1|null||Vacation|null", r.toString());
    }
}
