package com.medchart.ehr.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrExportReaderRulesTest {

    @TempDir
    Path dir;

    private final HrExportReader reader = new HrExportReader();

    @AfterEach
    void clear() {
        System.clearProperty(HrExportReader.DIR_PROPERTY);
    }

    private void write(String date, String body) throws Exception {
        Files.writeString(dir.resolve(HrExportReader.FILE_PREFIX + date + HrExportReader.FILE_SUFFIX), body);
    }

    @Test
    @DisplayName("BR-LEG-19: an export directory override lists only HR export files, sorted by date")
    void brLeg19_directoryOverride() throws Exception {
        System.setProperty(HrExportReader.DIR_PROPERTY, dir.toString());
        write("20261005", "h\n");
        write("20260901", "h\n");
        Files.writeString(dir.resolve("README.txt"), "x");
        Files.writeString(dir.resolve(HrExportReader.FILE_PREFIX + "x.txt"), "x");

        assertEquals(List.of("20260901", "20261005"), reader.listExportDates());
        assertEquals("20261005", reader.resolveExportDate("latest"));
        assertEquals("20261005", reader.resolveExportDate("${hr.export.date}"));
        assertEquals("20260901", reader.resolveExportDate(" 20260901 "));
    }

    @Test
    @DisplayName("BR-LEG-19: with no exports available the latest date cannot be resolved")
    void brLeg19_noExports() {
        System.setProperty(HrExportReader.DIR_PROPERTY, dir.resolve("missing").toString());
        assertTrue(reader.listExportDates().isEmpty());
        assertThrows(IllegalStateException.class, reader::latestExportDate);
        assertThrows(RuntimeException.class, () -> reader.read("20260101"));
    }

    @Test
    @DisplayName("BR-LEG-19: header, blank and malformed (<9 field) lines are skipped; quotes trimmed; notes optional")
    void brLeg19_parsingRules() throws Exception {
        System.setProperty(HrExportReader.DIR_PROPERTY, dir.toString());
        write("20261005", "employee_id,last,first,npi,cc,start,end,type,status,notes\n"
                + "\n"
                + "E1,Test,Short,row\n"
                + "E2,\"Test, Jr\",Surgeon,0000000000,CC-1,2026-10-12,,Vacation,Approved\n"
                + "E3,Test,\"\",,CC-1,10/12-10/13,,Sick,Pending,\"call x, y\"\n");

        List<HrPtoRecord> rows = reader.read("20261005");

        assertEquals(2, rows.size());
        assertEquals("Test, Jr", rows.get(0).lastName);
        assertEquals("", rows.get(0).notes);
        assertEquals(4, rows.get(0).lineNumber);
        assertEquals("", rows.get(1).firstName);
        assertEquals("call x, y", rows.get(1).notes);
    }

    @Test
    @DisplayName("BR-LEG-19: a missing bundled export is reported as an error naming the file")
    void brLeg19_missingClasspathExport() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> reader.read("19000101"));
        assertTrue(e.getMessage().contains("19000101"));
    }
}
