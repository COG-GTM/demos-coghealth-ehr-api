package com.medchart.ehr.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAccess {

    /** Resolve the patient id from the first {@code Long} argument. */
    int AUTO = -1;

    /** The method has no argument carrying this id. */
    int NONE = -2;

    AuditAction action() default AuditAction.READ;
    
    String resourceType();
    
    String description() default "";

    /** Index of the argument holding the patient id, or {@link #AUTO} / {@link #NONE}. */
    int patientIdArg() default AUTO;

    /** Index of the argument holding the audited resource id, or {@link #NONE}. */
    int resourceIdArg() default NONE;
}
