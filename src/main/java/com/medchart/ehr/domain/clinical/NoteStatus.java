package com.medchart.ehr.domain.clinical;

/**
 * Lifecycle status of a clinical note from draft through signature.
 */
public enum NoteStatus {
    DRAFT,
    PENDING_SIGNATURE,
    PENDING_COSIGN,
    SIGNED,
    AMENDED,
    ENTERED_IN_ERROR
}
