package com.medchart.ehr.controller;

import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * REST controller for legacy data export and reporting.
 *
 * Provides CSV and text-based exports of encounter and patient data,
 * as well as on-demand report generation. Uses native SQL queries
 * via {@link com.medchart.ehr.legacy.EncounterExportService} and
 * {@link com.medchart.ehr.legacy.ReportGenerator}.
 */
@RestController
@RequestMapping("/v1/export")
@RequiredArgsConstructor
public class LegacyExportController {

    private final EncounterExportService encounterExportService;
    private final ReportGenerator reportGenerator;

    @GetMapping("/encounters")
    public ResponseEntity<byte[]> exportEncounters(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        byte[] data = encounterExportService.exportEncountersForDateRange(startDate, endDate);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=encounters_export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    @GetMapping("/patient/{patientId}/encounters")
    public ResponseEntity<byte[]> exportPatientEncounters(@PathVariable Long patientId) {
        byte[] data = encounterExportService.exportPatientEncounterHistory(patientId);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patient_encounters.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(data);
    }

    @GetMapping("/reports/patient-roster")
    public ResponseEntity<String> generatePatientRoster() {
        String filePath = reportGenerator.generatePatientRoster();
        return ResponseEntity.ok("Report generated at: " + filePath);
    }

    @GetMapping("/reports/encounter-summary")
    public ResponseEntity<String> generateEncounterSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        String filePath = reportGenerator.generateEncounterSummary(startDate, endDate);
        return ResponseEntity.ok("Report generated at: " + filePath);
    }

    @GetMapping("/reports/daily")
    public ResponseEntity<byte[]> getDailyReport() {
        byte[] data = reportGenerator.generateDailyReport();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=daily_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }
}
