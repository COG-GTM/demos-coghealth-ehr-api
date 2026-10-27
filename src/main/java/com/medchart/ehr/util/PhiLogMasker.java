package com.medchart.ehr.util;

/**
 * Redacts patient identifiers before they are written to application logs.
 * Prefer logging the internal patient id; use this only when no id is available.
 */
public final class PhiLogMasker {

    private PhiLogMasker() {
    }

    public static String maskMrn(String mrn) {
        return (mrn == null || mrn.isEmpty()) ? "<none>" : "<redacted>";
    }
}
