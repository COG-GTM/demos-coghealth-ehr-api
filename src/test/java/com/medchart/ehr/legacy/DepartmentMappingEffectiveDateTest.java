package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartmentMappingEffectiveDateTest {

    private final DepartmentMapping mapping = new DepartmentMapping();

    @Test
    void oldCostCentersResolveUntilRestructure() {
        assertEquals(Integer.valueOf(2130), mapping.lookup("CC-200-5410", LocalDate.of(2026, 9, 30)));
        assertEquals(Integer.valueOf(2140), mapping.lookupForExport("CC-200-5420", "20260930"));
        assertNull(mapping.lookup("CC-200-5410", LocalDate.of(2026, 10, 1)));
        assertNull(mapping.lookupForExport("CC-200-5412", "20261008"));
    }

    @Test
    void restructuredCostCentersResolveFromEffectiveDate() {
        assertEquals(Integer.valueOf(2130), mapping.lookupForExport("CC-230-5410", "20261001"));
        assertEquals(Integer.valueOf(2131), mapping.lookupForExport("CC-230-5411", "20261008"));
        assertEquals(Integer.valueOf(2132), mapping.lookupForExport("CC-240-5412", "20261008"));
        assertEquals(Integer.valueOf(2140), mapping.lookupForExport(" CC-240-5420 ", "20261008"));
        assertNull(mapping.lookupForExport("CC-230-5410", "20260930"));
    }

    @Test
    void undatedLookupUsesLatestRow() {
        assertEquals(Integer.valueOf(2130), mapping.lookup("CC-230-5410"));
        assertEquals(Integer.valueOf(2130), mapping.lookup("CC-230-5410", null));
        assertNull(mapping.lookupForExport("CC-999-0000", "20261008"));
        assertTrue(mapping.knownCostCenters().contains("CC-240-5420"));
    }
}
