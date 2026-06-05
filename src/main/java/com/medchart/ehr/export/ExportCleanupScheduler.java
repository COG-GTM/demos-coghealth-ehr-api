package com.medchart.ehr.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExportCleanupScheduler {

    private final DataExportRepository dataExportRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${medchart.export.temp-dir:/tmp/medchart-exports}")
    private String tempDir;

    /**
     * Grace period before an unreferenced file is treated as a true orphan.
     * Protects files that are mid-creation (written to disk before their
     * DataExport row has committed) from being deleted prematurely.
     */
    private static final long ORPHAN_GRACE_MINUTES = 60;

    @Scheduled(fixedDelayString = "${medchart.export.cleanup-interval-ms:3600000}")
    public void cleanupExpiredExports() {
        // Phase 1: mark expired exports as deleted in DB (committed before file I/O)
        List<DataExport> expired = transactionTemplate.execute(status ->
                dataExportRepository.findByDeletedFalseAndExpiresAtBefore(LocalDateTime.now()));

        if (expired != null && !expired.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> {
                for (DataExport export : expired) {
                    dataExportRepository.markDeleted(export.getId());
                }
            });

            log.info("Cleaning up {} expired export files", expired.size());

            // Phase 2: delete files (DB state is already committed, so no rollback risk)
            for (DataExport export : expired) {
                deleteExportFile(export.getFilePath());
            }

            log.info("Cleanup complete: {} exports removed", expired.size());
        }

        // Sweep any files left on disk with no corresponding DB record (e.g. a
        // create whose transaction failed to commit after the file was written).
        cleanupOrphanedFiles();
    }

    private void cleanupOrphanedFiles() {
        Path dir = Paths.get(tempDir);
        if (!Files.isDirectory(dir)) {
            return;
        }

        Instant cutoff = Instant.now().minus(ORPHAN_GRACE_MINUTES, ChronoUnit.MINUTES);

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String reference = extractReference(file);
                if (reference == null) {
                    return;
                }
                boolean hasRecord = dataExportRepository.findByExportReference(reference).isPresent();
                if (!hasRecord && isOlderThan(file, cutoff)) {
                    log.warn("Deleting orphaned export file with no DB record: {}", file);
                    deleteExportFile(file.toString());
                }
            });
        } catch (IOException e) {
            log.error("Failed to scan export directory for orphaned files: {}", tempDir, e);
        }
    }

    private String extractReference(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".json.enc")) {
            return name.substring(0, name.length() - ".json.enc".length());
        }
        if (name.endsWith(".csv.enc")) {
            return name.substring(0, name.length() - ".csv.enc".length());
        }
        return null;
    }

    private boolean isOlderThan(Path file, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            log.error("Failed to read last-modified time for {}", file, e);
            return false;
        }
    }

    private void deleteExportFile(String filePath) {
        if (filePath == null) {
            return;
        }

        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("Deleted export file: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete export file: {}", filePath, e);
        }
    }
}
