package com.medchart.ehr.controller;

import com.medchart.ehr.interop.fhir.InterfaceContractCheck;
import com.medchart.ehr.interop.fhir.InterfaceContractReport;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoSyncJob;
import com.medchart.ehr.legacy.OnDemandRunThrottle;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/interfaces")
@PreAuthorize("hasAnyRole('INTERFACE_ADMIN', 'ADMIN')")
public class InterfaceController {

    static final String RUN_OPERATION = "hr-pto-sync/run";
    static final String CONTRACT_REPORT_OPERATION = "fhir/contract-report";

    private final HrPtoSyncJob hrPtoSyncJob;
    private final HrExportReader hrExportReader;
    private final InterfaceContractCheck interfaceContractCheck;
    private final OnDemandRunThrottle throttle;

    public InterfaceController(HrPtoSyncJob hrPtoSyncJob, HrExportReader hrExportReader,
                               InterfaceContractCheck interfaceContractCheck, OnDemandRunThrottle throttle) {
        this.hrPtoSyncJob = hrPtoSyncJob;
        this.hrExportReader = hrExportReader;
        this.interfaceContractCheck = interfaceContractCheck;
        this.throttle = throttle;
    }

    @GetMapping("/hr-pto-sync/errors")
    public List<Map<String, Object>> errors() {
        return hrPtoSyncJob.readErrors();
    }

    @GetMapping("/hr-pto-sync/runs")
    public List<Map<String, Object>> runs() {
        return hrPtoSyncJob.readRuns();
    }

    @GetMapping("/hr-pto-sync/exports")
    public Map<String, Object> exports() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("latest", hrExportReader.latestExportDate());
        out.put("dates", hrExportReader.listExportDates());
        return out;
    }

    @PostMapping("/hr-pto-sync/run")
    public ResponseEntity<?> run(@RequestParam(required = false) String exportDate,
                                 @RequestParam(defaultValue = "false") boolean force) {
        String date = hrExportReader.resolveRequestedExportDate(exportDate);
        long retryAfter = throttle.tryAcquire(RUN_OPERATION);
        if (retryAfter > 0) {
            return tooManyRequests(retryAfter);
        }
        Map<String, Object> result = hrPtoSyncJob.runForDate(date, force);
        HttpStatus status = HrPtoSyncJob.STATUS_ALREADY_RUN.equals(result.get("status"))
                ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/fhir/contract-report")
    public ResponseEntity<?> contractReport(@RequestParam(required = false) String exportDate) {
        String date = hrExportReader.resolveRequestedExportDate(exportDate);
        long retryAfter = throttle.tryAcquire(CONTRACT_REPORT_OPERATION);
        if (retryAfter > 0) {
            return tooManyRequests(retryAfter);
        }
        InterfaceContractReport report = interfaceContractCheck.run(date);
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Collections.<String, Object>singletonMap("error", e.getMessage()));
    }

    private static ResponseEntity<Map<String, Object>> tooManyRequests(long retryAfterSeconds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "On-demand run limit reached");
        body.put("retryAfterSeconds", retryAfterSeconds);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(body);
    }
}
