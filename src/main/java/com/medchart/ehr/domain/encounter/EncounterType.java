package com.medchart.ehr.domain.encounter;

/**
 * Classification of encounter setting (e.g., office visit, inpatient, telehealth).
 */
public enum EncounterType {
    OFFICE_VISIT,
    OUTPATIENT,
    INPATIENT,
    EMERGENCY,
    OBSERVATION,
    TELEHEALTH,
    HOME_HEALTH,
    AMBULATORY,
    DAY_SURGERY,
    LAB_ONLY,
    RADIOLOGY_ONLY
}
