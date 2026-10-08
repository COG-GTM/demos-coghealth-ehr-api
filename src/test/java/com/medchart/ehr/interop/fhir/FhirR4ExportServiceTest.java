package com.medchart.ehr.interop.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.legacy.DepartmentMapping;
import com.medchart.ehr.legacy.HrPtoRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirR4ExportServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static List<ExportedResource> resources;

    private static HrPtoRecord row(String id, String last, String first, String npi, String cc,
                                   String start, String end, String status, String notes) {
        HrPtoRecord r = PractitionerDeduplicatorTest.row(id, last, first, npi, notes);
        r.costCenter = cc;
        r.ptoStart = start;
        r.ptoEnd = end;
        r.ptoType = start.length() > 0 ? "Vacation" : "";
        r.status = status;
        return r;
    }

    @BeforeAll
    static void export() {
        List<HrPtoRecord> records = Arrays.asList(
                row("E104422", "Marquez", "Elena", "1932405817", "CC-230-5410", "10/13-10/17 vacation", "", "Approved", "approved by dept admin 09/22"),
                row("E119901", "MARQUEZ", "ELENA", "", "CC-230-5410", "10/13-10/17 vacation", "", "Approved", "legacy record - see E104422"),
                row("E108913", "Okafor", "Samuel", "1750389264", "CC-240-5412", "2026-10-14", "2026-10-14", "Approved", ""),
                row("E109640", "Whitfield", "Daniel", "1619072483", "CC-240-5420", "", "", "", "no approved time off in period"),
                row("E200001", "Pending", "Pat", "1000000004", "CC-240-5420", "2026-10-20", "2026-10-20", "Submitted", ""));
        resources = new FhirR4ExportService().export(records, new DepartmentMapping(), "20261008");
    }

    private static JsonNode resource(String reference) throws Exception {
        for (ExportedResource r : resources) {
            if (r.getReference().equals(reference)) {
                return JSON.readTree(r.getJson());
            }
        }
        throw new AssertionError("missing " + reference);
    }

    private static List<String> references() {
        List<String> refs = new ArrayList<>();
        for (ExportedResource r : resources) {
            refs.add(r.getReference());
        }
        return refs;
    }

    @Test
    void practitionerHasNpiIdentifierSystem() throws Exception {
        JsonNode ids = resource("Practitioner/E108913").path("identifier");
        assertEquals(FhirR4ExportService.NPI_SYSTEM, ids.get(0).path("system").asText());
        assertEquals("1750389264", ids.get(0).path("value").asText());
        assertEquals(FhirR4ExportService.HR_EMPLOYEE_ID_SYSTEM, ids.get(1).path("system").asText());
    }

    @Test
    void duplicatePractitionerCollapsedWithBothHrIdentifiers() throws Exception {
        assertFalse(references().contains("Practitioner/E119901"));
        assertFalse(references().contains("Slot/E119901-pto"));
        assertFalse(references().contains("Slot/E104422-pto-2"));
        JsonNode ids = resource("Practitioner/E104422").path("identifier");
        assertEquals(3, ids.size());
        assertEquals("1932405817", ids.get(0).path("value").asText());
        assertEquals("E104422", ids.get(1).path("value").asText());
        assertEquals("E119901", ids.get(2).path("value").asText());
        assertEquals("secondary", ids.get(2).path("use").asText());
    }

    @Test
    void practitionerRoleLinkedToEffectiveDepartment() throws Exception {
        JsonNode role = resource("PractitionerRole/E104422");
        assertEquals("Organization/dept-2130", role.path("organization").path("reference").asText());
        assertEquals("Main OR - Tower", role.path("organization").path("display").asText());
        assertEquals("Organization/dept-2132", resource("PractitionerRole/E108913").path("organization").path("reference").asText());
    }

    @Test
    void scheduleActorIsPractitionerRole() throws Exception {
        assertEquals("PractitionerRole/E109640", resource("Schedule/E109640").path("actor").get(0).path("reference").asText());
    }

    @Test
    void slotIsBusyUnavailableWithNewYorkInstants() throws Exception {
        JsonNode slot = resource("Slot/E104422-pto");
        assertEquals("busy-unavailable", slot.path("status").asText());
        assertEquals("2026-10-13T00:00:00-04:00", slot.path("start").asText());
        assertEquals("2026-10-17T23:59:59-04:00", slot.path("end").asText());
        assertEquals("Schedule/E104422", slot.path("schedule").path("reference").asText());
    }

    @Test
    void noSlotWithoutApprovedPto() {
        assertFalse(references().contains("Slot/E109640-pto"));
        assertFalse(references().contains("Slot/E200001-pto"));
        assertTrue(references().contains("Slot/E108913-pto"));
    }

    @Test
    void exportValidatesWithZeroErrors() {
        List<ContractFinding> findings = new FhirR4ContractValidator().validate(resources);
        for (ContractFinding f : findings) {
            assertFalse("ERROR".equals(f.getSeverity()) || "FATAL".equals(f.getSeverity()), f::toString);
        }
    }
}
