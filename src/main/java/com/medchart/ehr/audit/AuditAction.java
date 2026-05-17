package com.medchart.ehr.audit;

/**
 * Enumeration of auditable actions for HIPAA-compliant access tracking.
 *
 * Each action maps to an operation type recorded in the audit trail,
 * including standard CRUD operations and healthcare-specific actions
 * such as emergency access and break-the-glass overrides.
 */
public enum AuditAction {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    SEARCH,
    EXPORT,
    PRINT,
    LOGIN,
    LOGOUT,
    ACCESS_DENIED,
    EMERGENCY_ACCESS,
    BREAK_THE_GLASS
}
