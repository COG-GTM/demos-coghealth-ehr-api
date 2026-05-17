package com.medchart.ehr.domain.clinical;

/**
 * Verification and lifecycle status of a patient allergy record.
 */
public enum AllergyStatus {
    ACTIVE,
    INACTIVE,
    RESOLVED,
    REFUTED,
    ENTERED_IN_ERROR
}
