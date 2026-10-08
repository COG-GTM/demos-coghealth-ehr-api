package com.medchart.ehr.interop.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medchart.ehr.legacy.DepartmentMapping;
import com.medchart.ehr.legacy.HrPtoRecord;
import com.medchart.ehr.legacy.HrPtoSyncJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts the hr-pto-sync input (Workday export rows + cost center mapping) into FHIR R4
 * Practitioner / PractitionerRole / Schedule / Slot JSON, as the downstream Epic interface expects.
 *
 * Rows that resolve to a department are enriched from the EHR side (NPI identifier system, schedule
 * actor, PTO period as instants). Rows that do not resolve are passed through as received from HR so
 * IE-Prod can quarantine them.
 */
@Service
@Slf4j
public class FhirR4ExportService {

    public static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";
    public static final String ORGANIZATION_PREFIX = "Organization/dept-";

    private static final String OFFSET = "-04:00";

    private final ObjectMapper mapper = new ObjectMapper();

    public List<ExportedResource> export(List<HrPtoRecord> records, DepartmentMapping mapping, String exportDate) {
        int year = Integer.parseInt(exportDate.substring(0, 4));
        List<ExportedResource> out = new ArrayList<>();
        for (HrPtoRecord r : records) {
            Integer dept = mapping.lookup(r.costCenter);
            boolean matched = dept != null;
            out.add(practitioner(r, matched));
            out.add(practitionerRole(r, dept, mapping));
            out.add(schedule(r, matched));
            if (r.hasPto()) {
                out.add(matched ? slotFromParsedPeriod(r, year) : slotPassthrough(r));
            }
        }
        log.info("Exported {} FHIR resources from {} HR records", out.size(), records.size());
        return out;
    }

    private ExportedResource practitioner(HrPtoRecord r, boolean matched) {
        ObjectNode p = mapper.createObjectNode();
        p.put("resourceType", "Practitioner");
        p.put("id", r.employeeId);
        ArrayNode ids = p.putArray("identifier");
        ids.addObject().put("value", r.employeeId);
        if (r.npi != null && r.npi.length() > 0) {
            ObjectNode npi = ids.addObject();
            if (matched) {
                npi.put("system", NPI_SYSTEM);
            }
            npi.put("value", r.npi);
        }
        p.put("active", true);
        ObjectNode name = p.putArray("name").addObject();
        name.put("family", r.lastName);
        name.putArray("given").add(r.firstName);
        name.putArray("suffix").add("MD");
        return new ExportedResource("Practitioner", r.employeeId, r.employeeId, write(p));
    }

    private ExportedResource practitionerRole(HrPtoRecord r, Integer dept, DepartmentMapping mapping) {
        ObjectNode pr = mapper.createObjectNode();
        pr.put("resourceType", "PractitionerRole");
        pr.put("id", r.employeeId);
        pr.put("active", true);
        pr.putObject("practitioner").put("reference", "Practitioner/" + r.employeeId);
        if (dept != null) {
            ObjectNode org = pr.putObject("organization");
            org.put("reference", ORGANIZATION_PREFIX + dept);
            org.put("display", mapping.departmentName(dept));
        }
        ObjectNode code = pr.putArray("code").addObject();
        code.put("text", "Surgeon");
        return new ExportedResource("PractitionerRole", r.employeeId, r.employeeId, write(pr));
    }

    private ExportedResource schedule(HrPtoRecord r, boolean matched) {
        ObjectNode s = mapper.createObjectNode();
        s.put("resourceType", "Schedule");
        s.put("id", r.employeeId);
        s.put("active", true);
        if (matched) {
            s.putArray("actor").addObject().put("reference", "PractitionerRole/" + r.employeeId);
        }
        s.put("comment", "OR block schedule " + r.lastName + ", " + r.firstName + " (" + r.costCenter + ")");
        return new ExportedResource("Schedule", r.employeeId, r.employeeId, write(s));
    }

    private ExportedResource slotFromParsedPeriod(HrPtoRecord r, int year) {
        List<LocalDate> days = HrPtoSyncJob.parsePtoDays(r.ptoStart, r.ptoEnd, year);
        ObjectNode s = slotBase(r);
        if (days.isEmpty()) {
            s.put("start", r.ptoStart);
            s.put("end", r.ptoStart);
        } else {
            s.put("start", days.get(0) + "T00:00:00" + OFFSET);
            s.put("end", days.get(days.size() - 1) + "T23:59:59" + OFFSET);
        }
        return new ExportedResource("Slot", r.employeeId + "-pto", r.employeeId, write(s));
    }

    private ExportedResource slotPassthrough(HrPtoRecord r) {
        ObjectNode s = slotBase(r);
        s.put("start", instant(r.ptoStart, "T00:00:00"));
        s.put("end", instant(r.ptoEnd.length() > 0 ? r.ptoEnd : r.ptoStart, "T23:59:59"));
        return new ExportedResource("Slot", r.employeeId + "-pto", r.employeeId, write(s));
    }

    private ObjectNode slotBase(HrPtoRecord r) {
        ObjectNode s = mapper.createObjectNode();
        s.put("resourceType", "Slot");
        s.put("id", r.employeeId + "-pto");
        s.putObject("schedule").put("reference", "Schedule/" + r.employeeId);
        s.put("status", "busy-unavailable");
        s.put("comment", (r.ptoType + " " + r.notes).trim());
        return s;
    }

    private static String instant(String value, String timeSuffix) {
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return value + timeSuffix;
        }
        return value;
    }

    private String write(ObjectNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize FHIR resource", e);
        }
    }
}
