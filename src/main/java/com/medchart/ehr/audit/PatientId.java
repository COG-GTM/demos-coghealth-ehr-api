package com.medchart.ehr.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the {@code Long} parameter of an {@link AuditAccess} method that holds the patient ID.
 * Without it, the audit row is written with no patient ID.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PatientId {
}
