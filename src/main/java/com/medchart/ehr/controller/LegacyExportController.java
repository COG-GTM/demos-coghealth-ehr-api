package com.medchart.ehr.controller;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ExportLimits;
import com.medchart.ehr.legacy.ReportGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1/export")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHI_EXPORT')")
public class LegacyExportController {

    private final EncounterExportService encounterExportService;
    private final ReportGenerator reportGenerator;

    @GetMapping("/encounters")
    @PreAuthorize("hasRole('PHI_EXPORT')")
    @AuditAccess(action = AuditAction.EXPORT, resourceType = "Encounter", description = "Export encounters for date range")
    public ResponseEntity<byte[]> exportEncounters(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam int page,
            @RequestParam int size) {

        validatePagination(page, size);
        validateDateRange(startDate, endDate);

        byte[] data = encounterExportService.exportEncountersForDateRange(startDate, endDate, page, size);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=encounters_export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    @GetMapping("/patient/{patientId}/encounters")
    @PreAuthorize("hasRole('PHI_EXPORT')")
    @AuditAccess(action = AuditAction.EXPORT, resourceType = "Encounter", description = "Export patient encounter history")
    public ResponseEntity<byte[]> exportPatientEncounters(
            @PathVariable Long patientId,
            @RequestParam int page,
            @RequestParam int size) {

        validatePagination(page, size);

        byte[] data = encounterExportService.exportPatientEncounterHistory(patientId, page, size);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patient_encounters.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(data);
    }

    @GetMapping("/reports/patient-roster")
    @PreAuthorize("hasRole('PHI_EXPORT')")
    @AuditAccess(action = AuditAction.EXPORT, resourceType = "Patient", description = "Generate patient roster report")
    public ResponseEntity<String> generatePatientRoster(
            @RequestParam int page,
            @RequestParam int size) {

        validatePagination(page, size);

        String filePath = reportGenerator.generatePatientRoster(page, size);
        return ResponseEntity.ok("Report generated at: " + filePath);
    }

    @GetMapping("/reports/encounter-summary")
    @PreAuthorize("hasRole('PHI_EXPORT')")
    @AuditAccess(action = AuditAction.EXPORT, resourceType = "Encounter", description = "Generate encounter summary report")
    public ResponseEntity<String> generateEncounterSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam int page,
            @RequestParam int size) {

        validatePagination(page, size);
        validateDateRange(startDate, endDate);

        String filePath = reportGenerator.generateEncounterSummary(startDate, endDate, page, size);
        return ResponseEntity.ok("Report generated at: " + filePath);
    }

    @GetMapping("/reports/daily")
    @PreAuthorize("hasRole('PHI_EXPORT')")
    @AuditAccess(action = AuditAction.EXPORT, resourceType = "Patient", description = "Download daily patient report")
    public ResponseEntity<byte[]> getDailyReport(
            @RequestParam int page,
            @RequestParam int size) {

        validatePagination(page, size);

        byte[] data = reportGenerator.generateDailyReport(page, size);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=daily_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    private void validatePagination(int page, int size) {
        try {
            ExportLimits.validatePagination(page, size);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        try {
            ExportLimits.validateDateRange(startDate, endDate);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            ExportLimits.validateDateRange(startDate, endDate);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
