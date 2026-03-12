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
        reportGenerator.generatePatientRoster();
        return ResponseEntity.ok("Patient roster report generated successfully");
    }

    @GetMapping("/reports/encounter-summary")
    public ResponseEntity<String> generateEncounterSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        reportGenerator.generateEncounterSummary(startDate, endDate);
        return ResponseEntity.ok("Encounter summary report generated successfully");
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
