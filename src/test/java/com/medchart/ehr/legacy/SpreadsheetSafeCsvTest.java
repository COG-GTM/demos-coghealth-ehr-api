package com.medchart.ehr.legacy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpreadsheetSafeCsvTest {

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1", "-1+cmd", "@SUM(A1)", "\t=1", " =1", "\u3000=1",
            "=HYPERLINK(\"http://evil.example\",\"x\")", "=cmd|' /C calc'!A0", "\uFF1D1+1"})
    void prefixesFormulaTriggers(String value) {
        String parsed = CsvTestParser.parse(SpreadsheetSafeCsv.cell(value)).get(0).get(0);
        assertEquals("'", parsed.substring(0, 1));
    }

    @Test
    void quotesEveryCellAndDoublesEmbeddedQuotes() {
        assertEquals("\"Smith, Jr.\",\"O\"\"Brien\",\"\"", SpreadsheetSafeCsv.row("Smith, Jr.", "O'Brien".replace('\'', '"'), null));
    }

    @Test
    void lineBreaksAndControlCharactersCannotStartANewRow() {
        String line = SpreadsheetSafeCsv.row("Doe\r\n999,=1+1", "a\nb\rc\u2028d", "x\u0000y\u001Bz");
        List<List<String>> rows = CsvTestParser.parse(line);
        assertEquals(1, rows.size());
        assertEquals(List.of("Doe 999,=1+1", "a b c d", "xyz"), rows.get(0));
    }

    @Test
    void leadingLineBreakIsNeutralisedAfterFlattening() {
        assertEquals("' =1+1", CsvTestParser.parse(SpreadsheetSafeCsv.cell("\n=1+1")).get(0).get(0));
    }

    @Test
    void numbersAndDatesAreWrittenUnchanged() {
        assertEquals("\"-42\",\"-1.50\",\"1980-01-02\",\"Smith\"",
                SpreadsheetSafeCsv.row(-42L, new BigDecimal("-1.50"), LocalDate.of(1980, 1, 2), "Smith"));
    }
}
