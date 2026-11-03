package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void resolveExportDateRejectsTraversalThatCollapsesOntoRealExport() {
        assertThrows(InvalidExportDateException.class,
                () -> reader.resolveExportDate("2026,x/../workday_pto_export_20261008"));
        assertThrows(InvalidExportDateException.class,
                () -> reader.resolveExportDate("2026,x:y/../workday_pto_export_20261008"));
    }

    @Test
    void resolveExportDateRejectsNonCalendarValues() {
        for (String bad : Arrays.asList("2026-10-08", "202610081", "2026100", "20261332", "20260230",
                "../20261008", "20261008/../x", "abcdefgh")) {
            assertThrows(InvalidExportDateException.class, () -> reader.resolveExportDate(bad), bad);
        }
    }

    @Test
    void readRejectsInvalidExportDateBeforeOpeningFile() {
        assertThrows(InvalidExportDateException.class,
                () -> reader.read("2026,x:y/../workday_pto_export_20261008"));
    }

    @Test
    void listExportDatesIgnoresFilesWithoutYyyyMmDdDate(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("workday_pto_export_20261001.csv"), "h\n".getBytes());
        Files.write(dir.resolve("workday_pto_export_2026,x.csv"), "h\n".getBytes());
        Files.write(dir.resolve("workday_pto_export_backup.csv"), "h\n".getBytes());
        System.setProperty(HrExportReader.DIR_PROPERTY, dir.toString());
        try {
            assertEquals(Arrays.asList("20261001"), reader.listExportDates());
        } finally {
            System.clearProperty(HrExportReader.DIR_PROPERTY);
        }
    }
}
