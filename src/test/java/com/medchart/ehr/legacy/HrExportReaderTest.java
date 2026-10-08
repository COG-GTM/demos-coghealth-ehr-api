package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrExportReaderTest {

    private final HrExportReader reader = new HrExportReader();

    @Test
    void listsExportDatesSorted() {
        List<String> dates = reader.listExportDates();
        assertTrue(dates.size() >= 2);
        for (int i = 1; i < dates.size(); i++) {
            assertTrue(dates.get(i - 1).compareTo(dates.get(i)) < 0, "dates not sorted: " + dates);
        }
        assertEquals(dates.get(dates.size() - 1), reader.latestExportDate());
    }

    @Test
    void resolveExportDateDefaultsToLatest() {
        assertEquals(reader.latestExportDate(), reader.resolveExportDate(null));
        assertEquals(reader.latestExportDate(), reader.resolveExportDate(""));
        assertEquals(reader.latestExportDate(), reader.resolveExportDate("latest"));
        assertEquals("20260930", reader.resolveExportDate(" 20260930 "));
    }

    @Test
    void readsAllColumnsIncludingQuotedField() {
        List<HrPtoRecord> records = reader.read("20260930");
        assertEquals(4, records.size());

        HrPtoRecord marquez = records.get(0);
        assertEquals("E104422", marquez.employeeId);
        assertEquals("Marquez", marquez.lastName);
        assertEquals("1932405817", marquez.npi);
        assertEquals("CC-200-5410", marquez.costCenter);
        assertEquals("10/13-10/17 vacation", marquez.ptoStart);
        assertTrue(marquez.hasPto());
        assertTrue(marquez.isApproved());

        HrPtoRecord raman = records.get(2);
        assertEquals("Thu 10/22, half day AM", raman.ptoStart);
        assertEquals("Approved", raman.status);
        assertEquals("half day - clinic covered by locum", raman.notes);

        HrPtoRecord whitfield = records.get(3);
        assertFalse(whitfield.hasPto());
        assertEquals("", whitfield.status);
    }
}
