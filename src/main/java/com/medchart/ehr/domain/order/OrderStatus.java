package com.medchart.ehr.domain.order;

/**
 * Lifecycle status of a laboratory or diagnostic order.
 */
public enum OrderStatus {
    DRAFT,
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    ON_HOLD,
    ENTERED_IN_ERROR
}
