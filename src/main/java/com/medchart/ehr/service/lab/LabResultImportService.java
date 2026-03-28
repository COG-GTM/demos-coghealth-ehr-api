package com.medchart.ehr.service.lab;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.order.*;
import com.medchart.ehr.dto.LabResultImportResponse;
import com.medchart.ehr.dto.ParsedLabResult;
import com.medchart.ehr.repository.*;
import com.medchart.ehr.service.ProviderNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class LabResultImportService {

    private static final List<OrderStatus> MATCHABLE_STATUSES = List.of(
            OrderStatus.PENDING, OrderStatus.IN_PROGRESS);

    private final HL7ResultParser hl7Parser;
    private final CsvResultParser csvParser;
    private final LabOrderRepository labOrderRepository;
    private final LabResultRepository labResultRepository;
    private final LabResultImportRepository importRepository;
    private final UnmatchedLabResultRepository unmatchedRepository;
    private final PatientRepository patientRepository;
    private final ProviderNotificationService notificationService;

    @AuditAccess(action = AuditAction.CREATE, resourceType = "LabResultImport",
            description = "Import lab results from external reference lab")
    @Transactional
    public LabResultImportResponse importFile(MultipartFile file) {
        validateFile(file);

        String fileType = detectFileType(file.getOriginalFilename());
        LabResultImport importRecord = createImportRecord(file, fileType);

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<ParsedLabResult> parsedResults = parseFile(content, fileType);

            importRecord.setTotalResults(parsedResults.size());

            if (parsedResults.isEmpty()) {
                importRecord.setStatus(ImportStatus.COMPLETED);
                importRecord.setCompletedAt(LocalDateTime.now());
                importRepository.save(importRecord);
                return toResponse(importRecord);
            }

            processResults(parsedResults, importRecord);

            importRecord.setCompletedAt(LocalDateTime.now());
            if (importRecord.getErrorCount() > 0) {
                importRecord.setStatus(ImportStatus.COMPLETED_WITH_ERRORS);
            } else {
                importRecord.setStatus(ImportStatus.COMPLETED);
            }
            importRepository.save(importRecord);

            log.info("Lab result import completed: file={}, total={}, matched={}, unmatched={}, errors={}",
                    file.getOriginalFilename(), importRecord.getTotalResults(),
                    importRecord.getMatchedCount(), importRecord.getUnmatchedCount(),
                    importRecord.getErrorCount());

            return toResponse(importRecord);

        } catch (IOException e) {
            importRecord.setStatus(ImportStatus.FAILED);
            importRecord.setErrorMessage("Failed to read file: " + e.getMessage());
            importRecord.setCompletedAt(LocalDateTime.now());
            importRepository.save(importRecord);
            throw new IllegalArgumentException("Failed to read uploaded file", e);
        } catch (IllegalArgumentException e) {
            importRecord.setStatus(ImportStatus.FAILED);
            importRecord.setErrorMessage(e.getMessage());
            importRecord.setCompletedAt(LocalDateTime.now());
            importRepository.save(importRecord);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public LabResultImportResponse getImportStatus(Long importId) {
        LabResultImport importRecord = importRepository.findById(importId)
                .orElseThrow(() -> new EntityNotFoundException("Import not found with id: " + importId));
        return toResponse(importRecord);
    }

    @Transactional(readOnly = true)
    public Page<LabResultImportResponse> getImportHistory(Pageable pageable) {
        return importRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<UnmatchedLabResult> getUnmatchedResults(ReviewStatus reviewStatus, Pageable pageable) {
        if (reviewStatus != null) {
            return unmatchedRepository.findByReviewStatus(reviewStatus, pageable);
        }
        return unmatchedRepository.findAll(pageable);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required and must not be empty");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("File must have a filename");
        }
        String ext = filename.toLowerCase();
        if (!ext.endsWith(".hl7") && !ext.endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Only .hl7 and .csv files are accepted");
        }
        // Max 10MB
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds maximum of 10MB");
        }
    }

    private String detectFileType(String filename) {
        if (filename != null && filename.toLowerCase().endsWith(".hl7")) {
            return "HL7";
        }
        return "CSV";
    }

    private LabResultImport createImportRecord(MultipartFile file, String fileType) {
        LabResultImport importRecord = LabResultImport.builder()
                .fileName(file.getOriginalFilename())
                .fileType(fileType)
                .fileSizeBytes(file.getSize())
                .status(ImportStatus.PROCESSING)
                .startedAt(LocalDateTime.now())
                .build();
        return importRepository.save(importRecord);
    }

    private List<ParsedLabResult> parseFile(String content, String fileType) {
        if ("HL7".equals(fileType)) {
            return hl7Parser.parse(content);
        } else {
            csvParser.validate(content);
            return csvParser.parse(content);
        }
    }

    private void processResults(List<ParsedLabResult> parsedResults, LabResultImport importRecord) {
        Set<Long> notifiedProviderIds = new HashSet<>();

        for (ParsedLabResult parsed : parsedResults) {
            try {
                Optional<LabOrder> matchedOrder = matchToOrder(parsed);

                if (matchedOrder.isPresent()) {
                    LabOrder order = matchedOrder.get();
                    createLabResult(parsed, order);
                    updateOrderStatus(order);
                    importRecord.setMatchedCount(importRecord.getMatchedCount() + 1);

                    // Notify provider if not already notified for this import
                    Long providerId = order.getOrderingProvider().getId();
                    if (!notifiedProviderIds.contains(providerId)) {
                        notifyProvider(order);
                        notifiedProviderIds.add(providerId);
                    }
                } else {
                    createUnmatchedResult(parsed, importRecord);
                    importRecord.setUnmatchedCount(importRecord.getUnmatchedCount() + 1);
                }
            } catch (Exception e) {
                log.error("Error processing result for test code {}: {}",
                        parsed.getTestCode(), e.getMessage());
                importRecord.setErrorCount(importRecord.getErrorCount() + 1);
            }
        }
    }

    private Optional<LabOrder> matchToOrder(ParsedLabResult parsed) {
        // Strategy 1: Match by order number
        if (parsed.getOrderNumber() != null && !parsed.getOrderNumber().isEmpty()) {
            Optional<LabOrder> byOrderNumber = labOrderRepository.findByOrderNumber(parsed.getOrderNumber());
            if (byOrderNumber.isPresent()) {
                return byOrderNumber;
            }
        }

        // Strategy 2: Match by patient MRN + test code
        if (parsed.getPatientIdentifier() != null && parsed.getTestCode() != null) {
            List<LabOrder> matches = labOrderRepository
                    .findByPatientMrnAndTestCodeAndStatusIn(
                            parsed.getPatientIdentifier(),
                            parsed.getTestCode(),
                            MATCHABLE_STATUSES);
            if (!matches.isEmpty()) {
                // Return the most recent pending order
                return Optional.of(matches.get(0));
            }
        }

        return Optional.empty();
    }

    private void createLabResult(ParsedLabResult parsed, LabOrder order) {
        ResultStatus status = mapResultStatus(parsed.getResultStatus());
        ResultFlag flag = mapResultFlag(parsed.getFlag());

        LabResult labResult = LabResult.builder()
                .labOrder(order)
                .resultCode(parsed.getTestCode())
                .resultName(parsed.getTestName() != null ? parsed.getTestName() : parsed.getTestCode())
                .value(parsed.getValue())
                .numericValue(parsed.getNumericValue())
                .unit(parsed.getUnit())
                .referenceRange(parsed.getReferenceRange())
                .flag(flag)
                .status(status)
                .resultDateTime(parsed.getResultDateTime() != null
                        ? parsed.getResultDateTime() : LocalDateTime.now())
                .performingLab(parsed.getPerformingLab())
                .build();

        labResultRepository.save(labResult);
    }

    private void updateOrderStatus(LabOrder order) {
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.IN_PROGRESS);
            labOrderRepository.save(order);
        }
    }

    private void createUnmatchedResult(ParsedLabResult parsed, LabResultImport importRecord) {
        UnmatchedLabResult unmatched = UnmatchedLabResult.builder()
                .labResultImport(importRecord)
                .patientIdentifier(parsed.getPatientIdentifier())
                .patientFirstName(parsed.getPatientFirstName())
                .patientLastName(parsed.getPatientLastName())
                .orderNumber(parsed.getOrderNumber())
                .testCode(parsed.getTestCode())
                .testName(parsed.getTestName())
                .value(parsed.getValue())
                .numericValue(parsed.getNumericValue())
                .unit(parsed.getUnit())
                .referenceRange(parsed.getReferenceRange())
                .flag(mapResultFlag(parsed.getFlag()))
                .resultStatus(mapResultStatus(parsed.getResultStatus()))
                .resultDateTime(parsed.getResultDateTime())
                .performingLab(parsed.getPerformingLab())
                .reviewStatus(ReviewStatus.PENDING)
                .build();

        unmatchedRepository.save(unmatched);
    }

    @Async
    void notifyProvider(LabOrder order) {
        try {
            String patientName = order.getPatient().getLastName() + ", "
                    + order.getPatient().getFirstName();
            String subject = "New Lab Results Available";
            String message = String.format(
                    "Lab results received for patient %s (MRN: %s) - %s (%s)",
                    patientName,
                    order.getPatient().getMrn(),
                    order.getTestName(),
                    order.getTestCode());

            notificationService.notifyProvider(
                    order.getOrderingProvider().getId(),
                    order.getOrderingProvider().getEmail(),
                    null,
                    ProviderNotificationService.NotificationType.IN_APP,
                    subject,
                    message,
                    Map.of("orderId", order.getId(), "orderNumber", order.getOrderNumber()));
        } catch (Exception e) {
            log.error("Failed to notify provider for order {}", order.getOrderNumber(), e);
        }
    }

    private ResultStatus mapResultStatus(String status) {
        if (status == null) return ResultStatus.FINAL;
        try {
            return ResultStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResultStatus.FINAL;
        }
    }

    private ResultFlag mapResultFlag(String flag) {
        if (flag == null || flag.isEmpty()) return ResultFlag.NORMAL;
        try {
            return ResultFlag.valueOf(flag.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try HL7 flag mapping
            switch (flag.toUpperCase()) {
                case "N": return ResultFlag.NORMAL;
                case "L": return ResultFlag.LOW;
                case "H": return ResultFlag.HIGH;
                case "LL": return ResultFlag.CRITICAL_LOW;
                case "HH": return ResultFlag.CRITICAL_HIGH;
                case "A": return ResultFlag.ABNORMAL;
                case "POS": return ResultFlag.POSITIVE;
                case "NEG": return ResultFlag.NEGATIVE;
                default: return ResultFlag.NORMAL;
            }
        }
    }

    private LabResultImportResponse toResponse(LabResultImport record) {
        return LabResultImportResponse.builder()
                .importId(record.getId())
                .fileName(record.getFileName())
                .fileType(record.getFileType())
                .status(record.getStatus())
                .totalResults(record.getTotalResults())
                .matchedCount(record.getMatchedCount())
                .unmatchedCount(record.getUnmatchedCount())
                .errorCount(record.getErrorCount())
                .errorMessage(record.getErrorMessage())
                .matchRate(record.getMatchRate())
                .startedAt(record.getStartedAt())
                .completedAt(record.getCompletedAt())
                .build();
    }
}
