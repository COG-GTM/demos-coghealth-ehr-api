package com.medchart.ehr.domain.patient;

/**
 * Types of patient identifiers used across the EHR system.
 *
 * Includes government-issued IDs (SSN, driver's license, passport)
 * and healthcare-specific identifiers (MRN, insurance member/group IDs).
 */
public enum IdentifierType {
    MRN,
    SSN,
    DRIVERS_LICENSE,
    PASSPORT,
    INSURANCE_MEMBER_ID,
    INSURANCE_GROUP_ID,
    MEDICARE_ID,
    MEDICAID_ID,
    MILITARY_ID,
    OTHER
}
