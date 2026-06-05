package com.medchart.ehr.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BatchPatientExportService {

    private final PatientRepository patientRepository;
    private final DeIdentificationService deIdentificationService;
    private final ExportEncryptionService encryptionService;
    private final DataExportRepository dataExportRepository;
    private final PatientAccessLogger patientAccessLogger;

    private static final String EXPORT_RESOURCE_TYPE = "PatientBatchExport";

    @Value("${medchart.export.temp-dir:/tmp/medchart-exports}")
    private String tempDir;

    @Value("${medchart.export.ttl-hours:24}")
    private int ttlHours;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Transactional
    public DataExportDTO createExport(BatchExportRequest request, String userId,
                                      String userName, String userRole, String ipAddress) {
        List<Patient> patients = patientRepository.findAllById(request.getPatientIds());

        if (patients.isEmpty()) {
            throw new ExportException("No patients found for the given IDs");
        }

        boolean isResearch = request.getReason() == ExportReason.RESEARCH;
        byte[] content;

        if (isResearch) {
            List<Map<String, Object>> deIdentified = deIdentificationService.deIdentify(patients);
            content = formatData(deIdentified, request.getFormat());
        } else {
            List<Map<String, Object>> patientData = patients.stream()
                    .map(this::toExportMap)
                    .collect(Collectors.toList());
            content = formatData(patientData, request.getFormat());
        }

        byte[] encrypted = encryptionService.encrypt(content);

        String reference = UUID.randomUUID().toString();
        Path filePath = computeExportPath(reference, request.getFormat());

        LocalDateTime now = LocalDateTime.now();
        DataExport export = DataExport.builder()
                .exportReference(reference)
                .userId(userId)
                .userName(userName)
                .reason(request.getReason())
                .reasonDetails(request.getReasonDetails())
                .format(request.getFormat())
                .patientCount(patients.size())
                .deIdentified(isResearch)
                .filePath(filePath.toString())
                .fileSizeBytes((long) encrypted.length)
                .downloadCount(0)
                .expiresAt(now.plusHours(ttlHours))
                .deleted(false)
                .ipAddress(ipAddress)
                .build();

        // Save DB record before file I/O so a constraint violation won't leave
        // an orphaned encrypted file on disk with no corresponding DB record.
        dataExportRepository.save(export);
        writeEncryptedFile(filePath, encrypted);

        // Audit success only after transaction commits so the HIPAA trail
        // never contains a record for an export that was rolled back.
        int patientCount = patients.size();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                patientAccessLogger.logExport(
                        userId, userRole, EXPORT_RESOURCE_TYPE, patientCount,
                        String.format("Batch export created: reason=%s, format=%s, deIdentified=%s",
                                request.getReason(), request.getFormat(), isResearch),
                        ipAddress, "exportReference=" + reference, true);
            }
        });

        return DataExportDTO.fromEntity(export, contextPath);
    }

    @Transactional
    public ExportDownload downloadExport(String exportReference, String userId,
                                         String userRole, boolean isAdmin, String ipAddress) {
        DataExport export = dataExportRepository.findByExportReference(exportReference)
                .orElseThrow(() -> new ExportException("Export not found: " + exportReference, HttpStatus.NOT_FOUND));

        // PHI authorization: only the export's owner or an admin may download it.
        if (!isAdmin && !export.getUserId().equals(userId)) {
            patientAccessLogger.logExport(
                    userId,
                    userRole,
                    EXPORT_RESOURCE_TYPE,
                    export.getPatientCount(),
                    "DENIED download of export owned by another user",
                    ipAddress,
                    "exportReference=" + exportReference,
                    false);
            throw new ExportAccessDeniedException("Not authorized to download this export");
        }

        if (export.getDeleted()) {
            throw new ExportException("Export has been deleted (expired)", HttpStatus.GONE);
        }

        if (export.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExportException("Export has expired", HttpStatus.GONE);
        }

        try {
            byte[] encrypted = Files.readAllBytes(Paths.get(export.getFilePath()));
            byte[] decrypted = encryptionService.decrypt(encrypted);
            export.incrementDownloadCount();
            dataExportRepository.save(export);

            int downloadNum = export.getDownloadCount();
            int recordCount = export.getPatientCount();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    patientAccessLogger.logExport(
                            userId, userRole, EXPORT_RESOURCE_TYPE, recordCount,
                            "Export downloaded (download #" + downloadNum + ")",
                            ipAddress, "exportReference=" + exportReference, true);
                }
            });

            return new ExportDownload(decrypted, export.getFormat(), exportReference);
        } catch (IOException e) {
            throw new ExportException("Failed to read export file", e);
        }
    }

    public Page<DataExportDTO> getExportHistory(String userId, boolean isAdmin, Pageable pageable) {
        Page<DataExport> exports = isAdmin
                ? dataExportRepository.findAllByOrderByCreatedAtDesc(pageable)
                : dataExportRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return exports.map(e -> DataExportDTO.fromEntity(e, contextPath));
    }

    private Map<String, Object> toExportMap(Patient patient) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mrn", patient.getMrn());
        map.put("firstName", patient.getFirstName());
        map.put("lastName", patient.getLastName());
        map.put("dateOfBirth", patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : null);
        map.put("gender", patient.getGender() != null ? patient.getGender().name() : null);
        map.put("email", patient.getEmail());
        map.put("phoneHome", patient.getPhoneHome());
        map.put("phoneMobile", patient.getPhoneMobile());
        // Always emit address keys so CSV headers (derived from the first row) stay
        // consistent regardless of whether the first patient has an address.
        map.put("street", patient.getAddress() != null ? patient.getAddress().getStreet1() : null);
        map.put("city", patient.getAddress() != null ? patient.getAddress().getCity() : null);
        map.put("state", patient.getAddress() != null ? patient.getAddress().getState() : null);
        map.put("zipCode", patient.getAddress() != null ? patient.getAddress().getZipCode() : null);
        map.put("active", patient.getActive());
        return map;
    }

    private byte[] formatData(List<Map<String, Object>> data, ExportFormat format) {
        if (format == ExportFormat.JSON) {
            return formatAsJson(data);
        }
        return formatAsCsv(data);
    }

    private byte[] formatAsJson(List<Map<String, Object>> data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (Exception e) {
            throw new ExportException("Failed to generate JSON export", e);
        }
    }

    private byte[] formatAsCsv(List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return new byte[0];
        }

        StringBuilder csv = new StringBuilder();
        Set<String> headers = data.get(0).keySet();
        csv.append(String.join(",", headers)).append("\n");

        for (Map<String, Object> row : data) {
            csv.append(headers.stream()
                    .map(h -> escapeCsv(row.get(h)))
                    .collect(Collectors.joining(","))
            ).append("\n");
        }

        return csv.toString().getBytes();
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    private Path computeExportPath(String reference, ExportFormat format) {
        String extension = format == ExportFormat.JSON ? ".json.enc" : ".csv.enc";
        return Paths.get(tempDir, reference + extension);
    }

    private void writeEncryptedFile(Path filePath, byte[] data) {
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, data);
        } catch (IOException e) {
            throw new ExportException("Failed to write export file", e);
        }
    }
}
