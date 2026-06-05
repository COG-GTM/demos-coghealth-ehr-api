package com.medchart.ehr.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExportCleanupScheduler {

    private final DataExportRepository dataExportRepository;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${medchart.export.cleanup-interval-ms:3600000}")
    public void cleanupExpiredExports() {
        // Phase 1: mark expired exports as deleted in DB (committed before file I/O)
        List<DataExport> expired = transactionTemplate.execute(status -> {
            List<DataExport> list = dataExportRepository
                    .findByDeletedFalseAndExpiresAtBefore(LocalDateTime.now());
            for (DataExport export : list) {
                dataExportRepository.markDeleted(export.getId());
            }
            return list;
        });

        if (expired == null || expired.isEmpty()) {
            return;
        }

        log.info("Cleaning up {} expired export files", expired.size());

        // Phase 2: delete files (DB state is already committed, so no rollback risk)
        for (DataExport export : expired) {
            deleteExportFile(export);
        }

        log.info("Cleanup complete: {} exports removed", expired.size());
    }

    private void deleteExportFile(DataExport export) {
        if (export.getFilePath() == null) {
            return;
        }

        try {
            Path path = Paths.get(export.getFilePath());
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("Deleted export file: {}", export.getFilePath());
            }
        } catch (IOException e) {
            log.error("Failed to delete export file: {}", export.getFilePath(), e);
        }
    }
}
