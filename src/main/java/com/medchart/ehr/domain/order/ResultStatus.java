package com.medchart.ehr.domain.order;

/**
 * Review status of a lab result from pending through final verification.
 */
public enum ResultStatus {
    PENDING,
    PRELIMINARY,
    FINAL,
    CORRECTED,
    CANCELLED,
    ENTERED_IN_ERROR
}
