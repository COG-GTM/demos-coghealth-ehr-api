package com.medchart.ehr.service;

import com.medchart.ehr.domain.patient.IdentifierType;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.dto.PatientIdentifierDTO;

/**
 * Masks SSN identifier values in patient API responses to the last four digits.
 */
public final class PatientIdentifierMasker {

    private PatientIdentifierMasker() {
    }

    public static PatientDTO mask(PatientDTO patient) {
        if (patient != null && patient.getIdentifiers() != null) {
            for (PatientIdentifierDTO identifier : patient.getIdentifiers()) {
                if (isSsn(identifier)) {
                    identifier.setIdentifierValue(maskSsn(identifier.getIdentifierValue()));
                }
            }
        }
        return patient;
    }

    public static String maskSsn(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.length() >= 4 ? "***-**-" + digits.substring(digits.length() - 4) : "***-**-****";
    }

    public static boolean isMaskedSsn(PatientIdentifierDTO identifier) {
        return isSsn(identifier)
            && identifier.getIdentifierValue() != null
            && identifier.getIdentifierValue().indexOf('*') >= 0;
    }

    static boolean isSsn(PatientIdentifierDTO identifier) {
        return identifier != null && identifier.getIdentifierType() == IdentifierType.SSN;
    }
}
