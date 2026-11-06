package com.medchart.ehr.interop.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.medchart.ehr.interop.fhir.PractitionerDeduplicator.PractitionerGroup;
import com.medchart.ehr.legacy.DepartmentMapping;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the hr-pto-sync interface contract: cost center mapping, practitioner identity and FHIR R4
 * conformance of the data the job emits. Writes target/interface-contract/report.{json,md}.
 */
@Service
@Slf4j
public class InterfaceContractCheck {

    public static final String REPORT_DIR_PROPERTY = "interface.contract.report-dir";
    public static final String DEFAULT_REPORT_DIR = "target/interface-contract";

    private final HrExportReader hrExportReader;
    private final DepartmentMapping departmentMapping;
    private final FhirR4ExportService exportService;
    private final FhirR4ContractValidator validator;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public InterfaceContractCheck(HrExportReader hrExportReader, DepartmentMapping departmentMapping,
                                  FhirR4ExportService exportService, FhirR4ContractValidator validator) {
        this.hrExportReader = hrExportReader;
        this.departmentMapping = departmentMapping;
        this.exportService = exportService;
        this.validator = validator;
    }

    public InterfaceContractReport run(String exportDate) {
        List<HrPtoRecord> records = hrExportReader.read(exportDate);
        InterfaceContractReport report = new InterfaceContractReport();
        report.setJob("hr-pto-sync");
        report.setExportDate(exportDate);
        report.setExportFile(HrExportReader.EXPORT_DIR + "/" + HrExportReader.FILE_PREFIX + exportDate + HrExportReader.FILE_SUFFIX);
        report.setGeneratedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        report.setRecords(records.size());

        for (HrPtoRecord r : records) {
            if (departmentMapping.lookupForExport(r.costCenter, exportDate) == null) {
                report.getUnknownCostCenters().computeIfAbsent(r.costCenter, k -> new ArrayList<>()).add(r.employeeId);
            }
        }

        report.getDuplicatePractitioners().putAll(duplicatePractitioners(new PractitionerDeduplicator().group(records)));

        List<ExportedResource> resources = exportService.export(records, departmentMapping, exportDate);
        report.setResources(resources.size());
        report.getFindings().addAll(validator.validate(resources));

        writeReport(report, resources);
        log.info("Interface contract {} for export {}: {} unknown cost centers, {} duplicate practitioners, {} FHIR errors",
                report.getStatus(), exportDate, report.getUnknownCostCenters().size(),
                report.getDuplicatePractitioners().size(), report.getErrorCount());
        return report;
    }

    /**
     * Practitioners left after de-duplication that still share an NPI or a normalized name, keyed by
     * normalized name (or "NPI &lt;npi&gt;"), with every HR employee id involved.
     */
    static Map<String, List<String>> duplicatePractitioners(List<PractitionerGroup> groups) {
        Map<String, List<PractitionerGroup>> byKey = new LinkedHashMap<>();
        for (PractitionerGroup g : groups) {
            byKey.computeIfAbsent(g.normalizedName(), k -> new ArrayList<>()).add(g);
            if (g.npi() != null) {
                byKey.computeIfAbsent("NPI " + g.npi(), k -> new ArrayList<>()).add(g);
            }
        }
        Map<String, List<String>> dups = new LinkedHashMap<>();
        for (Map.Entry<String, List<PractitionerGroup>> e : byKey.entrySet()) {
            if (e.getValue().size() > 1) {
                List<String> ids = new ArrayList<>();
                for (PractitionerGroup g : e.getValue()) {
                    ids.addAll(g.employeeIds());
                }
                dups.put(e.getKey(), ids);
            }
        }
        return dups;
    }

    public static Path reportDir() {
        return Paths.get(System.getProperty(REPORT_DIR_PROPERTY, DEFAULT_REPORT_DIR));
    }

    private void writeReport(InterfaceContractReport report, List<ExportedResource> resources) {
        Path dir = reportDir();
        try {
            Files.createDirectories(dir.resolve("resources"));
            Files.write(dir.resolve("report.json"), mapper.writeValueAsBytes(report));
            Files.write(dir.resolve("report.md"), toMarkdown(report).getBytes(StandardCharsets.UTF_8));
            for (ExportedResource r : resources) {
                Files.write(dir.resolve("resources").resolve(r.getResourceType() + "-" + r.getId() + ".json"),
                        r.getJson().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write contract report to " + dir, e);
        }
    }

    public static String toMarkdown(InterfaceContractReport r) {
        StringBuilder md = new StringBuilder();
        md.append("# Interface contract report: ").append(r.getJob()).append("\n\n");
        md.append("- Export: `").append(r.getExportFile()).append("`\n");
        md.append("- Generated: ").append(r.getGeneratedAt()).append("\n");
        md.append("- Records: ").append(r.getRecords()).append(", FHIR resources: ").append(r.getResources()).append("\n");
        md.append("- Result: **").append(r.getStatus()).append("** (")
          .append(r.getErrorCount()).append(" FHIR errors, ").append(r.getWarningCount()).append(" warnings, ")
          .append(r.getUnknownCostCenters().size()).append(" unknown cost centers, ")
          .append(r.getDuplicatePractitioners().size()).append(" duplicate practitioners)\n\n");

        md.append("## 1. Cost center to department mapping\n\n");
        if (r.getUnknownCostCenters().isEmpty()) {
            md.append("All cost centers resolve to a department.\n\n");
        } else {
            md.append("| Cost center | Employee ids | Error |\n|---|---|---|\n");
            for (Map.Entry<String, List<String>> e : r.getUnknownCostCenters().entrySet()) {
                md.append("| ").append(e.getKey()).append(" | ").append(String.join(", ", e.getValue()))
                  .append(" | AE – Unknown department for cost center ").append(e.getKey()).append(" |\n");
            }
            md.append("\n");
        }

        md.append("## 2. Practitioner identity\n\n");
        if (r.getDuplicatePractitioners().isEmpty()) {
            md.append("No duplicate practitioners.\n\n");
        } else {
            md.append("| Practitioner | HR employee ids |\n|---|---|\n");
            for (Map.Entry<String, List<String>> e : r.getDuplicatePractitioners().entrySet()) {
                md.append("| ").append(e.getKey()).append(" | ").append(String.join(", ", e.getValue())).append(" |\n");
            }
            md.append("\n");
        }

        md.append("## 3. FHIR R4 validation\n\n");
        if (r.getFindings().isEmpty()) {
            md.append("No findings.\n");
        } else {
            md.append("| Resource | Severity | Location | Message |\n|---|---|---|---|\n");
            for (ContractFinding f : r.getFindings()) {
                md.append("| ").append(f.getResource()).append(" | ").append(f.getSeverity()).append(" | ")
                  .append(f.getLocation()).append(" | ").append(f.getMessage().replace("|", "\\|").replace("\n", " ")).append(" |\n");
            }
        }
        return md.toString();
    }
}
