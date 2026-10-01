package com.medchart.ehr.domain.medication;

/**
 * DEA controlled-substance schedule classification (I–V) or non-controlled.
 */
public enum DrugSchedule {
    SCHEDULE_I,
    SCHEDULE_II,
    SCHEDULE_III,
    SCHEDULE_IV,
    SCHEDULE_V,
    NON_CONTROLLED
}
