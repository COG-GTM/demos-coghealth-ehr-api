package com.medchart.ehr.interop.fhir;

import com.medchart.ehr.legacy.DepartmentMapping;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirR4ContractValidatorTest {

    private static FhirR4ContractValidator validator;

    @BeforeAll
    static void init() {
        validator = new FhirR4ContractValidator();
    }

    private static ExportedResource res(String type, String json) {
        return new ExportedResource(type, "1", "1", json);
    }

    @Test
    @DisplayName("BR-FHIR-06: a Practitioner without an NPI-system identifier is a contract ERROR")
    void brFhir06_practitionerRequiresNpi() {
        List<ContractFinding> f = validator.validateContractRules(res("Practitioner",
                "{\"resourceType\":\"Practitioner\",\"identifier\":[{\"value\":\"0000000001\"}]}"));
        assertEquals(1, f.size());
        assertEquals("ERROR", f.get(0).getSeverity());
        assertEquals("Practitioner.identifier", f.get(0).getLocation());
        assertTrue(validator.validateContractRules(res("Practitioner", "{\"identifier\":[{\"system\":\""
                + FhirR4ExportService.NPI_SYSTEM + "\",\"value\":\"1\"}]}")).isEmpty());
    }

    @Test
    @DisplayName("BR-FHIR-07: a PractitionerRole without a department Organization is a contract ERROR")
    void brFhir07_roleRequiresOrganization() {
        List<ContractFinding> f = validator.validateContractRules(res("PractitionerRole", "{}"));
        assertEquals("PractitionerRole.organization", f.get(0).getLocation());
        assertTrue(validator.validateContractRules(res("PractitionerRole",
                "{\"organization\":{\"reference\":\"Organization/dept-1\"}}")).isEmpty());
    }

    @Test
    @DisplayName("BR-FHIR-08: Slot start/end without a timezone offset are contract ERRORs; offset instants pass")
    void brFhir08_slotRequiresOffset() {
        List<ContractFinding> f = validator.validateContractRules(res("Slot",
                "{\"start\":\"2026-10-14T00:00:00\",\"end\":\"2026-10-14T23:59:59.5\"}"));
        assertEquals(2, f.size());
        assertEquals("Slot.start", f.get(0).getLocation());
        assertEquals("Slot.end", f.get(1).getLocation());
        assertTrue(validator.validateContractRules(res("Slot",
                "{\"start\":\"2026-10-14T00:00:00-04:00\",\"end\":\"2026-12-14T23:59:59-05:00\"}")).isEmpty());
        assertTrue(validator.validateContractRules(res("Schedule", "{}")).isEmpty());
    }

    @Test
    @DisplayName("BR-FHIR-09: a resource that is not valid JSON is a FATAL finding")
    void brFhir09_invalidJsonFatal() {
        List<ContractFinding> f = validator.validateContractRules(res("Slot", "{not json"));
        assertEquals("FATAL", f.get(0).getSeverity());
        assertTrue(f.get(0).isError());
    }

    @Test
    @DisplayName("BR-FHIR-06/07/08: a mapped export validates with zero contract and structural errors; an unmapped one fails")
    void brFhir06_endToEndValidation() {
        FhirR4ExportService export = new FhirR4ExportService();
        DepartmentMapping mapping = new DepartmentMapping();
        List<ContractFinding> ok = validator.validate(export.export(List.of(
                FhirR4ExportServiceTest.record("201", "CC-200-5410", "2026-10-14", "2026-10-14")), mapping, "20260930"));
        assertFalse(ok.stream().anyMatch(ContractFinding::isError), ok.toString());

        List<ContractFinding> bad = validator.validate(export.export(List.of(
                FhirR4ExportServiceTest.record("202", "CC-999", "2026-10-14", "")), mapping, "20260930"));
        assertTrue(bad.stream().filter(ContractFinding::isError).count() >= 3, bad.toString());
        assertNotNull(validator.getFhirContext());
    }

    @Test
    @DisplayName("BR-FHIR-10: only ERROR and FATAL findings count as errors")
    void brFhir10_findingSeverity() {
        assertTrue(new ContractFinding("r", "ERROR", "l", "m").isError());
        assertTrue(new ContractFinding("r", "FATAL", "l", "m").isError());
        assertFalse(new ContractFinding("r", "WARNING", "l", "m").isError());
        assertFalse(new ContractFinding("r", "INFORMATION", "l", "m").isError());
        assertTrue(new ContractFinding("Slot/1", "ERROR", "Slot.start", "x").toString().contains("Slot/1"));
        ExportedResource e = new ExportedResource("Slot", "1-pto", "1", "{}");
        assertEquals("Slot/1-pto", e.getReference());
        assertEquals("1", e.getSourceEmployeeId());
    }
}
