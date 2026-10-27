package com.medchart.ehr.audit;

/**
 * Masks patient identifiers before they are written to application logs.
 */
public final class PhiLogMasker {

    private static final int VISIBLE_SUFFIX = 4;
    private static final String MASK = "****";

    private PhiLogMasker() {
    }

    public static String maskMrn(String mrn) {
        if (mrn == null || mrn.isEmpty()) {
            return MASK;
        }
        String trimmed = mrn.trim();
        if (trimmed.length() <= VISIBLE_SUFFIX) {
            return MASK;
        }
        return MASK + trimmed.substring(trimmed.length() - VISIBLE_SUFFIX);
    }
}
