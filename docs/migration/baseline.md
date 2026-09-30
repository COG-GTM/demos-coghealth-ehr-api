# Migration baseline: build, test, package, deploy (pre-upgrade)

Step `s1.1` of the migration plan **Order service: Java 21 + Spring Boot 3.3** (javax → jakarta).
This document records how the service is built, tested, packaged and deployed **today**, and the
result of a full CI-equivalent build on the current toolchain. No upgrade work is included.

Recorded: 2026-09-30, from `main` @ `dfcd98b`.

## Repository and deviation from the plan

| | Plan assumption | Actual (this repo) |
|---|---|---|
| Repository | "order service" (not named in the request) | `COG-GTM/demos-coghealth-ehr-api` — chosen by the migration manager on the requester's behalf. The order domain here is lab orders (`domain/order/LabOrder`, `LabResult`). |
| Java | 8 | **11** (`<java.version>11</java.version>`, `maven-compiler-plugin` `<release>11</release>`) |
| Spring Boot | 2.7 | 2.7.18 (`spring-boot-starter-parent`) |
| Target | Java 21 / Boot 3.3 | unchanged |

No connected repository is on Java 8, so **the baseline is recorded on Java 11, matching CI**.
Downstream steps should treat Java 11 (not 8) as the starting point; the Java 8 → 11 removals
(e.g. `javax.xml.bind`, `javax.annotation` from the JDK) are therefore already behind this codebase.

## Build

| Item | Value |
|---|---|
| Build tool | Maven, single module, `pom.xml` |
| Maven wrapper | **None** (no `mvnw` / `.mvn/wrapper`). Maven version is whatever the machine provides: CI uses the Maven preinstalled on `ubuntu-latest`; locally verified with Apache Maven 3.6.3. |
| Compiler | `maven-compiler-plugin` 3.11.0, `<release>11</release>`; annotation processors Lombok 1.18.36 and MapStruct 1.5.5.Final |
| Artifact | `com.medchart:medchart-ehr-api:3.2.1`, packaging `jar` |
| Linter / formatter | None configured (`mvn compile` is the only static check) |

Key resolved runtime versions (from the packaged jar, `BOOT-INF/lib`):
Spring Framework 5.3.31, Hibernate ORM 5.6.15.Final, Tomcat 9.0.83, Flyway 8.5.13,
Jackson 2.13.5, PostgreSQL JDBC 42.3.8, springdoc-openapi-ui 1.7.0, jjwt 0.11.5, HAPI HL7v2 2.3 (v2.4 structures).

## Tests

