package com.medchart.ehr.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrExportReaderTest {

    private static final String HEADER =
            "employee_id,last_name,first_name,npi,cost_center,pto_start,pto_end,pto_type,status,notes";

    private final HrExportReader reader = new HrExportReader();

    @TempDir
    Path tempDir;

    @AfterEach
    void clearExportDir() {
        System.clearProperty(HrExportReader.DIR_PROPERTY);
    }

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
    void splitLineMatchesQuotedCsvSemantics() {
        assertArrayEquals(new String[] {"a", "\"b,c\"", "", "d"}, HrExportReader.splitLine("a,\"b,c\",,d"));
        assertArrayEquals(new String[] {"\"say \"\"hi\"\", ok\"", "x"},
                HrExportReader.splitLine("\"say \"\"hi\"\", ok\",x"));
        assertArrayEquals(new String[] {"", ""}, HrExportReader.splitLine(","));
        assertNull(HrExportReader.splitLine("a,\"b,c"));
    }

    @Test
    void splitLineRejectsTooManyFields() {
        char[] commas = new char[HrExportReader.MAX_FIELDS];
        Arrays.fill(commas, ',');
        assertNull(HrExportReader.splitLine(new String(commas)));
        assertEquals(HrExportReader.MAX_FIELDS, HrExportReader.splitLine(new String(commas, 1, commas.length - 1)).length);
    }

    @Test
    void splitsMegabyteLineWithOddQuoteCountInLinearTime() {
        String hostile = repeat(',', 1_000_000) + "\"";
        assertNull(assertTimeoutPreemptively(Duration.ofMillis(500), () -> HrExportReader.splitLine(hostile)));
    }

    @Test
    void readLineCapsBufferedCharactersAndHandlesAllLineEndings() throws IOException {
        StringReader in = new StringReader("abc\r\n" + repeat('x', 50) + "\rdef\nlast");
        java.io.BufferedReader br = new java.io.BufferedReader(in);
        StringBuilder buf = new StringBuilder();
        assertEquals(HrExportReader.OK, HrExportReader.readLine(br, buf, 10));
        assertEquals("abc", buf.toString());
        assertEquals(HrExportReader.TOO_LONG, HrExportReader.readLine(br, buf, 10));
        assertEquals(10, buf.length());
        assertEquals(HrExportReader.OK, HrExportReader.readLine(br, buf, 10));
        assertEquals("def", buf.toString());
        assertEquals(HrExportReader.OK, HrExportReader.readLine(br, buf, 10));
        assertEquals("last", buf.toString());
        assertEquals(HrExportReader.EOF, HrExportReader.readLine(br, buf, 10));
    }

    @Test
    void hostileExportFileIsSkippedQuicklyAndValidRowsSurvive() throws IOException {
        Path file = tempDir.resolve(HrExportReader.FILE_PREFIX + "20261101" + HrExportReader.FILE_SUFFIX);
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(HEADER + "\n");
            w.write("E104422,Marquez,Elena,1932405817,CC-200-5410,2026-11-02,2026-11-03,Vacation,Approved,\"ok, thanks\"\n");
            w.write(repeat(',', 1_000_000) + "\"\n");
            w.write(repeat(',', HrExportReader.MAX_LINE_LENGTH - 10) + "\"\n");
            w.write("E108913,Okafor,Samuel,1750389264,CC-200-5412,2026-11-04,,Vacation,Approved,\"unterminated\n");
            w.write("E109001,Raman,Priya,1497758221,CC-200-5410,0001-01-01,9999-12-31,Vacation,Approved,\r\n");
        }
        System.setProperty(HrExportReader.DIR_PROPERTY, tempDir.toString());

        List<HrPtoRecord> records = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> reader.read("20261101"));

        assertEquals(2, records.size());
        assertEquals("E104422", records.get(0).employeeId);
        assertEquals("ok, thanks", records.get(0).notes);
        assertEquals(2, records.get(0).lineNumber);
        assertEquals("E109001", records.get(1).employeeId);
        assertEquals("9999-12-31", records.get(1).ptoEnd);
        assertEquals(6, records.get(1).lineNumber);
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
