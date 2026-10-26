package com.medchart.ehr.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the {@code Long} parameter of an {@link AuditAccess} method that holds the patient id.
 * {@link AuditAspect} records only explicitly marked arguments; it never guesses.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditPatientId {
}
