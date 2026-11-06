package com.medchart.ehr.interop.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medchart.ehr.interop.fhir.PractitionerDeduplicator.PractitionerGroup;
import com.medchart.ehr.interop.fhir.PtoPeriodParser.PtoPeriod;
import com.medchart.ehr.legacy.DepartmentMapping;
import com.medchart.ehr.legacy.HrPtoRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts the hr-pto-sync input (Workday export rows + effective-dated cost center mapping) into FHIR R4
 * Practitioner / PractitionerRole / Schedule / Slot JSON, as the downstream Epic interface expects.
 *
 * One Practitioner per person (see {@link PractitionerDeduplicator}) carrying the NPI and every HR employee id;
 * PractitionerRole references the department the cost center maps to on the export date; Schedule.actor is the
 * PractitionerRole; one busy-unavailable Slot per distinct approved PTO period, as America/New_York instants.
 */
@Service
@Slf4j
public class FhirR4ExportService {

    private static final DateTimeFormatter SLOT_ID_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";
    public static final String HR_EMPLOYEE_ID_SYSTEM = "urn:coghealth:workday:employee-id";
    public static final String ORGANIZATION_PREFIX = "Organization/dept-";

    private final ObjectMapper mapper = new ObjectMapper();
    private final PractitionerDeduplicator deduplicator = new PractitionerDeduplicator();

    public List<ExportedResource> export(List<HrPtoRecord> records, DepartmentMapping mapping, String exportDate) {
        LocalDate exportDay = DepartmentMapping.exportDay(exportDate);
        List<PractitionerGroup> groups = deduplicator.group(records);
        List<ExportedResource> out = new ArrayList<>();
        for (PractitionerGroup g : groups) {
            HrPtoRecord p = g.primary();
            Integer dept = mapping.lookup(p.costCenter, exportDay);
            out.add(practitioner(g));
            out.add(practitionerRole(p, dept, mapping));
            out.add(schedule(p));
            Set<String> periods = new HashSet<>();
            for (HrPtoRecord r : g.records()) {
                if (!r.isApproved()) {
                    continue;
                }
                PtoPeriod period = PtoPeriodParser.parse(r, exportDay);
                if (period == null) {
                    if (r.hasPto()) {
                        log.warn("PTO period not parseable for employee {} (line {}); no Slot exported", r.employeeId, r.lineNumber);
                    }
                    continue;
                }
                if (periods.add(period.startInstant() + "/" + period.endInstant())) {
                    String id = p.employeeId + "-pto-" + period.start.format(SLOT_ID_TS);
                    out.add(slot(id, p, r, period));
                }
            }
        }
        log.info("Exported {} FHIR resources for {} practitioners from {} HR records", out.size(), groups.size(), records.size());
        return out;
    }

    private ExportedResource practitioner(PractitionerGroup g) {
        HrPtoRecord r = g.primary();
        ObjectNode p = mapper.createObjectNode();
        p.put("resourceType", "Practitioner");
        p.put("id", r.employeeId);
        ArrayNode ids = p.putArray("identifier");
        if (g.npi() != null) {
            ObjectNode npi = ids.addObject();
            npi.put("use", "official");
            npi.put("system", NPI_SYSTEM);
            npi.put("value", g.npi());
        }
        for (String employeeId : g.employeeIds()) {
            ObjectNode hr = ids.addObject();
            hr.put("use", employeeId.equals(r.employeeId) ? "usual" : "secondary");
            hr.put("system", HR_EMPLOYEE_ID_SYSTEM);
            hr.put("value", employeeId);
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

    private ExportedResource schedule(HrPtoRecord r) {
        ObjectNode s = mapper.createObjectNode();
        s.put("resourceType", "Schedule");
        s.put("id", r.employeeId);
        s.put("active", true);
        s.putArray("actor").addObject().put("reference", "PractitionerRole/" + r.employeeId);
        s.put("comment", "OR block schedule " + r.lastName + ", " + r.firstName + " (" + r.costCenter + ")");
        return new ExportedResource("Schedule", r.employeeId, r.employeeId, write(s));
    }

    private ExportedResource slot(String id, HrPtoRecord practitioner, HrPtoRecord r, PtoPeriod period) {
        ObjectNode s = mapper.createObjectNode();
        s.put("resourceType", "Slot");
        s.put("id", id);
        s.putObject("schedule").put("reference", "Schedule/" + practitioner.employeeId);
        s.put("status", "busy-unavailable");
        s.put("start", period.startInstant());
        s.put("end", period.endInstant());
        s.put("comment", (r.ptoType + " " + r.notes).trim());
        return new ExportedResource("Slot", id, r.employeeId, write(s));
    }

    private String write(ObjectNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize FHIR resource", e);
        }
    }
}
