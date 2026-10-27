# CogHealth EHR — Threat Model

Repositories: `COG-GTM/demos-coghealth-ehr-api`, `COG-GTM/demos-coghealth-ehr-data`, `COG-GTM/demos-coghealth-ehr-web`.
This file is the security model for the CogHealth EHR system. It was drafted from the code and refined with the owner's answers, which are recorded under "User decisions". Security scans use it to prioritise their findings, so keep it up to date when the system or its risk decisions change.

## User decisions (2026-10-26)
- **Deployment context:** treat this as a production EHR holding **real PHI**. Its demo / intentionally vulnerable status does **not** lower priorities.
- **Attackers:** **all** attacker profiles below are in scope.
- **Most critical asset:** **patient PHI, especially SSN and patient identifiers** (MRN, insurance member IDs, DOB+name).
- **Out of scope / accepted risk:** **none**. Report everything, including hardcoded dev credentials, the planted HIPAA gaps listed in `demos-coghealth-ehr-api/AGENTS.md`, and the duplicate `frontend/` and `docker/` copies inside the API repo.

## System overview and trust boundaries
Browser SPA → Spring Boot REST API (`:8080`, context path `/api`) → PostgreSQL (local docker or shared Neon cloud DB, `dev` profile). Keycloak (`:8180`), Redis, RabbitMQ, Elasticsearch/Kibana come from docker-compose. Main boundaries:
1. **Internet/browser ↔ API.** This is the primary boundary. `SecurityConfig` sets `antMatchers("/**").permitAll()`, so **no endpoint requires authentication**. A `JwtAuthenticationFilter`/`JwtTokenProvider` exists but nothing enforces it.
2. **API ↔ data stores.** DB credentials come from env (`DB_*`, `NEON_DB_*`) with defaults in `application.yml`. The JWT secret defaults to `dev-secret-key-change-in-production`.
3. **Infra services ↔ network.** docker-compose publishes every service port on the host (5432, 6379, 8180, 5672/15672, 9200, 5601). Elasticsearch has `xpack.security.enabled=false`, Redis has no auth, and Keycloak runs `start-dev` with `admin/admin` and `sslRequired: none`.
4. **External interfaces.** These are the HR/Workday PTO CSV feed (`legacy/HrExportReader`, `HrPtoSyncJob`, `src/main/resources/interfaces/hr/`), the X12 270/271 insurance gateway (`InsuranceGateway`), FHIR R4 mapping (`interop/fhir`, `FhirPatientMapper`), and the provider notifications (`ProviderNotificationService`).
5. **CI.** GitHub Actions (`ci.yml` in both the api and web repos, plus `nightly-interface-contract.yml` with a `workflow_dispatch` input `export_date`) has access to the `NEON_DB_*` secrets.

## Components
| Name | Covers |
|---|---|
| **Web client** | `demos-coghealth-ehr-web/**` (React/Vite SPA, `src/services/*`, local audit log in `localStorage`), plus the duplicate SPA at `demos-coghealth-ehr-api/frontend/**` |
| **Auth** | `demos-coghealth-ehr-api/src/main/java/com/medchart/ehr/config/**`, `controller/AuthController.java`, `service/CustomUserDetailsService.java`, `domain/auth/**`, `repository/UserRepository.java` |
| **Clinical API** | api `controller/` (Patient, Encounter, Provider), `service/` (non-chronic), `service/chronic/**`, `domain/**`, `dto/**`, `mapper/**`, `repository/**` (except UserRepository), `interop/fhir/**` |
| **Legacy & exports** | api `legacy/**`, `controller/LegacyExportController.java`, `controller/InterfaceController.java`, `src/main/resources/interfaces/**`, `scripts/interface-contract.sh` |
| **Audit** | api `audit/**` (AuditAspect, PatientAccessLogger, MedicationAuditLogger) and the web `src/services/auditService.ts` |
| **Data & schema** | api `src/main/resources/db/migration/**`, `application*.yml`; data repo `seed.sql`, `docker/init-db/**` |
| **Infrastructure** | data repo `docker-compose.yml`, `docker/keycloak/coghealth-realm.json`, `start-infrastructure.sh`; api `docker-compose.yml`, `docker/**`, `start*.sh`; `.github/workflows/**` in all repos |

## Entry points and who can reach them
- Every `/api/v1/patients/**`, `/api/v1/encounters/**`, `/api/v1/providers/**` (including `DELETE`), `/api/v1/export/**` (bulk encounter export, patient roster, daily reports) and `/api/interfaces/**` (`POST /hr-pto-sync/run`) endpoint is reachable by **anyone with network access to the API**, because of `permitAll`.
- `/api/api/auth/login` and `/api/api/auth/register` (the controller mapping includes `/api`). Open self-registration grants the `PROVIDER` role.
- Swagger UI at `/api/swagger-ui/`, and actuator `health,info,metrics`.
- Infra admin surfaces: Keycloak admin console, RabbitMQ management UI, Kibana, and raw Postgres/Redis/ES ports.
- CI: `workflow_dispatch` inputs and `pull_request` triggers on interface files.

## Assets and sensitivity
1. **Patient PHI** (highest): SSN, MRN, name/DOB/address, insurance member IDs, encounters, medications, labs, clinical notes, and the cached eligibility data in `legacy/InsuranceCache`.
2. **Credentials and secrets**: the JWT signing secret, DB/Neon credentials, the Keycloak admin login and `coghealth-api` client secret, the realm user passwords, and RabbitMQ creds.
3. **Audit trail integrity**: `audit_events`, Elasticsearch audit index, and the web-side audit log.
4. **HR/PTO data**: employee leave and the cost-center mapping.
5. **Clinical data integrity**: encounter status, orders, and medication records (patient-safety impact).

## Attacker profiles (all in scope)
- **Unauthenticated network attacker** who can reach the API or exposed infra ports.
- **Authenticated low-privilege staff** (front desk, billing, MA, self-registered user) reaching clinical PHI, other patients' records, or admin functions.
- **Malicious insider / compromised developer machine** with repo or shared Neon DB access.
- **Malicious upstream data source**: a crafted HR CSV, FHIR payload, or insurance response.
- **Supply chain / CI attacker** via PRs or workflow inputs.

## Existing controls
- BCrypt password hashing (`SecurityConfig.passwordEncoder`). JWT is HS512, but it is not enforced.
- CORS is restricted to localhost origins, with credentials allowed.
- Hibernate `ddl-auto: validate/none` and Flyway-managed schema.
- The `@AuditAccess` aspect covers some PHI reads. The planted gaps are listed in AGENTS.md.
- Keycloak has brute-force protection enabled. The web client has a 15-minute session timeout.

## Known / intentional weaknesses (still in scope)
These are planted per `demos-coghealth-ehr-api/AGENTS.md`: SSN logged in `PatientService`, no audit on `EncounterExportService` batch exports, direct JDBC in `LegacyPatientLookup` bypassing audit, SSN cached without TTL in `InsuranceCache`, PHI temp files left in `ReportGenerator`, and 15+ endpoints without access logging. The user asked that these be ranked like any other finding.

## Open questions
- Is there a real deployment target (cloud, ingress, TLS termination) beyond docker-compose? None was found. Infra findings are judged on the docker-compose/Keycloak config as written.
- Is the shared Neon `dev` database seeded with synthetic data only? It is treated as real PHI per the user's decision.
