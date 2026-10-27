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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HrPtoSyncJobIdempotencyTest {

    private static final String DATE = "20261008";

    @TempDir
    Path tmp;

    private Path exportDir;
    private Path outDir;

    @BeforeEach
    void setUp() throws IOException {
        exportDir = Files.createDirectories(tmp.resolve("export"));
        outDir = tmp.resolve("out");
        System.setProperty(HrExportReader.DIR_PROPERTY, exportDir.toString());
        System.setProperty(HrPtoSyncJob.OUT_DIR_PROPERTY, outDir.toString());
        writeExport("employee_id,last_name,first_name,npi,cost_center,pto_start,pto_end,pto_type,status,notes\n"
                + "E104422,Marquez,Elena,1932405817,CC-200-5410,10/13-10/23 vacation,,Vacation,Approved,\n"
                + "E200001,Doe,Jane,,CC-230-5410,10/14,,Vacation,Approved,unmapped cost center\n");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(HrExportReader.DIR_PROPERTY);
        System.clearProperty(HrPtoSyncJob.OUT_DIR_PROPERTY);
    }

    @Test
    void secondRunForSameDateQueuesNoNewMessages() throws IOException {
        HrPtoSyncJob job = job();

        Map<String, Object> first = job.runForDate(DATE);
        List<Path> filesAfterFirst = outboundFiles();
        Map<String, Object> second = job.runForDate(DATE);

        assertEquals(2, first.get("messagesSent"));
        assertEquals(0, first.get("messagesAlreadySent"));
        assertEquals(0, second.get("messagesSent"));
        assertEquals(2, second.get("messagesAlreadySent"));
        assertEquals(2, filesAfterFirst.size());
        assertEquals(filesAfterFirst, outboundFiles());
    }

    @Test
    void ledgerSurvivesRestartAndNewRowsGetFreshControlIds() throws IOException {
        job().runForDate(DATE);

        writeExport("employee_id,last_name,first_name,npi,cost_center,pto_start,pto_end,pto_type,status,notes\n"
                + "E104422,Marquez,Elena,1932405817,CC-200-5410,10/13-10/30 vacation,,Vacation,Approved,\n");
        Map<String, Object> rerun = job().runForDate(DATE);

        assertEquals(1, rerun.get("messagesSent"));
        assertEquals(2, rerun.get("messagesAlreadySent"));
        List<Path> files = outboundFiles();
        assertEquals(3, files.size());
        assertEquals("HRPTO" + DATE + "00003.hl7", files.get(2).getFileName().toString());
    }

    @Test
    void messagesQueuedBeforeLedgerExistedAreNotReplayed() throws IOException {
        job().runForDate(DATE);
        Files.delete(outDir.resolve(HrPtoSyncJob.OUTBOUND_DIR).resolve(DATE).resolve(HrPtoSyncJob.SENT_LEDGER_FILE));

        Map<String, Object> rerun = job().runForDate(DATE);

        assertEquals(0, rerun.get("messagesSent"));
        assertEquals(2, rerun.get("messagesAlreadySent"));
        assertEquals(2, outboundFiles().size());
    }

    @Test
    void correctedDepartmentMappingQueuesNewMessages() throws IOException {
        job().runForDate(DATE);

        HrPtoSyncJob remapped = job();
        ReflectionTestUtils.setField(remapped, "departmentMapping", new DepartmentMapping() {
            @Override
            public Integer lookup(String costCenter) {
                Integer dept = super.lookup(costCenter);
                return dept == null ? null : dept + 1;
            }
        });
        Map<String, Object> rerun = remapped.runForDate(DATE);

        assertEquals(2, rerun.get("messagesSent"));
        assertEquals(0, rerun.get("messagesAlreadySent"));
        assertEquals(4, outboundFiles().size());
    }

    @Test
    void errorQueueEntryIsNotDuplicatedOnRerun() {
        HrPtoSyncJob job = job();
        job.runForDate(DATE);
        job.runForDate(DATE);

        assertEquals(1, job.readErrors().size());
        assertEquals(2, job.readRuns().size());
    }

    @Test
    void concurrentRunIsRejected() throws Exception {
        CountDownLatch inRun = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HrPtoSyncJob job = new HrPtoSyncJob() {
            @Override
            public List<OrBlock> loadOrBlocks(String employeeId) {
                inRun.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Collections.emptyList();
            }
        };
        wire(job);
        Thread t = new Thread(() -> job.runForDate(DATE));
        t.start();
        inRun.await();
        try {
            assertThrows(JobAlreadyRunningException.class, () -> job.runForDate(DATE));
        } finally {
            release.countDown();
            t.join();
        }
    }

    @Test
    void rejectsNonDateExportDate() {
        HrPtoSyncJob job = job();
        assertThrows(IllegalArgumentException.class, () -> job.runForDate("20261008/../x"));
    }

    private HrPtoSyncJob job() {
        HrPtoSyncJob job = new HrPtoSyncJob() {
            @Override
            public List<OrBlock> loadOrBlocks(String employeeId) {
                return Collections.singletonList(new OrBlock(1L, 2130, "TUE", "07:00", "15:00", "OR-4"));
            }
        };
        wire(job);
        return job;
    }

    private void wire(HrPtoSyncJob job) {
        ReflectionTestUtils.setField(job, "hrExportReader", new HrExportReader());
        ReflectionTestUtils.setField(job, "departmentMapping", new DepartmentMapping());
    }

    private void writeExport(String csv) throws IOException {
        Files.write(exportDir.resolve(HrExportReader.FILE_PREFIX + DATE + HrExportReader.FILE_SUFFIX),
                csv.getBytes(StandardCharsets.UTF_8));
    }

    private List<Path> outboundFiles() throws IOException {
        Path dir = outDir.resolve(HrPtoSyncJob.OUTBOUND_DIR).resolve(DATE);
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".hl7")).sorted().collect(java.util.stream.Collectors.toList());
        }
    }
}
