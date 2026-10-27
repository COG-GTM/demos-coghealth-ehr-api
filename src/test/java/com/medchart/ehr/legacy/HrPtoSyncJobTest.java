package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrPtoSyncJobTest {

    @Test
    void parsesIsoSingleDay() {
        List<LocalDate> days = HrPtoSyncJob.parsePtoDays("2026-10-14", "2026-10-14", 2026);
        assertEquals(1, days.size());
        assertEquals(LocalDate.of(2026, 10, 14), days.get(0));
    }

    @Test
    void parsesFreeTextRange() {
        List<LocalDate> days = HrPtoSyncJob.parsePtoDays("10/13-10/17 vacation", "", 2026);
        assertEquals(5, days.size());
        assertEquals(LocalDate.of(2026, 10, 13), days.get(0));
        assertEquals(LocalDate.of(2026, 10, 17), days.get(4));
    }

    @Test
    void parsesFreeTextSingleDay() {
        List<LocalDate> days = HrPtoSyncJob.parsePtoDays("Thu 10/22, half day AM", "", 2026);
        assertEquals(1, days.size());
        assertEquals(LocalDate.of(2026, 10, 22), days.get(0));
        assertEquals("THU", HrPtoSyncJob.weekdayOf(days.get(0)));
    }

    @Test
    void buildsSiuS15ForBlock() {
        HrPtoRecord r = new HrPtoRecord();
        r.employeeId = "E104422";
        r.lastName = "Marquez";
        r.firstName = "Elena";
        r.npi = "1932405817";
        r.ptoStart = "10/13-10/17 vacation";
        r.ptoEnd = "";
        r.ptoType = "Vacation";
        OrBlock block = new OrBlock(1L, 2130, "TUE", "07:00", "15:00", "OR-4");

        String msg = HrPtoSyncJob.buildSiuS15(r, 2130, block, LocalDate.of(2026, 10, 13), "HRPTO2026100800001");
        String[] segments = msg.split("\r");

        assertTrue(segments[0].startsWith("MSH|^~\\&|COGHEALTH_EHR|HRPTO|IEPROD|OPTIME|"));
        assertTrue(segments[0].contains("||SIU^S15^SIU_S12|HRPTO2026100800001|P|2.5"));
        assertTrue(segments[1].startsWith("SCH|HRPTO2026100800001|"));
        assertTrue(segments[1].contains("^202610130700^202610131500"));
        assertTrue(segments[1].contains("|480|MIN|"));
        assertTrue(segments[3].startsWith("AIS|1||BLOCK^OR block^L|202610130700||480|MIN"));
        assertEquals("AIL|1||OR-4^^^2130|OR^Operating room", segments[4]);
        assertTrue(segments[5].startsWith("AIP|1||E104422^Marquez^Elena^^^Dr.^^^^^^^^NPI|SURGEON^Primary surgeon|202610130700||480|MIN"));
    }

    @Test
    void rejectsMultiCenturyIsoRangeWithoutExpandingIt() {
        InvalidPtoRangeException e = assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertThrows(InvalidPtoRangeException.class,
                        () -> HrPtoSyncJob.parsePtoDays("0001-01-01", "9999-12-31", 2026)));
        assertTrue(e.getMessage().contains("outside 2025-2027"), e.getMessage());
    }

    @Test
    void acceptsMaxSpanAndRejectsOneDayMore() {
        List<LocalDate> days = HrPtoSyncJob.parsePtoDays("2026-01-01", "2027-01-01", 2026);
        assertEquals(HrPtoSyncJob.MAX_PTO_DAYS, days.size());

        InvalidPtoRangeException e = assertThrows(InvalidPtoRangeException.class,
                () -> HrPtoSyncJob.parsePtoDays("2026-01-01", "2027-01-02", 2026));
        assertTrue(e.getMessage().contains("spans 367 days"), e.getMessage());
    }

    @Test
    void acceptsRangeCrossingIntoNextYear() {
        List<LocalDate> days = HrPtoSyncJob.parsePtoDays("2026-12-28", "2027-01-03", 2026);
        assertEquals(7, days.size());
    }

    @Test
    void rejectsReversedIsoRange() {
        assertThrows(InvalidPtoRangeException.class,
                () -> HrPtoSyncJob.parsePtoDays("2026-10-17", "2026-10-13", 2026));
    }

    @Test
    void rejectsInvalidIsoDate() {
        assertThrows(InvalidPtoRangeException.class,
                () -> HrPtoSyncJob.parsePtoDays("2026-13-45", "", 2026));
    }

    @Test
    void rejectsMalformedFreeText() {
        assertThrows(InvalidPtoRangeException.class,
                () -> HrPtoSyncJob.parsePtoDays("13/45 vacation", "", 2026));
        assertThrows(InvalidPtoRangeException.class,
                () -> HrPtoSyncJob.parsePtoDays("10/17-10/13 vacation", "", 2026));
        assertThrows(InvalidPtoRangeException.class,
                () -> HrPtoSyncJob.parsePtoDays("10/13-10/15-10/17", "", 2026));
    }
}
