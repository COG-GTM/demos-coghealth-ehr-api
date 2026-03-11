# PHI Data Flow Map

## Overview

This document provides a hierarchical view of Protected Health Information (PHI) flow through the CogHealth EHR system. Use this map to understand how patient data enters, moves through, and exits the system.

**HIPAA Requirement**: All PHI access must be logged. See `PatientAccessLogger.java` for the audit logging pattern.

---

## Hierarchical PHI Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ENTRY POINTS                                    │
│  (Where PHI enters the system)                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  REST APIs                          HL7 Interfaces        File Imports      │
│  ├── PatientController.java         ├── (planned)         ├── (planned)    │
│  ├── EncounterController.java       └── ADT feeds         └── CSV imports  │
│  ├── ProviderController.java                                                │
│  └── LegacyExportController.java ✓ Via Service @AuditAccess                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PROCESSING                                      │
│  (Services that transform or access PHI)                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  WITH Audit Logging ✓                                                        │
│  ├── PatientService.java            ├── LegacyPatientLookup.java ✓        │
│  ├── EncounterService.java ✓        ├── EncounterExportService.java ✓      │
│  ├── ProviderService.java ✓         ├── ReportGenerator.java ✓             │
│  ├── AppointmentService.java        └── InsuranceCache.java ✓ (TTL added) │
│  └── ProviderNotificationService                                            │
│                                                                             │
│  PATTERNS TO FOLLOW:                                                        │
│  • @AuditAccess annotation on all PHI methods                              │
│  • PatientAccessLogger for manual logging                                   │
│  • Never log SSN or full identifiers                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              STORAGE                                         │
│  (Where PHI is persisted)                                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Database Entities                  Caches                 Temp Files       │
│  ├── Patient.java                   ├── InsuranceCache ✓   ├── ReportGen ✓│
│  │   └── SSN, DOB, Address          │   └── SSN removed    │   └── 24h TTL │
│  ├── Encounter.java                 │   └── 24h TTL ✓      │   └── Auto    │
│  ├── InsuranceCoverage.java         │                      │       cleanup │
│  ├── MedicationOrder.java           │                      │               │
│  └── ClinicalNote.java              │                      │               │
│                                                                             │
│  RESOLVED:                                                                  │
│  ✓ InsuranceCache: SSN removed, 24-hour TTL added                          │
│  ✓ ReportGenerator: Temp files cleaned up after use                        │
│  ⚠️ No encryption at rest configured                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EXIT POINTS                                     │
│  (Where PHI leaves the system)                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  API Responses                      Reports                Integrations     │
│  ├── PatientDTO.java                ├── EncounterExport ✓  ├── InsuranceGW │
│  │   └── Excludes SSN ✓             │   └── SSN removed ✓  ├── FHIR APIs   │
│  ├── EncounterDTO.java              ├── PatientRoster ✓    └── (planned)   │
│  └── ProviderDTO.java               │   └── SSN removed ✓                  │
│                                     └── DailyReport ✓                       │
│                                                                             │
│  RESOLVED:                                                                  │
│  ✓ EncounterExportService: SSN removed from CSV exports                    │
│  ✓ ReportGenerator: SSN removed from patient roster                        │
│  ✓ Audit logging added to all bulk exports via @AuditAccess                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## File Reference (Click to Navigate)

### Entry Points
| File | Location | Audit Status |
|------|----------|--------------|
| PatientController | `controller/PatientController.java:24` | ✓ Via Service |
| EncounterController | `controller/EncounterController.java:20` | ✓ Via Service |
| ProviderController | `controller/ProviderController.java:18` | ✓ Via Service |
| LegacyExportController | `controller/LegacyExportController.java:15` | ✓ Via Service @AuditAccess |

