package com.medchart.ehr.controller;

import com.medchart.ehr.interop.fhir.InterfaceContractCheck;
import com.medchart.ehr.interop.fhir.InterfaceContractReport;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoSyncJob;
import com.medchart.ehr.legacy.JobAlreadyRunningException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Integration-admin endpoints for the hr-pto-sync interface. Access is restricted to
 * {@link com.medchart.ehr.config.SecurityConfig#INTERFACE_ADMIN_ROLE} in SecurityConfig.
 */
@RestController
@RequestMapping("/interfaces")
public class InterfaceController {

    private final HrPtoSyncJob hrPtoSyncJob;
    private final HrExportReader hrExportReader;
    private final InterfaceContractCheck interfaceContractCheck;
    private final ReentrantLock contractReportLock = new ReentrantLock();

    public InterfaceController(HrPtoSyncJob hrPtoSyncJob, HrExportReader hrExportReader,
                               InterfaceContractCheck interfaceContractCheck) {
        this.hrPtoSyncJob = hrPtoSyncJob;
        this.hrExportReader = hrExportReader;
        this.interfaceContractCheck = interfaceContractCheck;
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
    public Map<String, Object> run(@RequestParam(required = false) String exportDate) {
        return hrPtoSyncJob.runForDate(resolveExistingExportDate(exportDate));
    }

    @GetMapping("/fhir/contract-report")
    public InterfaceContractReport contractReport(@RequestParam(required = false) String exportDate) {
        String date = resolveExistingExportDate(exportDate);
        if (!contractReportLock.tryLock()) {
            throw new JobAlreadyRunningException("interface contract check is already running");
        }
        try {
            return interfaceContractCheck.run(date);
        } finally {
            contractReportLock.unlock();
        }
    }

    private String resolveExistingExportDate(String exportDate) {
        String date = hrExportReader.resolveExportDate(exportDate);
        if (!hrExportReader.listExportDates().contains(date)) {
            throw new IllegalArgumentException("Unknown exportDate");
        }
        return date;
    }

    @ExceptionHandler(JobAlreadyRunningException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> alreadyRunning(JobAlreadyRunningException e) {
        return Collections.singletonMap("error", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Collections.singletonMap("error", e.getMessage());
    }
}
