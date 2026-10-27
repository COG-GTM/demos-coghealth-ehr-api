package com.medchart.ehr.legacy;

import java.util.regex.Pattern;

/**
 * RFC 4180 CSV encoding that is safe to open in spreadsheet applications (OWASP CSV injection guidance):
 * every cell is quoted with embedded quotes doubled, line breaks and other control characters are
 * replaced so each record stays on one line, and text cells that a spreadsheet would evaluate as a
 * formula are prefixed with a single quote.
 */
public final class SpreadsheetSafeCsv {

    private static final Pattern LINE_BREAKS = Pattern.compile("\\r\\n|[\\r\\n\\u0085\\u2028\\u2029]");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");

    private SpreadsheetSafeCsv() {
    }

    public static String row(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(cell(values[i]));
        }
        return line.toString();
    }

    public static String cell(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String text = value.toString();
        if (!(value instanceof Number)) {
            text = LINE_BREAKS.matcher(text).replaceAll(" ");
            text = CONTROL_CHARS.matcher(text).replaceAll("");
            if (startsLikeFormula(text)) {
                text = "'" + text;
            }
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static boolean startsLikeFormula(String text) {
        if (text.isEmpty()) {
            return false;
        }
        if (text.charAt(0) == '\t') {
            return true;
        }
        String trimmed = text.stripLeading();
        if (trimmed.isEmpty()) {
            return false;
        }
        switch (trimmed.charAt(0)) {
            case '=':
            case '+':
            case '-':
            case '@':
            case '\uFF1D':
            case '\uFF0B':
            case '\uFF0D':
            case '\uFF20':
                return true;
            default:
                return false;
        }
    }
}
