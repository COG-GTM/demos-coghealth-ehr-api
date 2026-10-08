package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DepartmentMappingTest {

    private final DepartmentMapping mapping = new DepartmentMapping();

    @Test
    void mapsKnownCostCenters() {
        assertEquals(Integer.valueOf(2130), mapping.lookup("CC-200-5410"));
        assertEquals(Integer.valueOf(2131), mapping.lookup("CC-200-5411"));
        assertEquals(Integer.valueOf(2132), mapping.lookup("CC-200-5412"));
        assertEquals(Integer.valueOf(2140), mapping.lookup(" CC-200-5420 "));
        assertEquals("Cardiac OR", mapping.departmentName(2132));
    }

    @Test
    void unknownCostCenterReturnsNull() {
        assertNull(mapping.lookup("CC-999-0000"));
        assertNull(mapping.lookup(null));
        assertEquals("", mapping.departmentName(9999));
    }
}
