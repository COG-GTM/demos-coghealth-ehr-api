package com.medchart.ehr.interop;

import com.medchart.ehr.interop.fhir.ContractFinding;
import com.medchart.ehr.interop.fhir.FhirR4ContractValidator;
import com.medchart.ehr.interop.fhir.FhirR4ExportService;
import com.medchart.ehr.interop.fhir.InterfaceContractCheck;
import com.medchart.ehr.interop.fhir.InterfaceContractReport;
import com.medchart.ehr.legacy.DepartmentMapping;
import com.medchart.ehr.legacy.HrExportReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interface contract for hr-pto-sync against the HR export selected by -Dhr.export.date (default: latest file).
 * Excluded from the default surefire run; executed by scripts/interface-contract.sh and the nightly workflow.
 */
@Tag("interface-contract")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrPtoInterfaceContractTest {

    private String exportDate;
    private InterfaceContractReport report;

    @BeforeAll
    void runContractCheck() {
        HrExportReader reader = new HrExportReader();
        exportDate = reader.resolveExportDate(System.getProperty(HrExportReader.DATE_PROPERTY));
        InterfaceContractCheck check = new InterfaceContractCheck(reader, new DepartmentMapping(),
                new FhirR4ExportService(), new FhirR4ContractValidator());
        report = check.run(exportDate);
        System.out.println(InterfaceContractCheck.toMarkdown(report));
    }

    @Test
    @DisplayName("every cost center in the HR export resolves to an EHR department")
    void everyCostCenterResolvesToDepartment() {
        Map<String, List<String>> unknown = report.getUnknownCostCenters();
        assertTrue(unknown.isEmpty(), () -> "Export " + exportDate + ": " + unknown.size()
                + " cost center(s) have no department in " + DepartmentMapping.MAP_FILE + ":\n"
                + unknown.entrySet().stream()
                    .map(e -> "  AE – Unknown department for cost center " + e.getKey() + " (employees " + String.join(", ", e.getValue()) + ")")
                    .collect(Collectors.joining("\n")));
    }

    @Test
    @DisplayName("exported FHIR R4 resources validate with zero errors")
    void fhirExportHasNoValidationErrors() {
        List<ContractFinding> errors = report.getErrors();
        assertEquals(0, errors.size(), () -> "Export " + exportDate + ": " + errors.size()
                + " FHIR R4 validation error(s) across " + report.getResources() + " resources:\n"
                + errors.stream().map(f -> "  " + f.toString()).collect(Collectors.joining("\n")));
    }

    @Test
    @DisplayName("no duplicate practitioners by NPI or name")
    void noDuplicatePractitioners() {
        Map<String, List<String>> dups = report.getDuplicatePractitioners();
        assertTrue(dups.isEmpty(), () -> "Export " + exportDate + ": " + dups.size()
                + " practitioner(s) appear under more than one HR employee id:\n"
                + dups.entrySet().stream()
                    .map(e -> "  " + e.getKey() + " -> " + String.join(", ", e.getValue()))
                    .collect(Collectors.joining("\n")));
    }
}
