package com.medchart.ehr.interop.fhir;

import com.medchart.ehr.legacy.DepartmentMapping;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterfaceContractCheckTest {

    @TempDir
    Path tmp;

    private final HrExportReader reader = mock(HrExportReader.class);
    private final FhirR4ContractValidator validator = mock(FhirR4ContractValidator.class);
    private final InterfaceContractCheck check =
            new InterfaceContractCheck(reader, new DepartmentMapping(), new FhirR4ExportService(), validator);

    @AfterEach
    void clear() {
        System.clearProperty(InterfaceContractCheck.REPORT_DIR_PROPERTY);
    }

    private InterfaceContractReport run(List<HrPtoRecord> records, List<ContractFinding> findings) {
        System.setProperty(InterfaceContractCheck.REPORT_DIR_PROPERTY, tmp.toString());
        when(reader.read("20260930")).thenReturn(records);
        when(validator.validate(anyList())).thenReturn(new ArrayList<>(findings));
        return check.run("20260930");
    }

    @Test
    @DisplayName("BR-FHIR-11: a clean export PASSes and the JSON/Markdown report and resources are written")
    void brFhir11_cleanExportPasses() throws Exception {
        InterfaceContractReport r = run(List.of(FhirR4ExportServiceTest.record("301", "CC-200-5410", "2026-10-14", "")),
                List.of(new ContractFinding("Slot/301-pto", "WARNING", "Slot", "advisory")));

        assertTrue(r.isPassed());
        assertEquals("PASS", r.getStatus());
        assertEquals(1, r.getWarningCount());
        assertEquals(1, r.getRecords());
        assertEquals(4, r.getResources());
        assertEquals("hr-pto-sync", r.getJob());
        assertEquals("20260930", r.getExportDate());
        assertEquals("interfaces/hr/export/workday_pto_export_20260930.csv", r.getExportFile());
        assertTrue(Files.exists(tmp.resolve("report.json")));
        assertTrue(Files.exists(tmp.resolve("resources").resolve("Slot-301-pto.json")));
        String md = Files.readString(tmp.resolve("report.md"));
        assertTrue(md.contains("All cost centers resolve to a department."));
        assertTrue(md.contains("No duplicate practitioners."));
        assertTrue(md.contains("**PASS**"));
    }

    @Test
    @DisplayName("BR-FHIR-12: unknown cost centers fail the contract and are grouped by cost center (AE error)")
    void brFhir12_unknownCostCenterFails() throws Exception {
        InterfaceContractReport r = run(List.of(
                FhirR4ExportServiceTest.record("302", "CC-999", "", ""),
                FhirR4ExportServiceTest.record("303", "CC-999", "", "")), List.of());

        assertFalse(r.isPassed());
        assertEquals(List.of("302", "303"), r.getUnknownCostCenters().get("CC-999"));
        assertTrue(Files.readString(tmp.resolve("report.md")).contains("AE – Unknown department for cost center CC-999"));
    }

    @Test
    @DisplayName("BR-FHIR-13: the same practitioner under two HR ids (same normalized name) is a duplicate")
    void brFhir13_duplicateByName() {
        HrPtoRecord a = FhirR4ExportServiceTest.record("304", "CC-200-5410", "", "");
        HrPtoRecord b = FhirR4ExportServiceTest.record("305", "CC-200-5410", "", "");
        a.firstName = "Jo-Ann";
        b.firstName = "JoAnn ";
        b.npi = "";
        a.lastName = "O'Test";
        b.lastName = "otest";
        InterfaceContractReport r = run(List.of(a, b), List.of());
        assertEquals(List.of("304", "305"), r.getDuplicatePractitioners().get("OTEST JOANN"));
        assertEquals("FAIL", r.getStatus());
    }

    @Test
    @DisplayName("BR-FHIR-13: the same NPI under two HR ids with different names is a duplicate")
    void brFhir13_duplicateByNpi() throws Exception {
        HrPtoRecord a = FhirR4ExportServiceTest.record("306", "CC-200-5410", "", "");
        HrPtoRecord b = FhirR4ExportServiceTest.record("307", "CC-200-5410", "", "");
        b.npi = a.npi;
        InterfaceContractReport r = run(List.of(a, b), List.of());
        assertEquals(List.of("306", "307"), r.getDuplicatePractitioners().get("NPI " + a.npi));
        assertTrue(Files.readString(tmp.resolve("report.md")).contains("| NPI " + a.npi + " | 306, 307 |"));
    }

    @Test
    @DisplayName("BR-FHIR-11: any FHIR ERROR fails the contract and is listed with pipes escaped")
    void brFhir11_errorsFail() throws Exception {
        InterfaceContractReport r = run(List.of(FhirR4ExportServiceTest.record("308", "CC-200-5410", "", "")),
                List.of(new ContractFinding("Practitioner/308", "ERROR", "Practitioner", "a|b\nc")));
        assertEquals(1, r.getErrorCount());
        assertEquals(1, r.getErrors().size());
        assertFalse(r.isPassed());
        assertTrue(Files.readString(tmp.resolve("report.md")).contains("a\\|b c"));
    }

    @Test
    @DisplayName("BR-FHIR-11: an unwritable report directory is a hard failure")
    void brFhir11_unwritableReportDir() throws Exception {
        Path file = Files.createFile(tmp.resolve("not-a-dir"));
        System.setProperty(InterfaceContractCheck.REPORT_DIR_PROPERTY, file.toString());
        when(reader.read("20260930")).thenReturn(List.of());
        when(validator.validate(anyList())).thenReturn(new ArrayList<>());
        assertThrows(IllegalStateException.class, () -> check.run("20260930"));
    }

    @Test
    @DisplayName("BR-FHIR-11: the report directory defaults to target/interface-contract")
    void brFhir11_defaultReportDir() {
        assertEquals(Path.of("target/interface-contract"), InterfaceContractCheck.reportDir());
        InterfaceContractReport empty = new InterfaceContractReport();
        empty.setJob("j");
        empty.setGeneratedAt("now");
        assertTrue(InterfaceContractCheck.toMarkdown(empty).contains("No findings."));
    }
}
