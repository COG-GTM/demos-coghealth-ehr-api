package com.medchart.ehr.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HIPAA-compliant audit annotation for tracking access to Protected Health Information (PHI).
 *
 * Applied to service methods that access, modify, or export patient data.
 * Intercepted by {@link AuditAspect} to create immutable audit trail entries.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAccess {
    
    AuditAction action() default AuditAction.READ;
    
    String resourceType();
    
    String description() default "";
}
