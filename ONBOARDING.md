# CogHealth EHR API - Developer Onboarding

This document covers the backend API repository in detail. For the full cross-repo system overview, see the [ONBOARDING.md in demos-coghealth-ehr-data](https://github.com/COG-GTM/demos-coghealth-ehr-data/blob/main/ONBOARDING.md).

---

## Table of Contents

1. [Overview](#overview)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [Getting Started](#getting-started)
5. [Configuration](#configuration)
6. [Domain Model](#domain-model)
7. [API Endpoints](#api-endpoints)
8. [Service Layer](#service-layer)
9. [Audit Logging System](#audit-logging-system)
10. [Healthcare Standards (FHIR / HL7)](#healthcare-standards-fhir--hl7)
11. [Legacy Module (Intentional HIPAA Gaps)](#legacy-module-intentional-hipaa-gaps)
12. [Database Migrations (Flyway)](#database-migrations-flyway)
13. [Build & Test](#build--test)
14. [CI Pipeline](#ci-pipeline)
15. [Related Repositories](#related-repositories)

---

## Overview

`demos-coghealth-ehr-api` is the Spring Boot backend for the CogHealth EHR demo application. It provides a REST API consumed by the React frontend (`demos-coghealth-ehr-web`). Infrastructure services (PostgreSQL, Redis, Keycloak, etc.) are defined in `demos-coghealth-ehr-data`.

**Key facts:**
- Spring Boot 2.7.18 on Java 11
- Context path: `/api` (all endpoints are under `http://localhost:8080/api/`)
- Stateless (no server-side sessions; JWT-based auth intended for production)
- AOP-based HIPAA audit logging
- FHIR R4 and HL7 v2.x integration support
- Flyway for database schema management
- Swagger/OpenAPI documentation auto-generated

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 11 | Language runtime |
| Spring Boot | 2.7.18 | Application framework |
| Spring Data JPA | (managed) | ORM / database access via Hibernate |
| Spring Security | (managed) | Auth framework (disabled for dev) |
| Spring AOP | (managed) | Aspect-oriented audit logging |
| Flyway | (managed) | Database migration management |
| PostgreSQL | 14 | Primary database |
| Redis | 7 | Caching and session store |
| Lombok | 1.18.36 | Boilerplate reduction (@Data, @Builder, etc.) |
| MapStruct | 1.5.5 | Compile-time type-safe DTO mapping |
| SpringDoc OpenAPI | 1.7.0 | Swagger UI / API documentation |
| HAPI HL7 | 2.3 | HL7 v2.x message parsing |
| Maven | (wrapper) | Build and dependency management |

---

## Project Structure

```
src/main/java/com/medchart/ehr/
├── MedchartEhrApplication.java        # @SpringBootApplication entry point
│
├── config/
│   └── SecurityConfig.java            # CORS configuration, auth disabled for dev
│                                       # Allows: localhost:5173, 5178, 3000
│                                       # All endpoints permitAll() in dev mode
│
├── controller/                         # REST API layer
│   ├── PatientController.java         # /v1/patients - CRUD + search
│   ├── EncounterController.java       # /v1/encounters - full encounter lifecycle
│   ├── ProviderController.java        # /v1/providers - CRUD + search + lookup
│   └── LegacyExportController.java    # /v1/export - CSV exports & reports
│
├── service/                            # Business logic layer
│   ├── PatientService.java            # Patient CRUD with audit logging
│   ├── EncounterService.java          # Encounter state machine & queries
│   ├── ProviderService.java           # Provider management & search
│   ├── AppointmentService.java        # Scheduling + insurance eligibility check
│   ├── InsuranceGateway.java          # External insurance API (circuit breaker)
│   ├── ProviderNotificationService.java # @Async provider notifications
│   └── chronic/                        # Chronic care module (stubs)
│       ├── ChronicMedicationService.java
│       ├── MedicationAdherenceTracker.java
│       ├── PharmacyIntegrationService.java
│       └── MedicationNotificationService.java
│
├── domain/                             # JPA entities organized by bounded context
│   ├── patient/                        # Patient, Address, EmergencyContact,
│   │                                   # PatientIdentifier, Gender, MaritalStatus,
│   │                                   # IdentifierType enums
│   ├── encounter/                      # Encounter, EncounterDiagnosis,
│   │                                   # EncounterType, EncounterStatus
│   ├── clinical/                       # ClinicalNote, Allergy, Diagnosis, Vitals,
│   │                                   # NoteType, NoteStatus, AllergySeverity
│   ├── medication/                     # Medication, MedicationOrder,
│   │                                   # MedicationAdministration, DrugSchedule,
│   │                                   # MedicationOrderStatus
│   ├── order/                          # LabOrder, LabResult, OrderStatus,
│   │                                   # OrderPriority, ResultFlag
│   ├── insurance/                      # InsuranceCoverage, CoverageType
│   ├── provider/                       # Provider, ProviderLicense, ProviderType
│   └── chronic/                        # ChronicCondition, DiabetesManagement,
│                                       # MedicationAdherence
│
├── dto/                                # Data Transfer Objects
│   ├── PatientDTO.java                # Patient DTO (full demographics)
│   ├── AddressDTO.java                # Address DTO
│   ├── EmergencyContactDTO.java       # Emergency contact DTO
│   └── PatientIdentifierDTO.java      # Patient identifier DTO
│
├── repository/                         # Spring Data JPA repositories
│   ├── PatientRepository.java         # Custom search query: searchPatients()
│   ├── EncounterRepository.java       # Queries by patient, provider, date, status
│   └── ProviderRepository.java        # Queries by NPI, department, specialty
│
├── mapper/                             # Object mapping
│   ├── PatientMapper.java             # MapStruct: Patient <-> PatientDTO
│   └── FhirPatientMapper.java        # Manual: Patient <-> FHIR R4 Resource
│
├── audit/                              # HIPAA audit logging system
│   ├── AuditAccess.java               # @AuditAccess annotation definition
│   ├── AuditAction.java               # Enum: READ, CREATE, UPDATE, DELETE, SEARCH, EXPORT
│   ├── AuditAspect.java              # AOP @Around advice - intercepts annotated methods
│   ├── AuditEvent.java               # JPA entity for audit trail
│   ├── AuditEventRepository.java     # Repository for audit events
│   ├── AuditService.java             # Async audit event persistence
│   ├── PatientAccessLogger.java      # Convenience: patient PHI access logging
│   └── MedicationAuditLogger.java    # Convenience: medication audit logging
│
└── legacy/                             # !! INTENTIONAL HIPAA GAPS (demo only) !!
    ├── LegacyPatientLookup.java       # Direct JDBC - bypasses audit layer
    ├── InsuranceCache.java            # Caches SSN with no TTL
    ├── EncounterExportService.java    # Batch exports without audit logging
    └── ReportGenerator.java           # Writes PHI to temp files without cleanup

src/main/resources/
├── application.yml                     # Default config (local PostgreSQL)
├── application-dev.yml                 # Neon cloud database config
└── db/migration/
    ├── V1__initial_schema.sql         # Full schema definition
    └── V2__seed_data.sql              # Seed data
```

---

## Getting Started

### Prerequisites
- Java 11+ (Temurin/OpenJDK recommended)
- Maven 3.8+
- Docker (for infrastructure services)

### Quick Start

```bash
# 1. Start infrastructure (from the data repo)
cd ../demos-coghealth-ehr-data
docker-compose up -d
docker exec -i coghealth-postgres psql -U coghealth coghealth < seed.sql

# 2. Run the API
cd ../demos-coghealth-ehr-api
mvn spring-boot:run
# API available at http://localhost:8080/api/

# Or use the interactive start script:
./start.sh
```

### Verify
- Swagger UI: http://localhost:8080/api/swagger-ui/index.html
- Health check: http://localhost:8080/api/actuator/health
- Patient API: http://localhost:8080/api/v1/patients/1

---

## Configuration

### application.yml (Default / Local)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/medchart_ehr  # or override with DB_URL
    username: medchart                                    # or override with DB_USERNAME
    password: medchart_dev                                # or override with DB_PASSWORD
  jpa:
    hibernate.ddl-auto: none                             # Flyway handles schema
  flyway:
    enabled: true
    locations: classpath:db/migration
  redis:
    host: localhost
    port: 6379
  rabbitmq:
    host: localhost
    port: 5672

server:
  port: 8080
  servlet.context-path: /api

medchart:
  security:
    jwt:
      secret: ${JWT_SECRET:dev-secret-key-change-in-production}
      expiration: 86400000                               # 24 hours
  audit:
    enabled: true
    async: true
  integration:
    insurance.base-url: ${INSURANCE_API_URL:http://localhost:8081}
    pharmacy.base-url: ${PHARMACY_API_URL:http://localhost:8082}
    lab.hl7-port: 2575
```

### application-dev.yml (Neon Cloud DB)

Uses environment variables: `NEON_DB_URL`, `NEON_DB_USERNAME`, `NEON_DB_PASSWORD`

```bash
# Run with Neon cloud DB
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/medchart_ehr` | Database JDBC URL |
| `DB_USERNAME` | `medchart` | Database username |
| `DB_PASSWORD` | `medchart_dev` | Database password |
| `JWT_SECRET` | `dev-secret-key-change-in-production` | JWT signing secret |
| `INSURANCE_API_URL` | `http://localhost:8081` | External insurance API |
| `PHARMACY_API_URL` | `http://localhost:8082` | External pharmacy API |
| `NEON_DB_URL` | (none) | Neon cloud database URL (dev profile) |
| `NEON_DB_USERNAME` | (none) | Neon cloud username (dev profile) |
| `NEON_DB_PASSWORD` | (none) | Neon cloud password (dev profile) |

---

## Domain Model

The domain layer uses **JPA entities organized by bounded context** under `com.medchart.ehr.domain`.

### Core Entities

| Entity | Table | Key Fields | Notes |
|---|---|---|---|
| `Patient` | `patients` | mrn, ssn, name, dob, gender, address | Central entity; FK target for most tables |
| `Provider` | `providers` | npi, name, credentials, specialty, department | Physicians, NPs |
| `Encounter` | `encounters` | encounter_number, patient_id, provider_id, type, status | Office visits; has lifecycle states |
| `Medication` | `medications` | ndc_code, rxnorm_code, name, schedule, controlled | Reference data |
| `MedicationOrder` | `medication_orders` | order_number, patient_id, medication_id, dose, status | Prescriptions |
| `LabOrder` | `lab_orders` | patient_id, test_code, status, priority | Lab test orders |
| `LabResult` | `lab_results` | lab_order_id, value, unit, reference_range, flag | Test results |
| `ClinicalNote` | `clinical_notes` | patient_id, encounter_id, note_type, content | SOAP/H&P notes |
| `Allergy` | `allergies` | patient_id, allergen, severity, reaction | Patient allergies |
| `Diagnosis` | `diagnoses` | patient_id, icd10_code, description, chronic | ICD-10 coded |
| `InsuranceCoverage` | `insurance_coverages` | patient_id, payer_name, member_id, plan_type | Insurance info |
| `Vitals` | `vitals` | patient_id, encounter_id, temp, hr, bp, spo2 | Vital signs |
| `AuditEvent` | `audit_events` | user_id, patient_id, action, ip_address, timestamp | HIPAA audit trail |

### Encounter Lifecycle

```
PLANNED -> ARRIVED -> TRIAGED -> IN_PROGRESS -> ON_HOLD -> FINISHED
                                     |                        |
                                     +-> CANCELLED            +-> ENTERED_IN_ERROR
                                     |
                                     +-> (NO_SHOW)
```

State transitions are managed by `EncounterService` via dedicated endpoints: `check-in`, `start`, `complete`, `cancel`, `no-show`.

---

## API Endpoints

All endpoints are under `/api/v1/`. See the [full API reference in the main ONBOARDING.md](https://github.com/COG-GTM/demos-coghealth-ehr-data/blob/main/ONBOARDING.md#api-reference).

### Quick Reference

| Controller | Base Path | Key Operations |
|---|---|---|
| `PatientController` | `/v1/patients` | CRUD, search by name/MRN, lookup by MRN |
| `EncounterController` | `/v1/encounters` | CRUD, lifecycle (check-in/start/complete/cancel/no-show), queries by patient/provider/date/status |
| `ProviderController` | `/v1/providers` | CRUD, search by NPI/department/specialty/name |
| `LegacyExportController` | `/v1/export` | CSV encounter exports, report generation |

### Swagger UI

Interactive API documentation is auto-generated by SpringDoc OpenAPI and available at:
**http://localhost:8080/api/swagger-ui/index.html**

---

## Service Layer

### PatientService
- CRUD operations with `@AuditAccess` annotations on every method
- Uses `PatientMapper` (MapStruct) for entity/DTO conversion
- Auto-generates MRN if not provided
- `findBySsn()` is **deprecated** and throws `UnsupportedOperationException` (HIPAA compliance)

### EncounterService
- Full encounter lifecycle management (create, check-in, start, complete, cancel, no-show)
- Queries by patient, provider, date range, status
- Supports paged results

### ProviderService
- CRUD with search by NPI, department, specialty, last name
- Soft-delete (deactivate) instead of hard delete
- Lists all departments and specialties

### AppointmentService
- Combines scheduling with insurance eligibility verification
- Uses **cache-aside pattern**: checks Redis first, falls back to `InsuranceGateway`

### InsuranceGateway
- External API integration with **circuit breaker** pattern
- Configurable timeout (default 5s)
- Graceful degradation on failure

### ProviderNotificationService
- `@Async` non-blocking notifications
- Supports multiple notification channels
- Graceful failure handling

### Chronic Care Module (Stubs)
Located in `service/chronic/`. Partially implemented stubs for:
- `ChronicMedicationService` - Chronic medication refill management
- `MedicationAdherenceTracker` - PDC (Proportion of Days Covered) tracking
- `PharmacyIntegrationService` - NCPDP pharmacy data exchange
- `MedicationNotificationService` - Refill reminders

---

## Audit Logging System

The audit system ensures HIPAA-compliant logging of all PHI access.

### How It Works

1. **Annotate** service methods with `@AuditAccess`:
   ```java
   @AuditAccess(
       action = AuditAction.READ,
       resourceType = "Patient",
       description = "View patient record"
   )
   public PatientDTO getPatientById(Long id) { ... }
   ```

2. **AuditAspect** (AOP `@Around` advice) intercepts the call and:
   - Extracts user ID, username from security context (defaults to "system" in dev)
   - Extracts patient ID from method arguments
   - Captures client IP address (including X-Forwarded-For support) and User-Agent
   - Records success/failure and any error messages
   - Calls `AuditService.saveAuditEventAsync()` to persist

3. **AuditEvent** is stored in `audit_events` table with:
   - userId, userName, patientId
   - action (READ, CREATE, UPDATE, DELETE, SEARCH, EXPORT)
   - resourceType, description
   - ipAddress, userAgent
   - success, errorMessage
   - timestamp

### Available Audit Actions

```java
public enum AuditAction {
    READ, CREATE, UPDATE, DELETE, SEARCH, EXPORT
}
```

### Adding Audit Logging to New Methods

Simply add the `@AuditAccess` annotation:
```java
@AuditAccess(action = AuditAction.READ, resourceType = "LabResult", description = "View lab results")
public List<LabResult> getLabResults(Long patientId) { ... }
```

---

## Healthcare Standards (FHIR / HL7)

### FHIR R4 Patient Mapping

`FhirPatientMapper` provides bidirectional conversion:

- **`toFhirResource(Patient)`** -> FHIR R4 Patient JSON
  - Identifier systems: MRN (`http://hospital.example.org/mrn`), SSN (`http://hl7.org/fhir/sid/us-ssn`)
  - Maps: name, gender, birthDate, telecom, address, maritalStatus, active, deceased

- **`fromFhirResource(Map)`** -> Internal Patient entity

### HL7 v2.x

- HAPI HL7 v2.3 library for parsing ADT (Admit/Discharge/Transfer) messages
- RabbitMQ queue for async message processing
- HL7 listener port: 2575 (configurable)

---

## Legacy Module (Intentional HIPAA Gaps)

> **These files contain INTENTIONAL security/compliance issues for HIPAA audit demonstration purposes.**
> Do not "fix" them unless specifically working on the HIPAA audit demo.

| File | Issue |
|---|---|
| `LegacyPatientLookup.java` | Direct JDBC queries bypass the audit logging layer entirely |
| `InsuranceCache.java` | Caches SSN in memory with no TTL or expiration policy |
| `EncounterExportService.java` | Batch export operations are not audit logged |
| `ReportGenerator.java` | Writes PHI to temporary files on disk without cleanup |

---

## Database Migrations (Flyway)

Flyway runs automatically on application startup. Migrations are in `src/main/resources/db/migration/`:

| Migration | Description |
|---|---|
| `V1__initial_schema.sql` | All table definitions (patients, providers, encounters, etc.) |
| `V2__seed_data.sql` | Demo seed data |

### Adding New Migrations

1. Create `src/main/resources/db/migration/V{N}__{description}.sql`
2. Follow naming convention: `V{version}__{description}.sql` (double underscore)
3. Flyway applies new migrations on next startup
4. Never modify existing migration files after they've been applied

---

## Build & Test

```bash
# Compile
mvn compile -B

# Run tests
mvn test -B

# Full verify (compile + test + package)
mvn verify -B

# Package JAR (skip tests)
mvn package -DskipTests

# Run the application
mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## CI Pipeline

Defined in `.github/workflows/ci.yml`. Runs on push/PR to `main`.

| Step | Command |
|---|---|
| Setup Java 11 (Temurin) | `actions/setup-java@v4` with Maven cache |
| Compile | `mvn compile -B` |
| Test | `mvn test -B` |
| Verify | `mvn verify -B -DskipTests` |

CI environment uses GitHub Secrets for Neon DB credentials: `NEON_DB_URL`, `NEON_DB_USERNAME`, `NEON_DB_PASSWORD`.

---

## Related Repositories

| Repo | Description | Link |
|---|---|---|
| `demos-coghealth-ehr-data` | Infrastructure, database schema, seed data, main onboarding doc | [GitHub](https://github.com/COG-GTM/demos-coghealth-ehr-data) |
| `demos-coghealth-ehr-web` | React frontend application | [GitHub](https://github.com/COG-GTM/demos-coghealth-ehr-web) |

---

*See the [full cross-repo onboarding guide](https://github.com/COG-GTM/demos-coghealth-ehr-data/blob/main/ONBOARDING.md) for the complete system overview.*
