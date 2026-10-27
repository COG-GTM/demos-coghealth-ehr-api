package com.medchart.ehr.config;

/**
 * {@code @PreAuthorize} expressions for patient records, enforced per method so they
 * hold regardless of the URL rules in {@link SecurityConfig}.
 */
public final class PatientAccess {

    public static final String READ = "hasAnyRole('PROVIDER','STAFF','ADMIN')";
    public static final String WRITE = "hasAnyRole('PROVIDER','ADMIN')";

    private PatientAccess() {
    }
}