### Processing Services
| File | Location | Audit Status | Issues |
|------|----------|--------------|--------|
| PatientService | `service/PatientService.java:23` | ✓ @AuditAccess | None (SSN logging removed) |
| EncounterService | `service/EncounterService.java:22` | ✓ @AuditAccess | None |
| ProviderService | `service/ProviderService.java:15` | ✓ @AuditAccess | None |
| AppointmentService | `service/AppointmentService.java:25` | ✓ @AuditAccess | None |
| LegacyPatientLookup | `legacy/LegacyPatientLookup.java:16` | ✓ @AuditAccess | SSN lookup disabled |
| EncounterExportService | `legacy/EncounterExportService.java:19` | ✓ @AuditAccess | SSN removed from exports |
| ReportGenerator | `legacy/ReportGenerator.java:18` | ✓ @AuditAccess | Temp cleanup added, SSN removed |
| InsuranceCache | `legacy/InsuranceCache.java:12` | ✓ TTL added | SSN removed, 24h expiration |

### Storage
| File | Location | PHI Fields | Encryption |
|------|----------|------------|------------|
| Patient | `domain/patient/Patient.java:20` | SSN, DOB, Address | ⚠️ None |
| InsuranceCoverage | `domain/insurance/InsuranceCoverage.java:15` | Member ID | ⚠️ None |
| ClinicalNote | `domain/clinical/ClinicalNote.java:18` | Note content | ⚠️ None |

### Exit Points
| File | Location | PHI Exposed | Issues |
|------|----------|-------------|--------|
| PatientDTO | `dto/PatientDTO.java:10` | Name, DOB, MRN | ✓ SSN excluded |
| EncounterExportService | `legacy/EncounterExportService.java:26` | Name, DOB, MRN | ✓ SSN excluded |
| ReportGenerator | `legacy/ReportGenerator.java:27` | Name, DOB, MRN | ✓ SSN excluded |
| FhirPatientMapper | `mapper/FhirPatientMapper.java:30` | All demographics | ✓ Standard format |

---

## HIPAA Compliance Checklist

### Critical Issues (Resolved)

- [x] **PatientService.java** - SSN-based lookup disabled, throws UnsupportedOperationException
- [x] **EncounterExportService.java** - @AuditAccess added to all export methods, SSN removed from exports
- [x] **LegacyPatientLookup.java** - @AuditAccess added to all methods, SSN lookup disabled, SSN removed from demographics
- [x] **InsuranceCache.java** - SSN removed from cache, 24-hour TTL added with auto-eviction
- [x] **ReportGenerator.java** - @AuditAccess added, SSN removed from reports, temp file cleanup added
- [x] **EncounterService.java** - @AuditAccess added to all 17 PHI methods
- [x] **ProviderService.java** - @AuditAccess added to all PHI methods
- [x] **AppointmentService.java** - SSN removed from eligibility caching, TTL check fixed
- [x] **InsuranceGateway.java** - SSN removed from mock eligibility response

### Patterns to Follow

1. **Audit Logging Pattern** - See `PatientAccessLogger.java`
2. **Insurance Eligibility Pattern** - See `AppointmentService.java`
3. **Async Notification Pattern** - See `ProviderNotificationService.java`
4. **FHIR Mapping Pattern** - See `FhirPatientMapper.java`
5. **External API Pattern** - See `InsuranceGateway.java`

---

## Remediation Guide

### Adding Audit Logging to Legacy Services

```java
// BEFORE (no audit)
public Patient findPatientByMrn(String mrn) {
    Query query = entityManager.createNativeQuery(...);
    return (Patient) query.getSingleResult();
}

// AFTER (with audit)
@Autowired
private PatientAccessLogger accessLogger;

public Patient findPatientByMrn(String mrn, Long userId, String userRole) {
    Patient patient = // ... query logic
    
    accessLogger.logAccess(
        userId, userRole,
        patient.getId(), patient.getMrn(),
        "READ", "Patient",
        "Legacy MRN lookup",
        RequestContextHolder.getIpAddress(),
        RequestContextHolder.getSessionId()
    );
    
    return patient;
}
```

### Removing SSN from Logs

```java
// BEFORE (logs SSN - HIPAA violation)
log.error("Error finding patient by SSN: " + ssn, e);

// AFTER (masks SSN)
log.error("Error finding patient by SSN: XXX-XX-{}", ssn.substring(ssn.length() - 4), e);
```

### Adding Cache TTL

```java
// BEFORE (no expiration)
eligibilityCache.put(cacheKey, cached);

// AFTER (24-hour TTL)
eligibilityCache.put(cacheKey, cached, 24, TimeUnit.HOURS);
```
