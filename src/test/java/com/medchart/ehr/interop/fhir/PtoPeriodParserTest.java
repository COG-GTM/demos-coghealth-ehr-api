package com.medchart.ehr.interop.fhir;

import com.medchart.ehr.interop.fhir.PtoPeriodParser.PtoPeriod;
import com.medchart.ehr.interop.fhir.PtoPeriodParser.Source;
import com.medchart.ehr.legacy.HrPtoRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PtoPeriodParserTest {

    private static final LocalDate EXPORT_DAY = LocalDate.of(2026, 10, 8);

    private static HrPtoRecord row(String start, String end, String notes) {
        HrPtoRecord r = new HrPtoRecord();
        r.ptoStart = start;
        r.ptoEnd = end;
        r.notes = notes;
        return r;
    }

    @Test
    void isoColumnsSingleDay() {
        PtoPeriod p = PtoPeriodParser.parse(row("2026-10-14", "2026-10-14", ""), EXPORT_DAY);
        assertEquals("2026-10-14T00:00:00-04:00", p.startInstant());
        assertEquals("2026-10-14T23:59:59-04:00", p.endInstant());
        assertEquals(Source.COLUMNS, p.source);
    }

    @Test
    void isoColumnsUseNewYorkOffsetAcrossDst() {
        PtoPeriod p = PtoPeriodParser.parse(row("2026-10-30", "2026-11-02", ""), EXPORT_DAY);
        assertEquals("2026-10-30T00:00:00-04:00", p.startInstant());
        assertEquals("2026-11-02T23:59:59-05:00", p.endInstant());
    }

    @Test
    void isoStartWithoutEndIsSingleDay() {
        PtoPeriod p = PtoPeriodParser.parse(row("2026-10-14", "", ""), EXPORT_DAY);
        assertEquals("2026-10-14T23:59:59-04:00", p.endInstant());
    }

    @Test
    void freeTextRangeInStartColumn() {
        PtoPeriod p = PtoPeriodParser.parse(row("10/13-10/17 vacation", "", "approved by dept admin 09/22"), EXPORT_DAY);
        assertEquals("2026-10-13T00:00:00-04:00", p.startInstant());
        assertEquals("2026-10-17T23:59:59-04:00", p.endInstant());
        assertEquals(Source.COLUMNS, p.source);
    }

    @Test
    void freeTextHalfDayAmInStartColumn() {
        PtoPeriod p = PtoPeriodParser.parse(row("Thu 10/22, half day AM", "", "half day - clinic covered by locum"), EXPORT_DAY);
        assertEquals("2026-10-22T00:00:00-04:00", p.startInstant());
        assertEquals("2026-10-22T12:00:00-04:00", p.endInstant());
    }

    @Test
    void freeTextHalfDayPm() {
        PtoPeriod p = PtoPeriodParser.parse(row("Fri 10/23 half day PM", "", ""), EXPORT_DAY);
        assertEquals("2026-10-23T12:00:00-04:00", p.startInstant());
        assertEquals("2026-10-23T23:59:59-04:00", p.endInstant());
    }

    @Test
    void notesParsedOnlyWhenColumnsEmpty() {
        PtoPeriod p = PtoPeriodParser.parse(row("", "", "PTO 11/02-11/03 per dept admin"), EXPORT_DAY);
        assertEquals("2026-11-02T00:00:00-05:00", p.startInstant());
        assertEquals("2026-11-03T23:59:59-05:00", p.endInstant());
        assertEquals(Source.NOTES, p.source);
    }

    @Test
    void noPeriodWhenColumnsEmptyAndNotesHaveNoDate() {
        assertNull(PtoPeriodParser.parse(row("", "", "no approved time off in period"), EXPORT_DAY));
        assertNull(PtoPeriodParser.parse(row(null, null, null), EXPORT_DAY));
        assertNull(PtoPeriodParser.parse(row("pending", "", ""), EXPORT_DAY));
    }

    @Test
    void monthDayRollsIntoNextYear() {
        PtoPeriod p = PtoPeriodParser.parse(row("12/30-01/02 holiday", "", ""), LocalDate.of(2026, 12, 15));
        assertEquals("2026-12-30T00:00:00-05:00", p.startInstant());
        assertEquals("2027-01-02T23:59:59-05:00", p.endInstant());
        PtoPeriod jan = PtoPeriodParser.parse(row("01/05 vacation", "", ""), LocalDate.of(2026, 12, 15));
        assertEquals("2027-01-05T00:00:00-05:00", jan.startInstant());
    }
}
