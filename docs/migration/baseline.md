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

- **There are no tests in the repository.** `src/test` does not exist; Surefire (2.22.2) reports `No tests to run.`
- `h2` and `spring-boot-starter-test` / `spring-security-test` are declared with `test` scope but nothing uses them.
  (The environment note "`mvn test` uses H2" describes intent, not current state.)
- Consequence: the green CI status below proves the code **compiles and packages** on Java 11; it says
  nothing about behaviour. Closing this gap is the job of the "close test gaps on critical order flows"
  step and should happen before any upgrade step relies on CI as a safety net.

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
  later step sees them. Nothing in CI needs a database today (no tests), so this has no effect yet.
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
