package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvFieldsTest {

    @Test
    void quotesEveryFieldAndDoublesEmbeddedQuotes() {
        assertEquals("\"Smith, Jr.\"", CsvFields.field("Smith, Jr."));
        assertEquals("\"O\"\"Brien\"", CsvFields.field("O\"Brien"));
        assertEquals("", CsvFields.field(null));
    }

    @Test
    void neutralisesFormulaTriggers() {
        assertEquals("\"'=1+1\"", CsvFields.field("=1+1"));
        assertEquals("\"'+15551234\"", CsvFields.field("+15551234"));
        assertEquals("\"'-2+3\"", CsvFields.field("-2+3"));
        assertEquals("\"'@SUM(A1:A9)\"", CsvFields.field("@SUM(A1:A9)"));
        assertEquals("\"'\tcmd\"", CsvFields.field("\tcmd"));
        assertEquals("\" cmd\"", CsvFields.field("\rcmd"));
        assertEquals("\"' =1+1\"", CsvFields.field(" =1+1"));
        assertEquals("\"' =1+1\"", CsvFields.field("\r=1+1"));
        assertEquals("\"'=HYPERLINK(1)\"", CsvFields.field("\u001b=HYPERLINK(1)"));
        assertEquals("\"'=1\"", CsvFields.field("\u0000\u0007=1"));
        assertEquals("\"'=HYPERLINK(\"\"http://x/?\"\"&C2)\"",
                CsvFields.field("=HYPERLINK(\"http://x/?\"&C2)"));
    }

    @Test
    void replacesLineBreaksAndStripsControlCharacters() {
        assertEquals("\"Ann 999,MRN,123-45-6789\"", CsvFields.field("Ann\r\n999,MRN,123-45-6789"));
        assertEquals("\"AnnB\"", CsvFields.field("Ann\u0000\u001bB"));
    }

    @Test
    void leavesNumbersUnprefixed() {
        assertEquals("\"-5\"", CsvFields.field(-5));
        assertEquals("\"-1.50\"", CsvFields.field(new BigDecimal("-1.50")));
    }

    @Test
    void joinsRow() {
        assertEquals("\"1\",,\"'=1+1\"", CsvFields.row(1L, null, "=1+1"));
    }
}
