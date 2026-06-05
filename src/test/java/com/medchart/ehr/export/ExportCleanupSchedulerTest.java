package com.medchart.ehr.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportCleanupSchedulerTest {

    @Mock
    private DataExportRepository dataExportRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ExportCleanupScheduler scheduler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        scheduler = new ExportCleanupScheduler(dataExportRepository, transactionTemplate);
        ReflectionTestUtils.setField(scheduler, "tempDir", tempDir.toString());

        // Execute transaction callbacks inline against the mocked repository.
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().doAnswer(inv -> {
            TransactionCallbackWithoutResult cb = inv.getArgument(0);
            cb.doInTransaction(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        when(dataExportRepository.findByDeletedFalseAndExpiresAtBefore(any()))
                .thenReturn(List.of());
    }

    @Test
    void deletesOrphanFileWithNoDbRecordPastGracePeriod() throws IOException {
        Path orphan = tempDir.resolve("orphan-ref.csv.enc");
        Files.write(orphan, "x".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(orphan,
                FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        when(dataExportRepository.findByExportReference("orphan-ref"))
                .thenReturn(Optional.empty());

        scheduler.cleanupExpiredExports();

        assertFalse(Files.exists(orphan), "orphan file past grace period should be deleted");
    }

    @Test
    void keepsRecentOrphanWithinGracePeriod() throws IOException {
        Path recent = tempDir.resolve("recent-ref.csv.enc");
        Files.write(recent, "x".getBytes(StandardCharsets.UTF_8));
        when(dataExportRepository.findByExportReference("recent-ref"))
                .thenReturn(Optional.empty());

        scheduler.cleanupExpiredExports();

        assertTrue(Files.exists(recent), "freshly-written file should survive (mid-create grace)");
    }

    @Test
    void keepsFileThatHasDbRecord() throws IOException {
        Path tracked = tempDir.resolve("tracked-ref.csv.enc");
        Files.write(tracked, "x".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(tracked,
                FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        when(dataExportRepository.findByExportReference("tracked-ref"))
                .thenReturn(Optional.of(new DataExport()));

        scheduler.cleanupExpiredExports();

        assertTrue(Files.exists(tracked), "file with a DB record should never be treated as orphan");
    }
}
