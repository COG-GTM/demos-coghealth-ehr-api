package com.medchart.ehr.legacy;

import java.util.StringJoiner;

/**
 * RFC 4180 CSV encoding with spreadsheet formula neutralisation (OWASP CSV injection guidance):
 * every cell is quoted, embedded quotes are doubled, and cells starting with
 * {@code = + - @ TAB CR} are prefixed with a single quote so spreadsheets treat them as text.
 */
public final class CsvEncoder {

    private CsvEncoder() {
    }

    public static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && isFormulaTrigger(text.charAt(0))) {
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

    private static boolean isFormulaTrigger(char c) {
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
    }
}
