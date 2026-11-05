package com.medchart.ehr.interop.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.legacy.DepartmentMapping;
import com.medchart.ehr.legacy.HrPtoRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirR4ExportServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final FhirR4ExportService service = new FhirR4ExportService();
    private final DepartmentMapping mapping = new DepartmentMapping();

    static HrPtoRecord record(String id, String costCenter, String start, String end) {
        HrPtoRecord r = new HrPtoRecord();
        r.employeeId = id;
        r.lastName = "Test";
        r.firstName = "Surgeon" + id;
        r.npi = "0000000" + id;
        r.costCenter = costCenter;
        r.ptoStart = start;
        r.ptoEnd = end;
        r.ptoType = "Vacation";
        r.status = "Approved";
        r.notes = "";
        return r;
    }

    private static JsonNode find(List<ExportedResource> out, String type) throws Exception {
        for (ExportedResource r : out) {
            if (r.getResourceType().equals(type)) {
                return JSON.readTree(r.getJson());
            }
        }
        throw new AssertionError("no " + type);
    }

    @Test
    @DisplayName("BR-FHIR-01: a mapped HR row exports Practitioner (NPI system), PractitionerRole (department org), Schedule and Slot")
    void brFhir01_matchedRowExportsFullBundle() throws Exception {
        List<ExportedResource> out = service.export(
                List.of(record("101", "CC-200-5412", "2026-10-14", "2026-10-15")), mapping, "20260930");

        assertEquals(List.of("Practitioner", "PractitionerRole", "Schedule", "Slot"),
                out.stream().map(ExportedResource::getResourceType).collect(Collectors.toList()));
        JsonNode p = find(out, "Practitioner");
        assertEquals(FhirR4ExportService.NPI_SYSTEM, p.path("identifier").get(1).path("system").asText());
        assertEquals("0000000101", p.path("identifier").get(1).path("value").asText());
        JsonNode role = find(out, "PractitionerRole");
        assertEquals("Organization/dept-2132", role.path("organization").path("reference").asText());
        assertEquals("Cardiac OR", role.path("organization").path("display").asText());
        assertEquals("PractitionerRole/101", find(out, "Schedule").path("actor").get(0).path("reference").asText());
        JsonNode slot = find(out, "Slot");
        assertEquals("busy-unavailable", slot.path("status").asText());
        assertEquals("Schedule/101", slot.path("schedule").path("reference").asText());
        assertEquals("2026-10-14T00:00:00-04:00", slot.path("start").asText());
        assertEquals("2026-10-15T23:59:59-04:00", slot.path("end").asText());
        assertEquals("Slot/101-pto", out.get(3).getReference());
    }

    @Test
    @DisplayName("BR-FHIR-02: an unmapped cost center exports no NPI system, no organization, no actor and passes PTO through")
    void brFhir02_unmatchedRowPassthrough() throws Exception {
        List<ExportedResource> out = service.export(
                List.of(record("102", "CC-999", "2026-10-14", ""), record("103", "CC-999", "10/13-10/17", "")),
                mapping, "20260930");

        assertFalse(find(out, "Practitioner").path("identifier").get(1).has("system"));
        assertFalse(find(out, "PractitionerRole").has("organization"));
        assertFalse(find(out, "Schedule").has("actor"));
        JsonNode slot = find(out, "Slot");
        assertEquals("2026-10-14T00:00:00", slot.path("start").asText());
        assertEquals("2026-10-14T23:59:59", slot.path("end").asText());
        JsonNode freeText = JSON.readTree(out.get(7).getJson());
        assertEquals("10/13-10/17", freeText.path("start").asText());
    }

    @Test
    @DisplayName("BR-FHIR-03: PTO slot instants carry the America/New_York offset for that date (DST aware)")
    void brFhir03_newYorkOffsetPerDate() throws Exception {
        assertEquals("2026-07-01T00:00:00-04:00", FhirR4ExportService.zoned(LocalDate.of(2026, 7, 1), LocalTime.MIDNIGHT));
        assertEquals("2026-12-01T00:00:00-05:00", FhirR4ExportService.zoned(LocalDate.of(2026, 12, 1), LocalTime.MIDNIGHT));
        JsonNode slot = find(service.export(
                List.of(record("104", "CC-200-5410", "10/30-11/02", "")), mapping, "20261015"), "Slot");
        assertEquals("2026-10-30T00:00:00-04:00", slot.path("start").asText());
        assertEquals("2026-11-02T23:59:59-05:00", slot.path("end").asText());
    }

    @Test
    @DisplayName("BR-FHIR-04: unparseable PTO text on a mapped row is exported verbatim (so the validator flags it)")
    void brFhir04_unparseablePtoKeptVerbatim() throws Exception {
        JsonNode slot = find(service.export(
                List.of(record("105", "CC-200-5410", "see manager", "")), mapping, "20260930"), "Slot");
        assertEquals("see manager", slot.path("start").asText());
        assertEquals("see manager", slot.path("end").asText());
    }

    @Test
    @DisplayName("BR-FHIR-05: rows without PTO or NPI export no Slot and only the HR identifier")
    void brFhir05_noPtoNoSlot() throws Exception {
        HrPtoRecord r = record("106", "CC-200-5410", "", "");
        r.npi = "";
        List<ExportedResource> out = service.export(List.of(r), mapping, "20260930");
        assertEquals(3, out.size());
        assertEquals(1, find(out, "Practitioner").path("identifier").size());
        assertTrue(out.stream().noneMatch(x -> x.getResourceType().equals("Slot")));
    }
}
