package com.medchart.ehr.domain.order;

/**
 * Abnormality flag for a lab result relative to the reference range.
 */
public enum ResultFlag {
    NORMAL,
    LOW,
    HIGH,
    CRITICAL_LOW,
    CRITICAL_HIGH,
    ABNORMAL,
    POSITIVE,
    NEGATIVE
}
