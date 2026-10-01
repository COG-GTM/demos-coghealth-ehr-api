package com.medchart.ehr.domain.medication;

/**
 * Status of a single medication administration event.
 */
public enum AdministrationStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    NOT_GIVEN,
    ON_HOLD,
    CANCELLED,
    ENTERED_IN_ERROR
}
