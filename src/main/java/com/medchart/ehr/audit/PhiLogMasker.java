package com.medchart.ehr.audit;

/**
 * Masks patient identifiers before they are written to application logs.
 */
public final class PhiLogMasker {

    private static final int VISIBLE_SUFFIX = 4;
    private static final int MIN_LENGTH_FOR_SUFFIX = 2 * VISIBLE_SUFFIX;
    private static final String MASK = "****";

    private PhiLogMasker() {
    }

    public static String maskMrn(String mrn) {
        if (mrn == null || mrn.isEmpty()) {
            return MASK;
        }
        String trimmed = mrn.trim();
        if (trimmed.length() < MIN_LENGTH_FOR_SUFFIX) {
            return MASK;
        }
        return MASK + trimmed.substring(trimmed.length() - VISIBLE_SUFFIX);
    }
}
