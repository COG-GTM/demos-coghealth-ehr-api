package com.medchart.ehr.controller;

import com.medchart.ehr.domain.order.ReviewStatus;
import com.medchart.ehr.dto.LabResultImportResponse;
import com.medchart.ehr.dto.UnmatchedLabResultDTO;
import com.medchart.ehr.service.lab.LabResultImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/lab-results")
@RequiredArgsConstructor
@Tag(name = "Lab Result Import", description = "Bulk lab result import from external reference labs")
public class LabResultImportController {

    private final LabResultImportService importService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import lab results from external reference lab",
            description = "Upload an HL7 v2.5.1 ORU^R01 message file (.hl7) or CSV file (.csv) "
                    + "containing lab results. Results are automatically matched to pending lab orders.")
    public ResponseEntity<LabResultImportResponse> importLabResults(
            @RequestParam("file") MultipartFile file) {
        LabResultImportResponse response = importService.importFile(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/import/{importId}")
    @Operation(summary = "Get import status and details")
    public ResponseEntity<LabResultImportResponse> getImportStatus(
            @PathVariable Long importId) {
        return ResponseEntity.ok(importService.getImportStatus(importId));
    }

    @GetMapping("/import/history")
    @Operation(summary = "Get import history",
            description = "Returns paginated import history with file name, timestamp, and match rate")
    public ResponseEntity<Page<LabResultImportResponse>> getImportHistory(Pageable pageable) {
        return ResponseEntity.ok(importService.getImportHistory(pageable));
    }

    @GetMapping("/import/unmatched")
    @Operation(summary = "Get unmatched lab results for manual review")
    public ResponseEntity<Page<UnmatchedLabResultDTO>> getUnmatchedResults(
            @RequestParam(required = false) ReviewStatus reviewStatus,
            Pageable pageable) {
        return ResponseEntity.ok(importService.getUnmatchedResults(reviewStatus, pageable));
    }
}
