package com.medchart.ehr.legacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class DepartmentMapping {

    public static final String MAP_FILE = "interfaces/hr/cost_center_department_map.csv";

    private final Map<String, Integer> ccToDept = new HashMap<String, Integer>();
    private final Map<Integer, String> deptNames = new HashMap<Integer, String>();

    public DepartmentMapping() {
        load();
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
                ccToDept.put(cc, dept);
                if (f.length > 2) {
                    deptNames.put(dept, f[2].trim());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot read " + MAP_FILE, e);
        }
        log.info("Loaded {} cost center mappings", ccToDept.size());
    }

    public Integer lookup(String costCenter) {
        if (costCenter == null) {
            return null;
        }
        return ccToDept.get(costCenter.trim());
    }

    public String departmentName(Integer deptId) {
        String n = deptNames.get(deptId);
        return n == null ? "" : n;
    }

    public Set<String> knownCostCenters() {
        return new LinkedHashSet<String>(ccToDept.keySet());
    }

    public int size() {
        return ccToDept.size();
    }
}
