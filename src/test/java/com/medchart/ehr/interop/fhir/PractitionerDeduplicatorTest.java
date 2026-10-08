package com.medchart.ehr.interop.fhir;

import com.medchart.ehr.interop.fhir.PractitionerDeduplicator.PractitionerGroup;
import com.medchart.ehr.legacy.HrPtoRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PractitionerDeduplicatorTest {

    private final PractitionerDeduplicator dedup = new PractitionerDeduplicator();

    static HrPtoRecord row(String id, String last, String first, String npi, String notes) {
        HrPtoRecord r = new HrPtoRecord();
        r.employeeId = id;
        r.lastName = last;
        r.firstName = first;
        r.npi = npi;
        r.notes = notes;
        return r;
    }

    @Test
    void mergesBySameNpi() {
        List<PractitionerGroup> g = dedup.group(Arrays.asList(
                row("E1", "Okafor", "Samuel", "1750389264", ""),
                row("E2", "Okafor", "Sam", "1750389264", "")));
        assertEquals(1, g.size());
        assertEquals(Arrays.asList("E1", "E2"), g.get(0).employeeIds());
        assertEquals("1750389264", g.get(0).npi());
    }

    @Test
    void mergesByNormalizedNameAndHrCrossReference() {
        List<PractitionerGroup> g = dedup.group(Arrays.asList(
                row("E119901", "MARQUEZ", "ELENA", "", "legacy record - see E104422"),
                row("E104422", "Marquez", "Elena", "1932405817", "approved by dept admin 09/22")));
        assertEquals(1, g.size());
        assertEquals("E104422", g.get(0).primary().employeeId);
        assertEquals(Arrays.asList("E104422", "E119901"), g.get(0).employeeIds());
        assertEquals("MARQUEZ ELENA", g.get(0).normalizedName());
    }

    @Test
    void sameNameWithoutHrIdentityIsNotMerged() {
        List<PractitionerGroup> g = dedup.group(Arrays.asList(
                row("E1", "Smith", "John", "1111111111", ""),
                row("E2", "SMITH", "JOHN", "", "")));
        assertEquals(2, g.size());
    }

    @Test
    void crossReferenceWithDifferentNameIsNotMerged() {
        List<PractitionerGroup> g = dedup.group(Arrays.asList(
                row("E1", "Raman", "Priya", "1487296350", ""),
                row("E2", "Whitfield", "Daniel", "", "see E1")));
        assertEquals(2, g.size());
    }

    @Test
    void crossReferenceWithConflictingNpiIsNotMerged() {
        List<PractitionerGroup> g = dedup.group(Arrays.asList(
                row("E1", "Lee", "Ann", "1111111111", ""),
                row("E2", "Lee", "Ann", "2222222222", "see E1")));
        assertEquals(2, g.size());
    }

    @Test
    void normalizesNameAndCrossReference() {
        assertEquals("OBRIEN MARY ANN", PractitionerDeduplicator.normalizedName(row("E1", "O'Brien", " Mary  Ann", "", "")));
        assertEquals("E104422", PractitionerDeduplicator.crossReference(row("E2", "", "", "", "Legacy record - SEE e104422")));
        assertNull(PractitionerDeduplicator.crossReference(row("E3", "", "", "", "approved by dept admin")));
    }
}
