package com.medchart.ehr.interop.fhir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InterfaceContractReport {

    private String job;
    private String exportDate;
    private String exportFile;
    private String generatedAt;
    private int records;
    private int resources;
    private Map<String, List<String>> unknownCostCenters = new LinkedHashMap<>();
    private Map<String, List<String>> duplicatePractitioners = new LinkedHashMap<>();
    private List<ContractFinding> findings = new ArrayList<>();

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public String getExportDate() {
        return exportDate;
    }

    public void setExportDate(String exportDate) {
        this.exportDate = exportDate;
    }

    public String getExportFile() {
        return exportFile;
    }

    public void setExportFile(String exportFile) {
        this.exportFile = exportFile;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public int getRecords() {
        return records;
    }

    public void setRecords(int records) {
        this.records = records;
    }

    public int getResources() {
        return resources;
    }

    public void setResources(int resources) {
        this.resources = resources;
    }

    public Map<String, List<String>> getUnknownCostCenters() {
        return unknownCostCenters;
    }

    public Map<String, List<String>> getDuplicatePractitioners() {
        return duplicatePractitioners;
    }

    public List<ContractFinding> getFindings() {
        return findings;
    }

    public List<ContractFinding> getErrors() {
        List<ContractFinding> out = new ArrayList<>();
        for (ContractFinding f : findings) {
            if (f.isError()) {
                out.add(f);
            }
        }
        return out;
    }

    public int getErrorCount() {
        return getErrors().size();
    }

    public int getWarningCount() {
        int n = 0;
        for (ContractFinding f : findings) {
            if ("WARNING".equals(f.getSeverity())) {
                n++;
            }
        }
        return n;
    }

    public boolean isPassed() {
        return unknownCostCenters.isEmpty() && duplicatePractitioners.isEmpty() && getErrorCount() == 0;
    }

    public String getStatus() {
        return isPassed() ? "PASS" : "FAIL";
    }
}
