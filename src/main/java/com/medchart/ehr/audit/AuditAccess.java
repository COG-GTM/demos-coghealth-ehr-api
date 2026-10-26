package com.medchart.ehr.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Audits a method call. The patient reference is taken from parameters marked with
 * {@link AuditPatientId} / {@link AuditPatientMrn}, and, when {@link #patientFromResult()}
 * is set, from a returned patient (e.g. lookups by MRN, creates).
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAccess {
    
    AuditAction action() default AuditAction.READ;
    
    String resourceType();
    
    String description() default "";

    boolean patientFromResult() default false;
}
