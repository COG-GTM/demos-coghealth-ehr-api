package com.medchart.ehr.legacy;

import java.util.regex.Pattern;

/**
 * RFC 4180 field encoding with spreadsheet formula neutralisation (OWASP CSV injection guidance).
 */
public final class CsvFields {

    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]+");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");

    private CsvFields() {
    }

    public static String row(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) line.append(',');
            line.append(field(values[i]));
        }
        return line.toString();
    }

    public static String field(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (!(value instanceof Number)) {
            text = LINE_BREAKS.matcher(text).replaceAll(" ");
            text = CONTROL_CHARS.matcher(text).replaceAll("");
            text = neutraliseFormula(text);
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static String neutraliseFormula(String text) {
        if (text.isEmpty()) return text;
        if (text.charAt(0) == '\t' || isFormulaTrigger(text.stripLeading())) {
            return "'" + text;
        }
        return text;
    }

    private static boolean isFormulaTrigger(String text) {
        if (text.isEmpty()) return false;
        switch (text.charAt(0)) {
            case '=':
            case '+':
            case '-':
            case '@':
                return true;
            default:
                return false;
        }
    }
}
