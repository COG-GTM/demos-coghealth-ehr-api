package com.medchart.ehr.legacy;

import java.util.StringJoiner;

/**
 * RFC 4180 CSV encoding with spreadsheet formula neutralisation (OWASP CSV injection guidance):
 * every cell is quoted, embedded quotes are doubled, and cells starting with
 * {@code = + - @ TAB CR LF} (also after leading spaces or control characters, which some
 * importers trim) are prefixed with a single quote so spreadsheets treat them as text.
 */
public final class CsvEncoder {

    private CsvEncoder() {
    }

    public static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        if (startsWithFormula(text)) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    public static String row(Object... values) {
        StringJoiner joiner = new StringJoiner(",");
        for (Object value : values) {
            joiner.add(cell(value));
        }
        return joiner.toString();
    }

    private static boolean startsWithFormula(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isFormulaTrigger(c)) {
                return true;
            }
            if (!Character.isWhitespace(c) && !Character.isISOControl(c) && !Character.isSpaceChar(c)) {
                return false;
            }
        }
        return false;
    }

    private static boolean isFormulaTrigger(char c) {
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r' || c == '\n';
    }
}
