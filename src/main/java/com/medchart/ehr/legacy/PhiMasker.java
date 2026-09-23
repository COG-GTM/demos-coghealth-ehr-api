package com.medchart.ehr.legacy;

/**
 * Masks sensitive identifiers before they leave the system in exports or reports.
 */
public final class PhiMasker {

    private static final String REDACTED = "REDACTED";

    private PhiMasker() {
    }

    /**
     * Renders a Social Security Number as {@code ***-**-1234}.
     */
    public static String maskSsn(Object ssn) {
        String digits = digitsOf(ssn);
        if (digits.length() < 4) {
            return REDACTED;
        }
        return "***-**-" + digits.substring(digits.length() - 4);
    }

    /**
     * Renders any other identifier (insurance member id, policy number, ...) keeping only its last 4 characters.
     */
    public static String maskIdentifier(Object identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.toString().trim();
        if (value.length() < 4) {
            return value.isEmpty() ? "" : REDACTED;
        }
        return "****" + value.substring(value.length() - 4);
    }

    private static String digitsOf(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().replaceAll("\\D", "");
    }
}
