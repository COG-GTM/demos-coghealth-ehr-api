package com.medchart.ehr.domain.medication;

/**
 * Lifecycle status of a medication order from draft through completion.
 */
public enum MedicationOrderStatus {
    DRAFT,
    PENDING,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    CANCELLED,
    DISCONTINUED,
    ENTERED_IN_ERROR
}
