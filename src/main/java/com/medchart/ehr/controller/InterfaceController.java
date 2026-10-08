package com.medchart.ehr.controller;

import com.medchart.ehr.interop.fhir.InterfaceContractCheck;
import com.medchart.ehr.interop.fhir.InterfaceContractReport;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoSyncJob;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/interfaces")
public class InterfaceController {

    private final HrPtoSyncJob hrPtoSyncJob;
    private final HrExportReader hrExportReader;
    private final InterfaceContractCheck interfaceContractCheck;

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
        return hrPtoSyncJob.runForDate(hrExportReader.resolveExportDate(exportDate));
    }

    @GetMapping("/fhir/contract-report")
    public InterfaceContractReport contractReport(@RequestParam(required = false) String exportDate) {
        return interfaceContractCheck.run(hrExportReader.resolveExportDate(exportDate));
    }
}
