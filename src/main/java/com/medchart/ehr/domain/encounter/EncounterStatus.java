package com.medchart.ehr.domain.encounter;

/**
 * Workflow state of an encounter from scheduling through completion.
 */
public enum EncounterStatus {
    SCHEDULED,
    CHECKED_IN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