At the `s1.1` baseline there were **no tests** (`src/test` did not exist). Step `s1.3` added the
characterization suite described in [Characterization test suite](#characterization-test-suite-s13) below.

## CI

File: `.github/workflows/ci.yml` (workflow `CI`, job `build`, `runs-on: ubuntu-latest`).

Triggers: `push` and `pull_request` to `main`.

```yaml
- uses: actions/checkout@v4
- uses: actions/setup-java@v4   # distribution: temurin, java-version: '11', cache: maven
- run: mvn compile -B
- run: mvn test -B
- run: mvn verify -B -DskipTests
```

Notes:
- `NEON_DB_URL` / `NEON_DB_USERNAME` / `NEON_DB_PASSWORD` are set as `env` on the **setup-java step only**, so no
  later step sees them. The test suite does not use Neon: it starts its own Postgres/Redis with Testcontainers
  (Docker is available on `ubuntu-latest`).
- CI annotations warn that `actions/checkout@v4` and `actions/setup-java@v4` run on the deprecated Node.js 20 runtime.
- No artifact upload, image build, or deploy job.
- Recent `main` history: the last 5 pushes to `main` (latest run
  [23705414573](https://github.com/COG-GTM/demos-coghealth-ehr-api/actions/runs/23705414573), 2026-03-29) are all green, 22–43 s.
  Their logs have expired (HTTP 410), so the fresh run on this PR is the recorded CI baseline.

## Packaging and deployment

| Item | Value |
|---|---|
| Packaging | Executable fat jar via `spring-boot-maven-plugin` 2.7.18 `repackage` (Lombok excluded). `target/medchart-ehr-api-3.2.1.jar`, ~62 MB. |
| Container image | **None.** No `Dockerfile`, no Jib, no buildpacks config. |
| `docker-compose.yml` (this repo and `demos-coghealth-ehr-data`) | Infrastructure only: Postgres 14, Redis 7, Keycloak 23, RabbitMQ 3, Elasticsearch/Kibana 8.11. The API itself is not a compose service. |
| Deployment target | **None defined in the repo.** No deploy workflow, manifests (k8s/Helm/Terraform) or platform config. The service is run from source with `mvn spring-boot:run` (see `start.sh`, README) or `java -jar`. |

## Runtime configuration sources

| Source | Purpose |
|---|---|
| `src/main/resources/application.yml` (default profile) | Local Postgres via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (defaults `jdbc:postgresql://localhost:5432/medchart_ehr`, `medchart` / `medchart_dev`), Redis `localhost:6379`, RabbitMQ settings, `server.servlet.context-path=/api`, actuator `health,info,metrics`, `JWT_SECRET`, `INSURANCE_API_URL`, `PHARMACY_API_URL`. `ddl-auto: none`. |
| `src/main/resources/application-dev.yml` (`dev` profile) | Shared Neon Postgres via `NEON_DB_URL` / `NEON_DB_USERNAME` / `NEON_DB_PASSWORD`, Hikari tuning, `ddl-auto: validate`, Redis auto-config excluded. |
| `src/main/resources/db/migration` | Flyway `V1__initial_schema.sql`, `V2__add_user_authentication.sql`, `V3__seed_data.sql`; `baseline-on-migrate: true`. |
| `.gitignore`d | `application-local.yml`, `application-prod.yml`, `.env` (no prod profile is committed). |
| Config server / Vault | None. |

Inconsistencies worth knowing about during the upgrade:
- The default datasource (`medchart_ehr` / `medchart`) does not match the Postgres in `demos-coghealth-ehr-data`
  (`coghealth` / `coghealth` / `coghealth_dev_2024`); override with `DB_*` env vars.
- `start.sh` defaults `JAVA_HOME` to `openjdk@17`, the README says "Java 11 (required)", CI uses 11, and the Devin
  environment blueprint starts the app on a Java 21 runtime. Only Java 11 is exercised by CI.
- RabbitMQ is configured in YAML but `spring-boot-starter-amqp` is commented out in `pom.xml`.

## Baseline run (CI-equivalent, Java 11)

Environment: OpenJDK 11.0.32.1 (Ubuntu), Apache Maven 3.6.3, Linux x86_64. Clean checkout of `main` @ `dfcd98b`.

| Step (same as CI) | Result | Wall time |
|---|---|---|
| `mvn clean compile -B` | BUILD SUCCESS — 91 source files, 6 compiler warnings (below) | 3.6 s |
| `mvn test -B` | BUILD SUCCESS — **0 tests** (`No tests to run.`); 0 failures, 0 errors, 0 skipped, no flaky tests | 0.7 s |
| `mvn verify -B -DskipTests` | BUILD SUCCESS — fat jar built and repackaged | 1.2 s |

Pre-existing compiler warnings (not fixed):
- `domain/auth/User.java` lines 48, 51, 54, 57 — Lombok `@Builder will ignore the initializing expression entirely` (needs `@Builder.Default`).
- `mapper/PatientMapper.java` lines 18, 24 — MapStruct `Unmapped target properties: "ssn, createdBy, updatedBy"`.

Pre-existing test failures: none (there are no tests).

### Runtime smoke check (not part of CI)

Because CI runs no tests, the packaged jar was also started on Java 11 against Postgres 14 (`demos-coghealth-ehr-data`
`docker compose up postgres`) and a local Redis:

```bash
DB_URL=jdbc:postgresql://localhost:5432/coghealth DB_USERNAME=coghealth DB_PASSWORD=coghealth_dev_2024 \
  java -jar target/medchart-ehr-api-3.2.1.jar
```

- Flyway applied V1–V3 to an empty schema (0.23 s); app started in ~5.0 s on Tomcat 9.0.83, context path `/api`.
- `GET /api/actuator/health` → `200 {"status":"UP"}`
- `GET /api/swagger-ui/index.html` → `200`
- `GET /api/v1/patients/search?q=a` without a token → `200` with patient data. This is current (insecure) behaviour, recorded
  so the Spring Security 6 migration does not mistake a behaviour change for a regression; open PRs
  (e.g. #106, #108) already propose requiring authentication.

## javax surface (for scale only; detailed inventory is step s1.2)

33 of 91 main source files import `javax.*`: `javax.persistence` (32 imports), `javax.validation` (6), `javax.servlet` (5),
`javax.crypto` (1; JDK, stays `javax`).

## Related open work

Several open PRs already attempt parts of the upgrade outside this plan: #110 (Boot 3.5.3 / Java 21 pom-only, CI red on
its latest run), #111 (jakarta sweep), #112 (Spring Security 6 / jjwt 0.12), #113 (Boot 3 config + smoke test).
Note that #110 targets Boot 3.5.3, not the plan's 3.3. They should be reconciled with this plan before the upgrade steps start.

## Reproduce

```bash
export JAVA_HOME=/path/to/jdk-11
mvn clean compile -B && mvn test -B && mvn verify -B -DskipTests
```

## Characterization test suite (s1.3)

Added by "Close test gaps on critical order flows". Tests pin **current** behaviour on Java 11 / Boot 2.7.18 —
including bugs — so an upgrade step that changes any of it fails loudly. No production code was changed.

Stack: JUnit 5, `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` (real HTTP through Tomcat, context path `/api`),
`@SpringBootTest` + MockMvc (security filter chain / CORS), `@DataJpaTest` with `replace = NONE`, and Testcontainers
`postgres:14-alpine` + `redis:7-alpine` shared across the run (`support/TestContainers`). Flyway V1–V3 runs against
the container, so tests see the real schema and seed data. `src/test/resources/application-test.yml` supplies a
512-bit JWT secret (see finding 3).

| Area | Test class | What is pinned |
|---|---|---|
| Flyway | `persistence/FlywayMigrationTest` | V1–V3 applied in order, expected tables/constraints, seed counts |
| JPA mapping | `persistence/SchemaValidationTest` | `ddl-auto=validate` (the `dev` profile setting) fails today, with the exact Hibernate message |
| Lab orders | `persistence/LabOrderPersistenceTest` | LabOrder insert/select fail (finding 1); LabResult round-trip; lab_orders constraints and status lifecycle at SQL level; `addResult`, `isAbnormal` |
| Patient queries | `persistence/PatientRepositoryTest` | round-trip incl. embedded address, `findByMrn/Ssn`, JPQL `searchPatients` (case, MRN fragment, paging), derived queries, active queries/count |
| Encounter queries | `persistence/EncounterRepositoryTest` | `findByEncounterNumber`, fetch-join `findByIdWithDetails` vs lazy `findById`, date range, `findTodaysSchedule` status filter, count by patient, enum failure on seed row 15 |
| Users | `persistence/UserRepositoryTest` | seeded admin roles/authorities; admin hash matches neither `admin123` nor `password` |
| Patient HTTP | `api/PatientApiTest` | GET by id/MRN, search page JSON, POST 201 (MRN generation), PUT partial merge + version bump, 400s for Bean Validation / bad JSON / bad enum, 500s for not-found / duplicate MRN / DB NOT NULL, 405, Boot error body shape, async audit rows |
| Encounter HTTP | `api/EncounterApiTest` | entity JSON shape (incl. patient SSN), 404 on unknown id, lazy-proxy 500s, 400s, create/update 500s, check-in → start → complete (text/plain notes) → cancel / no-show, transitions on unknown ids return 200 |
| Actuator | `api/ActuatorApiTest` | health `{"status":"UP"}` with no details; only health/info/metrics exposed |
| JWT provider | `security/JwtTokenProviderTest` | HS512, subject/expiry/roles claims, subject check, expired/tampered/foreign/malformed tokens throw, default secret is a `WeakKeyException` |
| Auth HTTP | `security/AuthApiTest` | register → login → Bearer token, BCrypt + default `PROVIDER` role, duplicate username/email 400 plain text, bad credentials 403, admin cannot log in, path is `/api/api/auth/**` |
| Security chain | `security/SecurityFilterChainTest` | anonymous requests allowed; valid token populates SecurityContext; invalid/expired/foreign/unknown-user/wrong-scheme tokens silently fall back to anonymous; disabled users still authenticate; JWT filter sits outside `FilterChainProxy`; CORS allow-list; stateless, no session cookie |

**Messaging / outbound HTTP:** none on the order path. RabbitMQ is configured but the AMQP starter is commented out;
the insurance/pharmacy clients are not reachable from any order, patient, encounter or auth flow, so they are not covered here.

### Coverage (JaCoCo 0.8.12, `mvn test`, 79 tests, all passing)

Report: `target/site/jacoco/index.html` locally; uploaded as the `jacoco-report` artifact by CI.

| Scope | Line | Branch |
|---|---|---|
| Whole module | 45.1% (704/1562) | 26.8% (89/332) |
| `config` (SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetailsService) | 100% | 83.3% |
| `domain.order` (LabOrder, LabResult) | 100% | 100% |
| `PatientController` / `PatientService` | 100% / 96.3% | – / 100% |
| `EncounterController` / `EncounterService` | 83.9% / 83.6% | – / 100% |
| `AuthController` | 100% | 100% |

Largest uncovered areas are outside the order/patient/security flows: `legacy` (5%), `service.chronic` (6%), `audit` (27%), MapStruct mappers.

### Findings from characterization (current behaviour, not fixed)

1. **Lab orders cannot be read or written through JPA.** `LabOrder.icd10Code` is mapped by Spring's
   `CamelCaseToUnderscoresNamingStrategy` to column `icd10code`, but V1 creates `icd10_code`. Every LabOrder
   INSERT/SELECT fails with `column "icd10code" ... does not exist`. Combined with there being **no LabOrder
   repository, service or controller**, there is no order create/read/update/cancel over HTTP to test; the
   ticket's HTTP order-flow coverage is therefore provided for the nearest existing flows (patients, encounter
   lifecycle incl. cancel) plus lab-order persistence at SQL/JPA level.
2. **`ddl-auto=validate` fails** on the Flyway schema (first mismatch: `audit_events.resource_id` varchar vs `Long`),
   so the `dev` profile cannot start against a Flyway-built schema. The default profile uses `none`.
3. **Default JWT secret is too short for HS512** (280 bits) → login returns 500 (`WeakKeyException`) unless `JWT_SECRET`
   is set. jjwt 0.12 keeps this rule.
4. **Security is permit-all, and the JWT filter never rejects.** It is registered as a plain servlet filter that runs
   after Spring Security's authorization, swallows all token errors, and ignores `enabled=false`.
5. **`AuthController` is served at `/api/api/auth/**`** (controller mapping `/api/auth` + context path `/api`).
6. **Seed admin cannot log in** — the V2 hash does not match `admin123` (the documented password).
7. **Error handling:** no `@ControllerAdvice`; not-found / duplicate MRN / DB constraint errors are 500 with Boot's
   default error body (`timestamp,status,error,path`); Bean Validation errors are 400 without field details
   (`server.error.include-binding-errors` unset). Boot 3 changes the default error attributes and adds
   ProblemDetail support, so these assertions are expected to need re-baselining.
8. **Encounter endpoints serialize JPA entities.** Endpoints that return lazily loaded associations 500 on Hibernate
   proxy serialization; `GET /v1/encounters/{id}` exposes the patient's SSN. Seed encounter `ENC-2024-000015` is
   column-shifted in V3 (`encounter_type = 'IN_PROGRESS'`) and fails enum mapping.
9. **Encounter create/update with `{"patient":{"id":..}}` references 500** (transient associations); lifecycle
   transitions on unknown ids return 200 (the service ignores missing encounters).

### Running

Requires Docker for Testcontainers.

```bash
export JAVA_HOME=/path/to/jdk-11
mvn test -B        # tests + target/site/jacoco
```
