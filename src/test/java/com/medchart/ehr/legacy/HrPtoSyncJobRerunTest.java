package com.medchart.ehr.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HrPtoSyncJobRerunTest {

    private static final String EXPORT_DATE = "20260930";

    @TempDir
    Path outDir;

    private HrPtoSyncJob job;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        System.setProperty(HrPtoSyncJob.OUT_DIR_PROPERTY, outDir.toString());
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), (Object) any())).thenReturn(everyWeekdayBlocks());

        job = newJob();
    }

    private HrPtoSyncJob newJob() {
        HrPtoSyncJob job = new HrPtoSyncJob();
        ReflectionTestUtils.setField(job, "hrExportReader", new HrExportReader());
        ReflectionTestUtils.setField(job, "departmentMapping", new DepartmentMapping());
        ReflectionTestUtils.setField(job, "jdbcTemplate", jdbc);
        return job;
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
    void forcedResendAfterRestartUsesNewControlIds() {
        job.runForDate(EXPORT_DATE);
        Set<String> firstIds = fileNames();

        Map<String, Object> forced = newJob().runForDate(EXPORT_DATE, true);

        Set<String> allIds = fileNames();
        assertEquals(firstIds.size() + ((Number) forced.get("messagesSent")).intValue(), allIds.size());
        assertTrue(allIds.containsAll(firstIds));
    }

    @Test
    void interruptedRunResumesWithoutRequeueingSentMessages() {
        AtomicInteger fullCalls = new AtomicInteger();
        when(jdbc.queryForList(anyString(), (Object) any())).thenAnswer(inv -> {
            fullCalls.incrementAndGet();
            return everyWeekdayBlocks();
        });
        Map<String, Object> full = newJob().runForDate(EXPORT_DATE);
        int total = ((Number) full.get("messagesSent")).intValue();
        int failAfter = fullCalls.get() / 2;
        assertTrue(failAfter > 0, "export needs at least two records with OR blocks");
        resetOutDir();

        AtomicInteger calls = new AtomicInteger();
        when(jdbc.queryForList(anyString(), (Object) any())).thenAnswer(inv -> {
            if (calls.incrementAndGet() > failAfter) {
                throw new IllegalStateException("db down");
            }
            return everyWeekdayBlocks();
        });
        assertThrows(IllegalStateException.class, () -> job.runForDate(EXPORT_DATE));
        int partial = outboundFiles().length;
        assertTrue(partial > 0 && partial < total, "partial=" + partial + " total=" + total);
        assertTrue(job.readRuns().isEmpty());
        assertFalse(job.alreadyRun(EXPORT_DATE));

        doReturn(everyWeekdayBlocks()).when(jdbc).queryForList(anyString(), (Object) any());
        Map<String, Object> retry = newJob().runForDate(EXPORT_DATE);

        assertEquals(total - partial, retry.get("messagesSent"));
        assertEquals(total, outboundFiles().length);
    }

    @Test
    void blocksDifferingOnlyByEndTimeAreBothQueued() {
        int single = ((Number) newJob().runForDate(EXPORT_DATE).get("messagesSent")).intValue();
        resetOutDir();

        List<Map<String, Object>> rows = everyWeekdayBlocks();
        for (Map<String, Object> row : everyWeekdayBlocks()) {
            row.put("end_time", "12:00:00");
            rows.add(row);
        }
        doReturn(rows).when(jdbc).queryForList(anyString(), (Object) any());

        assertEquals(single * 2, newJob().runForDate(EXPORT_DATE).get("messagesSent"));
        assertEquals(single * 2, outboundFiles().length);
    }

    @Test
    void retryWithQueuedFilesButNoLedgerQueuesNothingNew() {
        int total = ((Number) job.runForDate(EXPORT_DATE).get("messagesSent")).intValue();
        assertTrue(new File(outDir.toFile(), HrPtoSyncJob.RUNS_FILE).delete());
        FileSystemUtils.deleteRecursively(new File(outDir.toFile(), HrPtoSyncJob.SENT_DIR));

        assertEquals(0, newJob().runForDate(EXPORT_DATE).get("messagesSent"));
        assertEquals(total, outboundFiles().length);
    }

    @Test
    void otherDatesAreNotBlocked() {
        job.runForDate(EXPORT_DATE);

        assertFalse(job.alreadyRun("20261001"));
        assertFalse(HrPtoSyncJob.STATUS_ALREADY_RUN.equals(job.runForDate("20261001").get("status")));
    }

    private void resetOutDir() {
        FileSystemUtils.deleteRecursively(outDir.toFile());
        assertTrue(outDir.toFile().mkdirs());
    }

    private Set<String> fileNames() {
        Set<String> names = new HashSet<String>();
        for (File f : outboundFiles()) {
            names.add(f.getName());
        }
        return names;
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
