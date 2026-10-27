package com.medchart.ehr.audit;

/**
 * Implemented by results that belong to a single patient, so {@link AuditAspect} can attribute
 * the access to that patient when the audited method is keyed by another id (e.g. an encounter id).
 */
public interface PatientScoped {

    Long getPatientId();
}
