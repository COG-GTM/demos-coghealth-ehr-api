# Migration baseline (pre-upgrade)

Baseline for the migration plan **Order service: Java 21 + Spring Boot 3.3** (javax → jakarta). No upgrade work is included.

- Step `s1.1`: how the service is built, tested, packaged and deployed **today**, and the result of a full CI-equivalent
  build on the current toolchain (sections below, up to the first "Reproduce").
- Step `s1.4`: what "behaves the same" means: [API contract](#api-contract-s14), [performance](#performance-s14) and
  [observability](#observability-s14). Verification and hardening compare against these.

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

---

# s1.4: API, performance and observability baseline

Recorded: 2026-09-30 on Java 11, from `main` @ `8b5fd10` (no source changes on this branch). Raw outputs are in
[`baseline/java11/`](baseline/java11/) and were all produced by one run of
[`perf/baseline/run-baseline.sh`](../../perf/baseline/run-baseline.sh).

**Java 8 deviation.** The plan asks for performance "on Java 8". This repository compiles for and runs on **Java 11**
(see the table at the top), so every number below is **Java 11**, not Java 8. Nothing here was measured on Java 8.

**Order endpoints.** The "order" domain in this repo (`domain/order/LabOrder`, `LabResult`) has no controller, so there
are no order endpoints to load. The load profile uses the service's main read paths instead: encounters, patients,
providers.

## API contract (s1.4)

| Artifact | What it is |
|---|---|
| [`api/openapi.json`](baseline/java11/api/openapi.json) | springdoc OpenAPI 3.0.1 document from the running app (`GET /api/v3/api-docs`), keys sorted. 33 paths across 5 controllers. |
| [`api/responses.json`](baseline/java11/api/responses.json) | 71 recorded requests covering every public operation plus actuator and error cases: method, path, synthetic request body, status, content type, `Content-Disposition`, and the response body **reduced to its shape** (scalars → JSON type name, arrays → length + first item). Spring error bodies keep `status`/`error`/`path`; CSV bodies keep only their header row; text reports keep only line labels. No seeded patient values are stored. |

Regenerate with `python3 perf/baseline/capture_api.py <base-url> <out-dir>` against a disposable database (it creates
rows and changes the status of seed encounters 16–18); `run-baseline.sh` does this after the load test.

URL layout: `server.servlet.context-path=/api`, so every path in `openapi.json` is served under `/api`
(e.g. `/api/v1/patients/{id}`). `AuthController` is itself mapped to `/api/auth`, so login/register are served at
**`/api/api/auth/*`** — `openapi.json` lists them as `/api/auth/*` relative to the `/api` server URL, and
`/api/auth/*` (one `/api`) returns 404.

Security: `SecurityConfig` permits `/**`, so every endpoint answers without a token; the JWT only matters to callers that
send it. Recorded as current behaviour, not endorsed.

### Behaviour to preserve or deliberately change

These are **current** responses, recorded so a migration step does not mistake a pre-existing failure for a regression
(or silently "fix" one without saying so). None were changed here.

| Request | Today | Cause |
|---|---|---|
| `GET /v1/patients/{unknown}`, `/v1/patients/mrn/{unknown}`, `PUT /v1/patients/{unknown}` | **500** | `EntityNotFoundException` has no handler (providers/encounters return 404 with empty body) |
| `GET /v1/encounters/15` | **500** | seed row has `encounter_type='IN_PROGRESS'`, not an `EncounterType` constant |
| `GET /v1/encounters/number/{n}`, `/patient/{id}`, `/patient/{id}/paged`, `/provider/{id}`, `/provider/{id}/schedule`, `/date-range`, `/status/{s}` | **500** | Jackson cannot serialize the lazy `patient`/`attendingProvider` Hibernate proxies (`ByteBuddyInterceptor`). `GET /v1/encounters/{id}` works (200) |
| `GET /v1/encounters/status/BOGUS`; missing required query params | 400 | Spring type/param resolution |
| `GET /v1/providers/{unknown}`, `/npi/{unknown}`, `/v1/encounters/{unknown}`, `/number/{unknown}` | 404, empty body | `ResponseEntity.notFound()` |
| `POST /v1/encounters/{unknown}/check-in` (and the other transitions) | 200, empty body | `findById(...).ifPresent(...)` silently ignores unknown ids |
| `POST /v1/patients` without `active`/`deceased` | **500** | entity inserts NULL instead of the column defaults (NOT NULL violation); 201 when both are sent |
| `PUT /v1/providers/{id}`, `PUT /v1/encounters/{id}` without `version` | **500** | `@Version` null → entity treated as new → unique violation on `npi` / `encounter_number` |
| `PUT /v1/encounters/{id}` with `version` | **500** | update runs, then the returned entity hits the lazy-proxy serialization error |
| `POST /v1/encounters` with `{patient:{id}}` refs without `version` | **500** | HHH000437 transient reference; 200 when refs carry `version` |
| `GET /v1/export/reports/patient-roster`, `/encounter-summary` | 200, `Content-Type: application/json`, body is plain text `Report generated at: /tmp/...` | controller returns `String` |
| `GET /v1/export/*` CSV/text | 200 `text/csv` / `text/plain` with `Content-Disposition: attachment` | CSV headers include SSN columns (PHI surface, recorded not endorsed) |
| `POST /api/api/auth/login` | 200 with token only if `medchart.security.jwt.secret` is ≥ 64 bytes; wrong password → 403 | the `application.yml` default secret is too short for HS512 (`WeakKeyException`) |
| `GET /api/actuator/prometheus` | 404 | not exposed (see Observability) |

## Performance (s1.4)

### Environment

| | |
|---|---|
| JVM | OpenJDK 11.0.32.1 (Ubuntu build), `-Xmx1g`, default GC (G1), no other flags |
| Build | `mvn clean package -DskipTests`, Maven 3.6.3; app jar `medchart-ehr-api-3.2.1.jar`, Spring Boot 2.7.18, Tomcat 9.0.83 |
| Host | Ubuntu 22.04.5, kernel 6.8.0-1061-aws, 8 vCPU Intel Xeon Platinum 8559C, 31 GiB RAM (cloud VM, not isolated) |
| Topology | app, PostgreSQL 14 and Redis 7 (Docker containers) and k6 v0.54.0 all on one host over loopback |
| Data | empty database migrated by Flyway V1–V3: 15 patients, 18 encounters, 8 providers, 1 user |
| Config | `application.yml` defaults (incl. `com.medchart: DEBUG`, `org.hibernate.SQL: DEBUG`, `open-in-view` on); only DB URL/credentials, Redis port and a throwaway JWT secret are overridden. App stdout is redirected to a file. |

Full details: [`environment.txt`](baseline/java11/environment.txt).

### Load profile ([`perf/k6/baseline.js`](../../perf/k6/baseline.js))

Read-only, no auth header. Weighted mix per request: 30% `GET /v1/encounters/{id}` (ids 1–14, 16–18), 20%
`GET /v1/patients/{id}`, 10% `GET /v1/patients/mrn/{mrn}`, 20% `GET /v1/patients/search?q=…&size=10`, 10%
`GET /v1/providers`, 10% `GET /v1/providers/{id}`. `setup()` fails the run if any endpoint in the mix is not 200.

Sequence: start + 30 s idle → warm-up 50 req/s for 30 s (discarded) → **latency** (open model, constant 200 req/s,
60 s) → **throughput** (closed model, 32 VUs with no think time, 60 s). RSS from `/proc/<pid>/status`, heap from
`/actuator/metrics`, both sampled every second.

### Results (Java 11)

Startup ([`startup.csv`](baseline/java11/startup.csv), `wall` = JVM launch → first 200 from `/actuator/health`):

| | wall to healthy | "Started … in" | ApplicationReady |
|---|---|---|---|
| run 0: empty schema, Flyway migrates V1–V3 | 5.33 s | 4.63 s | 4.66 s |
| runs 1–5: schema up to date, **median** (min–max) | **4.79 s** (4.67–4.94) | **4.12 s** (4.09–4.25) | **4.14 s** (4.12–4.27) |

Memory ([`idle.json`](baseline/java11/idle.json), [`resources-*.json`](baseline/java11/), per-second samples in `memory-*.csv`):

| | RSS | heap used | heap committed | GC pauses |
|---|---|---|---|---|
| idle, 30 s after start | 650 MiB | 290 MiB (sawtooth; 59–294 MiB across runs) | 504 MiB | — |
| latency (200 req/s), median / max | 769 / 776 MiB | 190 / 326 MiB | 504 MiB | 7, 45 ms total, max 20 ms |
| throughput (32 VUs), median / max | 832 / 853 MiB | 158 / 341 MiB | 504 MiB | 168, 303 ms total, max 11 ms |
| whole run peak (`VmHWM`) | 852 MiB | | | |

GC pause count and total are per phase (difference of `jvm.gc.pause` COUNT/TOTAL_TIME before and after). The max is
Micrometer's rolling-window MAX read at the end of the phase, so it can include pauses from just before the phase started.

Non-heap used at idle: 127 MiB; 26 live threads; 17,680 classes loaded.

Latency and throughput ([`k6-latency.txt`](baseline/java11/k6-latency.txt), [`k6-throughput.txt`](baseline/java11/k6-throughput.txt); 0.00% failed requests in both):

| Endpoint | 200 req/s: p50 / p95 / p99 | 32 VUs: p50 / p95 / p99 |
|---|---|---|
| **all** | **1.40 / 2.30 / 3.58 ms** | **5.95 / 14.43 / 19.72 ms** |
| `GET /v1/encounters/{id}` | 1.53 / 2.43 / 3.82 | 6.23 / 14.81 / 20.13 |
| `GET /v1/patients/{id}` | 1.24 / 1.94 / 2.92 | 5.51 / 13.94 / 19.29 |
| `GET /v1/patients/mrn/{mrn}` | 1.29 / 2.07 / 3.36 | 5.57 / 13.86 / 18.98 |
| `GET /v1/patients/search` | 1.45 / 2.38 / 3.58 | 5.94 / 14.49 / 19.76 |
| `GET /v1/providers` | 1.54 / 2.58 / 4.05 | 6.71 / 15.16 / 20.41 |
| `GET /v1/providers/{id}` | 1.09 / 1.82 / 3.18 | 5.28 / 13.61 / 18.98 |
| **throughput** | 200.0 req/s (offered) | **4,614 req/s** (276,835 requests in 60 s) |

Reading these numbers:
- Run-to-run spread on this VM: across five full runs the same day (only one is committed), 32-VU throughput
  ranged 3,965–4,620 req/s, 200 req/s p99 2.9–4.8 ms, and warm startup (wall to healthy) 4.67–6.66 s. Treat differences smaller than that as noise; compare the migrated build **on the same host
  class with the same script**, ideally several runs each.
- k6 shares the 8 vCPUs with the app and Postgres, so the throughput figure is a host-level ceiling, not app-only.
- DEBUG logging is part of what is measured: the app wrote ~60 MiB of log during the 200 req/s phase and ~1.37 GiB
  during the 60 s throughput phase (`app_log_mib_written`). Changing log levels during the migration will move these
  numbers on its own.

### Reproduce

```bash
export JAVA_HOME=/path/to/jdk-11     # the jdk-21 build uses the same command with its JAVA_HOME and LABEL=java21
LABEL=java11 perf/baseline/run-baseline.sh
```

Needs Docker, k6, jq, curl, python3 and Maven. It starts throwaway `postgres:14-alpine` / `redis:7-alpine` containers on
127.0.0.1:55432/56379 with a random password, builds the jar, runs 1 + 5 startups, the idle sample, the three k6 phases and
the API capture, then removes the containers. Output goes to `docs/migration/baseline/<LABEL>/`; application logs go to
`target/baseline-logs/<LABEL>/` (not committed: DEBUG SQL logging makes them >1 GB, and they contain seeded patient data).
Knobs: `JAVA_OPTS`, `RATE`, `VUS`, `DURATION`, `STARTUP_RUNS`, `IDLE_SECONDS`, `KEEP_INFRA=1`.

## Observability (s1.4)

No dashboards or alert definitions live in this repo, so this lists what the service **emits**; anything a dashboard
or alert uses must come from here.

### Metrics

Micrometer via `spring-boot-starter-actuator`; **no registry dependency** (no `micrometer-registry-prometheus` etc.), so
metrics are only readable through `GET /api/actuator/metrics[/{name}]`. Exposed actuator endpoints:
`health`, `info`, `metrics` (`management.endpoints.web.exposure.include`); `/api/actuator/prometheus` → 404.
No custom meters in the code (no `MeterRegistry`, `@Timed`, `Counter`, `Timer`).

The 63 meter names served on Java 11 (from `responses.json`):

| Group | Names |
|---|---|
| HTTP | `http.server.requests` (tags `method`, `uri`, `status`, `outcome`, `exception`) |
| JVM | `jvm.buffer.count`, `jvm.buffer.memory.used`, `jvm.buffer.total.capacity`, `jvm.classes.loaded`, `jvm.classes.unloaded`, `jvm.gc.live.data.size`, `jvm.gc.max.data.size`, `jvm.gc.memory.allocated`, `jvm.gc.memory.promoted`, `jvm.gc.overhead`, `jvm.gc.pause`, `jvm.memory.committed`, `jvm.memory.max`, `jvm.memory.usage.after.gc`, `jvm.memory.used`, `jvm.threads.daemon`, `jvm.threads.live`, `jvm.threads.peak`, `jvm.threads.states` |
| Process / system | `process.cpu.usage`, `process.files.max`, `process.files.open`, `process.start.time`, `process.uptime`, `system.cpu.count`, `system.cpu.usage`, `system.load.average.1m`, `disk.free`, `disk.total` |
| Startup | `application.started.time`, `application.ready.time` |
| DB pool | `hikaricp.connections`, `hikaricp.connections.acquire`, `hikaricp.connections.active`, `hikaricp.connections.creation`, `hikaricp.connections.idle`, `hikaricp.connections.max`, `hikaricp.connections.min`, `hikaricp.connections.pending`, `hikaricp.connections.timeout`, `hikaricp.connections.usage`, `jdbc.connections.max`, `jdbc.connections.min` |
| Spring Data | `spring.data.repository.invocations` |
| Redis (Lettuce) | `lettuce.command.completion`, `lettuce.command.completion.percentile`, `lettuce.command.firstresponse`, `lettuce.command.firstresponse.percentile` |
| Tomcat | `tomcat.sessions.active.current`, `tomcat.sessions.active.max`, `tomcat.sessions.alive.max`, `tomcat.sessions.created`, `tomcat.sessions.expired`, `tomcat.sessions.rejected` |
| Executor (`@EnableAsync`) | `executor.active`, `executor.completed`, `executor.pool.core`, `executor.pool.max`, `executor.pool.size`, `executor.queue.remaining`, `executor.queued` |
| Logging | `logback.events` (tag `level`) |

Migration watch-point: Boot 3 moves HTTP server instrumentation to the Micrometer Observation API, so re-capture this
list (and the `http.server.requests` tags) on the migrated build and diff it against the table above rather than
assuming names and tags carry over.

### Logs

- Logback via `spring-boot-starter-logging`; **no** `logback-spring.xml`/`logback.xml`, no `logging.file`/`logging.pattern`,
  no JSON encoder. Output is the Spring Boot 2.7 default console pattern to stdout:
  `%d{yyyy-MM-dd HH:mm:ss.SSS} %5p ${PID} --- [%15.15t] %-40.40logger{39} : %m%n`, e.g.
  `2026-09-30 19:10:37.201  WARN 25327 --- [nio-8080-exec-1] .w.s.m.s.DefaultHandlerExceptionResolver : Resolved [...]`.
  Boot 3.0 changed the default timestamp to ISO-8601 with offset, and later 3.x releases add fields to the default
  pattern, so any log parser keyed on this layout needs checking after the upgrade.
- Levels (`application.yml`): `com.medchart: DEBUG`, `org.springframework.security: INFO`, `org.hibernate.SQL: DEBUG`
  (every SQL statement is logged); root `INFO`. `application-dev.yml` is not active by default.
- Message prefixes an alert could key on: `AUDIT:`, `AUDIT FAILURE:`, `AUDIT BULK:` (`PatientAccessLogger`),
  `PRESCRIPTION*`, `CONTROLLED SUBSTANCE:`, `EPRESCRIBE:`, `PDMP CHECK:`, `MEDICATION HISTORY:` (`MedicationAuditLogger`),
  `Failed to save audit event` (`AuditService`). Audit events are also persisted asynchronously by `AuditAspect` for
  methods annotated `@AuditAccess`.

### Trace / request propagation

- **None.** No Sleuth, Micrometer Tracing, OpenTelemetry, Brave or Zipkin dependency; no MDC usage; no request-id or
  `traceparent`/`b3` header handling; log lines carry no trace or span id.
- The only request header read for correlation is `X-Forwarded-For` (first value), used by `AuditAspect` as the audit
  event's client IP.
- If Micrometer Tracing is added during the Boot 3 migration, that is a new capability, not a preserved one.
