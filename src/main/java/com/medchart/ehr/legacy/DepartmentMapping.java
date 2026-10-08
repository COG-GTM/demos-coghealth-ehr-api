package com.medchart.ehr.legacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Effective-dated HR cost center -> EHR department mapping
 * (columns: cost_center, department_id, department_name, effective_from, effective_to; effective_to inclusive, blank = open).
 */
@Component
@Slf4j
public class DepartmentMapping {

    public static final String MAP_FILE = "interfaces/hr/cost_center_department_map.csv";

    private final Map<String, List<Entry>> byCostCenter = new LinkedHashMap<String, List<Entry>>();
    private final Map<Integer, String> deptNames = new HashMap<Integer, String>();
    private int rows;

    public DepartmentMapping() {
        load();
    }

    static final class Entry {
        final int departmentId;
        final LocalDate effectiveFrom;
        final LocalDate effectiveTo;

        Entry(int departmentId, LocalDate effectiveFrom, LocalDate effectiveTo) {
            this.departmentId = departmentId;
            this.effectiveFrom = effectiveFrom;
            this.effectiveTo = effectiveTo;
        }

        boolean isEffectiveOn(LocalDate day) {
            return (effectiveFrom == null || !day.isBefore(effectiveFrom))
                    && (effectiveTo == null || !day.isAfter(effectiveTo));
        }
    }

    private void load() {
        InputStream in = DepartmentMapping.class.getClassLoader().getResourceAsStream(MAP_FILE);
        if (in == null) {
            throw new IllegalStateException("Mapping file missing: " + MAP_FILE);
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (line.trim().length() == 0) {
                    continue;
                }
                String[] f = line.split(",", -1);
                String cc = f[0].trim();
                int dept = Integer.parseInt(f[1].trim());
                LocalDate from = f.length > 3 ? date(f[3]) : null;
                LocalDate to = f.length > 4 ? date(f[4]) : null;
                byCostCenter.computeIfAbsent(cc, k -> new ArrayList<Entry>()).add(new Entry(dept, from, to));
                if (f.length > 2) {
                    deptNames.put(dept, f[2].trim());
                }
                rows++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot read " + MAP_FILE, e);
        }
        log.info("Loaded {} cost center mappings ({} cost centers)", rows, byCostCenter.size());
    }

    private static LocalDate date(String s) {
        String v = s.trim();
        return v.length() == 0 ? null : LocalDate.parse(v);
    }

    /** Department for the cost center from its most recently effective row, ignoring effective dates. */
    public Integer lookup(String costCenter) {
        List<Entry> entries = entries(costCenter);
        if (entries == null) {
            return null;
        }
        Entry latest = null;
        for (Entry e : entries) {
            if (latest == null || (e.effectiveFrom != null
                    && (latest.effectiveFrom == null || e.effectiveFrom.isAfter(latest.effectiveFrom)))) {
                latest = e;
            }
        }
        return latest.departmentId;
    }

    /** Department for the cost center on the given day, or null when no row is effective that day. */
    public Integer lookup(String costCenter, LocalDate asOf) {
        if (asOf == null) {
            return lookup(costCenter);
        }
        List<Entry> entries = entries(costCenter);
        if (entries == null) {
            return null;
        }
        for (Entry e : entries) {
            if (e.isEffectiveOn(asOf)) {
                return e.departmentId;
            }
        }
        return null;
    }

    /** Department for the cost center as of an HR export date (yyyyMMdd). */
    public Integer lookupForExport(String costCenter, String exportDate) {
        return lookup(costCenter, exportDay(exportDate));
    }

    public static LocalDate exportDay(String exportDate) {
        if (exportDate == null || exportDate.trim().length() == 0) {
            return null;
        }
        return LocalDate.parse(exportDate.trim(), DateTimeFormatter.BASIC_ISO_DATE);
    }

    private List<Entry> entries(String costCenter) {
        if (costCenter == null) {
            return null;
        }
        return byCostCenter.get(costCenter.trim());
    }

    public String departmentName(Integer deptId) {
        String n = deptNames.get(deptId);
        return n == null ? "" : n;
    }

    public Set<String> knownCostCenters() {
        return new LinkedHashSet<String>(byCostCenter.keySet());
    }

    public int size() {
        return byCostCenter.size();
    }
}
