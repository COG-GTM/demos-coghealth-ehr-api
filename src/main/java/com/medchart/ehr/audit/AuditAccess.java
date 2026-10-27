package com.medchart.ehr.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAccess {
    
    AuditAction action() default AuditAction.READ;
    
    String resourceType();
    
    String description() default "";

    /**
     * Whether the first {@code Long} argument is the patient id. Set to false when the method is
     * keyed by another id (encounter, provider); the patient is then taken from a
     * {@link PatientScoped} result, if any.
     */
    boolean patientIdFromArgs() default true;
}
