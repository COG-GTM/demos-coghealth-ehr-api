package com.medchart.ehr.legacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartmentMappingRulesTest {

    private final DepartmentMapping mapping = new DepartmentMapping();

    @Test
    @DisplayName("BR-LEG-20: cost centers are matched trimmed; null/unknown cost centers have no department")
    void brLeg20_lookupRules() {
        assertEquals(2130, mapping.lookup("  CC-200-5410 "));
        assertNull(mapping.lookup(null));
        assertNull(mapping.lookup("cc-200-5410"));
    }

    @Test
    @DisplayName("BR-LEG-20: unknown department ids have a blank name; the known set matches the mapping file")
    void brLeg20_namesAndKnownSet() {
        assertEquals("Endoscopy Suite", mapping.departmentName(2140));
        assertEquals("", mapping.departmentName(9999));
        assertEquals(4, mapping.size());
        assertTrue(mapping.knownCostCenters().contains("CC-200-5420"));
        assertEquals(mapping.size(), mapping.knownCostCenters().size());
    }
}
