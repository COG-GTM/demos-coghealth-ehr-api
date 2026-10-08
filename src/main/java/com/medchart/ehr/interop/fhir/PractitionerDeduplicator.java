package com.medchart.ehr.interop.fhir;

import com.medchart.ehr.legacy.HrPtoRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collapses HR export rows that describe the same practitioner:
 * <ol>
 *   <li>same NPI;</li>
 *   <li>otherwise same normalized name AND an HR identity link (the Workday record cross-references the other
 *       employee id, e.g. "legacy record - see E104422"), provided the two rows do not carry different NPIs.</li>
 * </ol>
 * Same name without an HR identity link is not merged (and is reported by the contract check).
 */
public class PractitionerDeduplicator {

    static final Pattern HR_CROSS_REFERENCE = Pattern.compile("\\bsee\\s+(E\\d+)\\b", Pattern.CASE_INSENSITIVE);

    public static final class PractitionerGroup {
        private final List<HrPtoRecord> records = new ArrayList<>();

        /** Row whose employee id becomes the Practitioner id: first row with an NPI, else the first row. */
        public HrPtoRecord primary() {
            for (HrPtoRecord r : records) {
                if (hasNpi(r)) {
                    return r;
                }
            }
            return records.get(0);
        }

        public String npi() {
            HrPtoRecord p = primary();
            return hasNpi(p) ? p.npi.trim() : null;
        }

        public List<HrPtoRecord> records() {
            return Collections.unmodifiableList(records);
        }

        public List<String> employeeIds() {
            List<String> ids = new ArrayList<>();
            HrPtoRecord p = primary();
            ids.add(p.employeeId);
            for (HrPtoRecord r : records) {
                if (r != p && !ids.contains(r.employeeId)) {
                    ids.add(r.employeeId);
                }
            }
            return ids;
        }

        public String normalizedName() {
            return PractitionerDeduplicator.normalizedName(primary());
        }
    }

    public List<PractitionerGroup> group(List<HrPtoRecord> records) {
        int n = records.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        Map<String, Integer> firstByNpi = new LinkedHashMap<>();
        Map<String, Integer> firstByEmployeeId = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            HrPtoRecord r = records.get(i);
            firstByEmployeeId.putIfAbsent(r.employeeId, i);
            if (hasNpi(r)) {
                Integer j = firstByNpi.putIfAbsent(r.npi.trim(), i);
                if (j != null) {
                    union(parent, i, j);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            HrPtoRecord r = records.get(i);
            String target = crossReference(r);
            Integer j = target == null ? null : firstByEmployeeId.get(target);
            if (j == null || j == i) {
                continue;
            }
            HrPtoRecord other = records.get(j);
            if (normalizedName(r).equals(normalizedName(other)) && !conflictingNpi(r, other)) {
                union(parent, i, j);
            }
        }
        Map<Integer, PractitionerGroup> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new PractitionerGroup()).records.add(records.get(i));
        }
        return new ArrayList<>(groups.values());
    }

    public static String normalizedName(HrPtoRecord r) {
        String name = (nullToEmpty(r.lastName) + " " + nullToEmpty(r.firstName)).toUpperCase(Locale.ROOT);
        return name.replaceAll("[^A-Z ]", "").replaceAll("\\s+", " ").trim();
    }

    static String crossReference(HrPtoRecord r) {
        if (r.notes == null) {
            return null;
        }
        Matcher m = HR_CROSS_REFERENCE.matcher(r.notes);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private static boolean conflictingNpi(HrPtoRecord a, HrPtoRecord b) {
        return hasNpi(a) && hasNpi(b) && !a.npi.trim().equals(b.npi.trim());
    }

    static boolean hasNpi(HrPtoRecord r) {
        return r.npi != null && r.npi.trim().length() > 0;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[Math.max(ra, rb)] = Math.min(ra, rb);
        }
    }
}
