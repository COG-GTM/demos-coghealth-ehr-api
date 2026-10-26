package com.medchart.ehr.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HrPtoSyncJobRerunTest {

    private static final String EXPORT_DATE = "20260930";

    @TempDir
    Path outDir;

    private HrPtoSyncJob job;

    @BeforeEach
    void setUp() {
        System.setProperty(HrPtoSyncJob.OUT_DIR_PROPERTY, outDir.toString());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), (Object) any())).thenReturn(everyWeekdayBlocks());

        job = new HrPtoSyncJob();
        ReflectionTestUtils.setField(job, "hrExportReader", new HrExportReader());
        ReflectionTestUtils.setField(job, "departmentMapping", new DepartmentMapping());
        ReflectionTestUtils.setField(job, "jdbcTemplate", jdbc);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(HrPtoSyncJob.OUT_DIR_PROPERTY);
    }

    @Test
    void secondRunForSameDateWithoutForceQueuesNothing() {
        Map<String, Object> first = job.runForDate(EXPORT_DATE);
        int queued = outboundFiles().length;
        int errorLines = job.readErrors().size();
        assertTrue(queued > 0, "first run should queue SIU^S15 messages");
        assertEquals(queued, first.get("messagesSent"));

        Map<String, Object> second = job.runForDate(EXPORT_DATE);

        assertEquals(HrPtoSyncJob.STATUS_ALREADY_RUN, second.get("status"));
        assertEquals(0, second.get("messagesSent"));
        assertEquals(queued, outboundFiles().length);
        assertEquals(errorLines, job.readErrors().size());
        assertEquals(1, job.readRuns().size());
    }

    @Test
    void runLogAloneMarksDateAsRun() {
        job.runForDate(EXPORT_DATE);
        for (File f : outboundFiles()) {
            assertTrue(f.delete());
        }

        assertEquals(HrPtoSyncJob.STATUS_ALREADY_RUN, job.runForDate(EXPORT_DATE).get("status"));
        assertEquals(0, outboundFiles().length);
    }

    @Test
    void forceResendsForSameDate() {
        job.runForDate(EXPORT_DATE);
        int queued = outboundFiles().length;

        Map<String, Object> forced = job.runForDate(EXPORT_DATE, true);

        assertFalse(HrPtoSyncJob.STATUS_ALREADY_RUN.equals(forced.get("status")));
        assertEquals(queued * 2, outboundFiles().length);
        assertEquals(2, job.readRuns().size());
    }

    @Test
    void otherDatesAreNotBlocked() {
        job.runForDate(EXPORT_DATE);

        assertFalse(job.alreadyRun("20261001"));
        assertFalse(HrPtoSyncJob.STATUS_ALREADY_RUN.equals(job.runForDate("20261001").get("status")));
    }

    private File[] outboundFiles() {
        File[] files = new File(outDir.toFile(), HrPtoSyncJob.OUTBOUND_DIR + File.separator + EXPORT_DATE)
                .listFiles((d, n) -> n.endsWith(".hl7"));
        return files == null ? new File[0] : files;
    }

    private static List<Map<String, Object>> everyWeekdayBlocks() {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (String day : new String[] {"MON", "TUE", "WED", "THU", "FRI"}) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("provider_id", 1L);
            row.put("department_id", 2130);
            row.put("weekday", day);
            row.put("start_time", "07:00:00");
            row.put("end_time", "15:00:00");
            row.put("room", "OR-4");
            rows.add(row);
        }
        return rows;
    }
}
