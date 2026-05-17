package com.medchart.ehr.domain.encounter;

/**
 * Role a diagnosis plays within an encounter (e.g., admission, billing, chief complaint).
 */
public enum DiagnosisRole {
    ADMISSION,
    DISCHARGE,
    BILLING,
    CHIEF_COMPLAINT,
    COMORBIDITY,
    COMPLICATION,
    REASON_FOR_VISIT
}
