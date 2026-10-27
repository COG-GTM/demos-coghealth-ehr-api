package com.medchart.ehr.legacy;

public final class PhiMasking {

    private PhiMasking() {
    }

    public static String maskSsn(Object ssn) {
        if (ssn == null) {
            return "";
        }
        String digits = ssn.toString().replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "***-**-****";
        }
        return "***-**-" + digits.substring(digits.length() - 4);
    }
}
