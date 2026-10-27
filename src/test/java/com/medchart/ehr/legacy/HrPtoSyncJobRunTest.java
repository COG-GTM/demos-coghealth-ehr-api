package com.medchart.ehr.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrPtoSyncJobRunTest {

    private static final String EXPORT_DATE = "20261008";
    private static final String HEADER = "employee_id,last_name,first_name,npi,cost_center,pto_start,pto_end,pto_type,status,notes";
    private static final String[] WEEKDAYS = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

    @TempDir
    Path tmp;

    private Path exportDir;
    private Path outDir;
    private HrPtoSyncJob job;

    @BeforeEach
    void setUp() throws IOException {
        exportDir = Files.createDirectories(tmp.resolve("export"));
        outDir = tmp.resolve("out");
        System.setProperty(HrExportReader.DIR_PROPERTY, exportDir.toString());
        System.setProperty(HrPtoSyncJob.OUT_DIR_PROPERTY, outDir.toString());

        job = new HrPtoSyncJob();
        ReflectionTestUtils.setField(job, "hrExportReader", new HrExportReader());
        ReflectionTestUtils.setField(job, "departmentMapping", new DepartmentMapping());
        ReflectionTestUtils.setField(job, "jdbcTemplate", new BlockEveryDayJdbcTemplate());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(HrExportReader.DIR_PROPERTY);
        System.clearProperty(HrPtoSyncJob.OUT_DIR_PROPERTY);
        System.clearProperty(HrPtoSyncJob.MAX_MESSAGES_PROPERTY);
    }

    @Test
    void multiCenturyRowGoesToErrorQueueAndWritesNoOutbound() throws IOException {
        writeExport("E104422,Marquez,Elena,1932405817,CC-200-5410,0001-01-01,9999-12-31,Vacation,Approved,");

        Map<String, Object> result = job.runForDate(EXPORT_DATE);

        assertEquals(0, result.get("messagesSent"));
        assertEquals(1, result.get("recordsRejected"));
        assertEquals("PARTIAL", result.get("status"));
        assertEquals(0, outboundFiles().size());
        List<Map<String, Object>> errors = job.readErrors();
        assertEquals(1, errors.size());
        assertEquals("E104422", errors.get(0).get("employeeId"));
        assertEquals("AR", errors.get(0).get("code"));
        assertTrue(String.valueOf(errors.get(0).get("message")).contains("Invalid PTO period on line 2"),
                String.valueOf(errors.get(0).get("message")));
    }

    @Test
    void validRowsStillSyncAlongsideRejectedRow() throws IOException {
        writeExport(
                "E104422,Marquez,Elena,1932405817,CC-200-5410,10/13-10/17 vacation,,Vacation,Approved,",
                "E200001,Okafor,Chidi,1234567890,CC-200-5410,2026-01-01,2027-01-02,Vacation,Approved,");

        Map<String, Object> result = job.runForDate(EXPORT_DATE);

        assertEquals(5, result.get("messagesSent"));
        assertEquals(1, result.get("recordsRejected"));
        assertEquals(5, outboundFiles().size());
        assertEquals("E200001", job.readErrors().get(0).get("employeeId"));
    }

    @Test
    void rowThatWouldExceedRunCapIsRejectedWithoutPartialWrites() throws IOException {
        System.setProperty(HrPtoSyncJob.MAX_MESSAGES_PROPERTY, "7");
        writeExport(
                "E104422,Marquez,Elena,1932405817,CC-200-5410,10/13-10/17 vacation,,Vacation,Approved,",
                "E200001,Okafor,Chidi,1234567890,CC-200-5410,2026-10-19,2026-10-23,Vacation,Approved,");

        Map<String, Object> result = job.runForDate(EXPORT_DATE);

        assertEquals(5, result.get("messagesSent"));
        assertEquals(1, result.get("recordsRejected"));
        assertEquals(5, outboundFiles().size());
        Map<String, Object> error = job.readErrors().get(0);
        assertEquals("E200001", error.get("employeeId"));
        assertTrue(String.valueOf(error.get("message")).contains("Run message cap of 7 reached"),
                String.valueOf(error.get("message")));
    }

    @Test
    void errorCountsByCostCenterIgnoresPtoRangeErrors() throws IOException {
        writeExport(
                "E104422,Marquez,Elena,1932405817,CC-999-0000,10/13-10/17 vacation,,Vacation,Approved,",
                "E200001,Okafor,Chidi,1234567890,CC-200-5410,0001-01-01,9999-12-31,Vacation,Approved,");

        job.runForDate(EXPORT_DATE);

        Map<String, Integer> counts = job.errorCountsByCostCenter();
        assertEquals(1, counts.size());
        assertEquals(Integer.valueOf(1), counts.get("CC-999-0000"));
    }

    private void writeExport(String... rows) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add(HEADER);
        for (String r : rows) {
            lines.add(r);
        }
        Files.write(exportDir.resolve(HrExportReader.FILE_PREFIX + EXPORT_DATE + HrExportReader.FILE_SUFFIX),
                lines, StandardCharsets.UTF_8);
    }

    private List<File> outboundFiles() {
        List<File> out = new ArrayList<File>();
        File[] files = outDir.resolve(HrPtoSyncJob.OUTBOUND_DIR).resolve(EXPORT_DATE).toFile().listFiles();
        if (files != null) {
            for (File f : files) {
                out.add(f);
            }
        }
        return out;
    }

    private static class BlockEveryDayJdbcTemplate extends JdbcTemplate {
        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (String weekday : WEEKDAYS) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("provider_id", 1L);
                row.put("department_id", 2130);
                row.put("weekday", weekday);
                row.put("start_time", "07:00:00");
                row.put("end_time", "15:00:00");
                row.put("room", "OR-4");
                rows.add(row);
            }
            return rows;
        }
    }
}
