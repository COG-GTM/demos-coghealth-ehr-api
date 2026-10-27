package com.medchart.ehr.util;

/**
 * Masks patient identifiers before they are written to application logs.
 * Prefer logging the internal patient id; use this only when no id is available.
 */
public final class PhiLogMasker {

    private static final int VISIBLE_SUFFIX = 4;
    private static final int MIN_LENGTH_FOR_SUFFIX = 8;

    private PhiLogMasker() {
    }

    public static String maskMrn(String mrn) {
        if (mrn == null || mrn.isEmpty()) {
            return "<none>";
        }
        if (mrn.length() < MIN_LENGTH_FOR_SUFFIX) {
            return "****";
        }
        String suffix = mrn.substring(mrn.length() - VISIBLE_SUFFIX);
        if (!suffix.chars().allMatch(Character::isLetterOrDigit)) {
            return "****";
        }
        return "****" + suffix;
    }
}
