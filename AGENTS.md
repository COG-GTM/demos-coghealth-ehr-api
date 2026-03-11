# CogHealth EHR - Agent Instructions

## Overview

CogHealth EHR is a healthcare Electronic Health Record system. This codebase contains intentional HIPAA compliance gaps.

## Repository Structure

- `demos-coghealth-ehr-web` - React frontend (Vite, TypeScript, Tailwind)
- `demos-coghealth-ehr-api` - Spring Boot backend (Java 11, PostgreSQL)

## Critical: HIPAA Compliance

**Before modifying any code that handles patient data (PHI):**

1. Check `docs/PHI_DATA_FLOW.md` for data flow map
2. Add audit logging via `@AuditAccess` annotation or `PatientAccessLogger`
3. Never log SSN, full identifiers, or sensitive data
4. Update `HIPAA_COMPLIANCE.md` checklist if adding new data access

## Known HIPAA Issues

These are intentionally planted for HIPAA audit demo:

1. **PatientService.java:85** - Logs full SSN in error messages
2. **EncounterExportService.java** - No audit logging on batch exports
3. **LegacyPatientLookup.java** - Direct JDBC bypasses audit layer
4. **InsuranceCache.java** - Caches SSN with no TTL/expiration
5. **ReportGenerator.java** - Writes PHI to temp files without cleanup
6. **15+ endpoints** - Missing access logging

## Patterns to Follow

### 1. Audit Logging Pattern
See: `audit/PatientAccessLogger.java`
```java
@AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient")
public PatientDTO getPatient(Long id) { ... }
```

### 2. Insurance Eligibility Pattern
See: `service/AppointmentService.java`
- Check cache first
- Call external gateway on miss
- Cache result with TTL

### 3. Async Notification Pattern
See: `service/ProviderNotificationService.java`
- Use @Async for non-blocking
- Support multiple channels
- Handle failures gracefully

### 4. FHIR Mapping Pattern
See: `mapper/FhirPatientMapper.java`
- Map to/from FHIR R4 resources
- Handle identifier systems

### 5. External API Pattern
See: `service/InsuranceGateway.java`
- Circuit breaker for resilience
- Timeout handling
- Structured responses

## Chronic Medication Module

The chronic medication module is partially implemented. Key components:

### Domain Objects (Complete)
- `domain/chronic/ChronicCondition.java`
- `domain/chronic/MedicationAdherence.java`
- `domain/chronic/DiabetesManagement.java`

### Services (Stubs - Need Implementation)
- `service/chronic/ChronicMedicationService.java`
- `service/chronic/MedicationAdherenceTracker.java`
- `service/chronic/PharmacyIntegrationService.java`
- `service/chronic/MedicationNotificationService.java`

### Tasks for Implementation
1. Create repositories for chronic domain objects
2. Implement PDC calculation in MedicationAdherenceTracker
3. Implement care gap identification
4. Add NCPDP integration for pharmacy communication
5. Create REST endpoints for chronic care dashboard

## Build & Test

```bash
# Backend (uses shared Neon cloud database)
cd demos-coghealth-ehr-api
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd demos-coghealth-ehr-web
npm install
npm run dev
```

## Database

**Use the `dev` profile** - it connects to a shared Neon PostgreSQL database with pre-seeded test data. No local database setup required.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Schema managed by Flyway migrations in `src/main/resources/db/migration/`. Migrations run automatically on startup.

## Do Not

- Commit real PHI or PII
- Remove audit logging
- Bypass security controls
- Log sensitive identifiers (SSN, full DOB with name)
