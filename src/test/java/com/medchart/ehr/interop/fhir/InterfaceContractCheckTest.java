package com.medchart.ehr.interop.fhir;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.medchart.ehr.interop.fhir.PractitionerDeduplicatorTest.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterfaceContractCheckTest {

    private final PractitionerDeduplicator dedup = new PractitionerDeduplicator();

    @Test
    void mergedPractitionerIsNotReportedAsDuplicate() {
        Map<String, List<String>> dups = InterfaceContractCheck.duplicatePractitioners(dedup.group(Arrays.asList(
                row("E104422", "Marquez", "Elena", "1932405817", ""),
                row("E119901", "MARQUEZ", "ELENA", "", "legacy record - see E104422"))));
        assertTrue(dups.isEmpty(), dups::toString);
    }

    @Test
    void sameNameWithoutHrIdentityIsReported() {
        Map<String, List<String>> dups = InterfaceContractCheck.duplicatePractitioners(dedup.group(Arrays.asList(
                row("E1", "Smith", "John", "1111111111", ""),
                row("E2", "SMITH", "JOHN", "", ""))));
        assertEquals(Arrays.asList("E1", "E2"), dups.get("SMITH JOHN"));
    }

    @Test
    void sameNpiUnderDifferentNamesIsMergedNotReported() {
        Map<String, List<String>> dups = InterfaceContractCheck.duplicatePractitioners(dedup.group(Arrays.asList(
                row("E1", "Okafor", "Samuel", "1750389264", ""),
                row("E2", "Okafor-Ade", "Samuel", "1750389264", ""))));
        assertTrue(dups.isEmpty(), dups::toString);
    }
}
