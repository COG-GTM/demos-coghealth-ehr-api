package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrPtoSyncJobTest {

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

    private static HrPtoRecord marquez() {
        HrPtoRecord r = new HrPtoRecord();
        r.employeeId = "E104422";
        r.lastName = "Marquez";
        r.firstName = "Elena";
        r.npi = "1932405817";
        r.ptoStart = "10/13-10/17 vacation";
        r.ptoEnd = "";
        r.ptoType = "Vacation";
        return r;
    }

    private static String[] build(HrPtoRecord r) {
        OrBlock block = new OrBlock(1L, 2130, "TUE", "07:00", "15:00", "OR-4");
        return HrPtoSyncJob.buildSiuS15(r, 2130, block, LocalDate.of(2026, 10, 13), "HRPTO2026100800001").split("\r", -1);
    }

    private static int fields(String segment) {
        return segment.split("\\|", -1).length;
    }

    private static int components(String field) {
        return field.split("\\^", -1).length;
    }

    @Test
    void escapesHl7DelimitersAndControlCharacters() {
        assertEquals("Smith\\F\\X\\S\\Y", HrPtoSyncJob.hl7Escape("Smith|X^Y"));
        assertEquals("a\\R\\b\\T\\c\\E\\d", HrPtoSyncJob.hl7Escape("a~b&c\\d"));
        assertEquals("line one line two", HrPtoSyncJob.hl7Escape("line one\rline two"));
        assertEquals("", HrPtoSyncJob.hl7Escape(null));
        assertEquals("Marquez", HrPtoSyncJob.hl7Escape("Marquez"));
    }

    @Test
    void delimiterInLastNameDoesNotShiftSiuFields() {
        String[] baseline = build(marquez());
        HrPtoRecord r = marquez();
        r.lastName = "Smith|X^Y";
        String[] segments = build(r);

        assertEquals(baseline.length, segments.length);
        assertEquals(fields(baseline[1]), fields(segments[1]));
        assertEquals(fields(baseline[5]), fields(segments[5]));
        String aip3 = segments[5].split("\\|", -1)[3];
        assertEquals(components(baseline[5].split("\\|", -1)[3]), components(aip3));
        assertTrue(aip3.startsWith("E104422^Smith\\F\\X\\S\\Y^Elena^^^Dr."), aip3);
        assertTrue(segments[5].endsWith("|SURGEON^Primary surgeon|202610130700||480|MIN"), segments[5]);
        assertTrue(segments[1].contains("|E104422^Smith\\F\\X\\S\\Y^Elena||||||||Cancelled"), segments[1]);
    }

    @Test
    void delimitersInPtoTextStayInsideNte() {
        HrPtoRecord r = marquez();
        r.firstName = "El~ena&";
        r.employeeId = "E1|04422";
        r.ptoType = "Vacation|SURGEON^X~Y\\";
        r.ptoStart = "10/13^10/17";
        r.ptoEnd = "10/17|9";
        String[] baseline = build(marquez());
        String[] segments = build(r);

        assertEquals(baseline.length, segments.length);
        for (int i = 0; i < baseline.length; i++) {
            assertEquals(fields(baseline[i]), fields(segments[i]), "segment " + i);
        }
        assertEquals("NTE|1||Vacation\\F\\SURGEON\\S\\X\\R\\Y\\E\\ 10/13\\S\\10/17 - 10/17\\F\\9", segments[2]);
        assertTrue(segments[5].startsWith("AIP|1||E1\\F\\04422^Marquez^El\\R\\ena\\T\\^^^Dr."), segments[5]);
    }

    @Test
    void identityRejectionFlagsDelimitersInIdsAndNames() {
        assertNull(HrPtoSyncJob.identityRejection(marquez()));
        HrPtoRecord apostrophe = marquez();
        apostrophe.lastName = "O'Neil-Smith";
        assertNull(HrPtoSyncJob.identityRejection(apostrophe));

        HrPtoRecord badName = marquez();
        badName.lastName = "Smith|X^Y";
        assertNotNull(HrPtoSyncJob.identityRejection(badName));
        HrPtoRecord badFirst = marquez();
        badFirst.firstName = "Elena\u0007";
        assertNotNull(HrPtoSyncJob.identityRejection(badFirst));
        HrPtoRecord badId = marquez();
        badId.employeeId = "E104422^X";
        assertNotNull(HrPtoSyncJob.identityRejection(badId));
        HrPtoRecord blankId = marquez();
        blankId.employeeId = "";
        assertNotNull(HrPtoSyncJob.identityRejection(blankId));
    }

    @Test
    void runRejectsRecordsWithDelimiterInIdentityAndKeepsOutboundClean(@TempDir Path tmp) throws IOException {
        Path exportDir = Files.createDirectories(tmp.resolve("export"));
        Path outDir = tmp.resolve("out");
        Files.write(exportDir.resolve("workday_pto_export_20261013.csv"), (
                "employee_id,last_name,first_name,npi,cost_center,pto_start,pto_end,pto_type,status,notes\n"
                + "E104422,Marquez,Elena,1932405817,CC-200-5410,2026-10-13,2026-10-13,Vacation|X^Y,Approved,\n"
                + "E200001,\"Smith|X^Y\",Mallory,1234567893,CC-200-5410,2026-10-13,2026-10-13,Vacation,Approved,\n"
                + "E2|00002,Jones,Pat,1234567893,CC-200-5410,2026-10-13,2026-10-13,Vacation,Approved,\n")
                .getBytes(StandardCharsets.UTF_8));

        HrPtoSyncJob job = new HrPtoSyncJob();
        ReflectionTestUtils.setField(job, "hrExportReader", new HrExportReader());
        ReflectionTestUtils.setField(job, "departmentMapping", new DepartmentMapping());
        ReflectionTestUtils.setField(job, "jdbcTemplate", new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                Map<String, Object> row = new HashMap<String, Object>();
                row.put("provider_id", 1L);
                row.put("department_id", 2130);
                row.put("weekday", "TUE");
                row.put("start_time", "07:00:00");
                row.put("end_time", "15:00:00");
                row.put("room", "OR-4");
                return new ArrayList<Map<String, Object>>(Collections.singletonList(row));
            }
        });

        System.setProperty(HrExportReader.DIR_PROPERTY, exportDir.toString());
        System.setProperty(HrPtoSyncJob.OUT_DIR_PROPERTY, outDir.toString());
        try {
            Map<String, Object> result = job.runForDate("20261013");
            assertEquals(1, result.get("messagesSent"));
            assertEquals(2, result.get("recordsRejected"));
            assertEquals("PARTIAL", result.get("status"));

            File[] outbound = outDir.resolve("outbound").resolve("20261013").toFile().listFiles();
            assertNotNull(outbound);
            assertEquals(1, outbound.length);
            String msg = new String(Files.readAllBytes(outbound[0].toPath()), StandardCharsets.UTF_8);
            assertFalse(msg.contains("Smith"));
            assertFalse(msg.contains("Jones"));
            String nte = msg.split("\r")[2];
            assertEquals("NTE|1||Vacation\\F\\X\\S\\Y 2026-10-13 - 2026-10-13", nte);

            List<Map<String, Object>> errors = job.readErrors();
            assertEquals(2, errors.size());
            assertEquals("E200001", errors.get(0).get("employeeId"));
            assertEquals("AR", errors.get(0).get("code"));
            assertEquals("E2\\F\\00002", errors.get(1).get("employeeId"));
            assertEquals("AR", errors.get(1).get("code"));
            assertTrue(String.valueOf(errors.get(1).get("message")).startsWith("AR – Invalid employee id"));
        } finally {
            System.clearProperty(HrExportReader.DIR_PROPERTY);
            System.clearProperty(HrPtoSyncJob.OUT_DIR_PROPERTY);
        }
    }
}
