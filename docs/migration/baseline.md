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

## Upgrade inventory (s1.2)

Collected on the base commit with Maven 3 and JDK 11/21, plus one throw-away OpenRewrite dry run (not committed) that was built
and started against the same Postgres. Section headings follow the ticket's checklist.

### Dependency tree

`mvn dependency:tree -B` on Boot 2.7.18 (full output at the end of this section). There is **no Spring Cloud** dependency,
BOM or `spring-cloud.version` property, so there is no release train to move; if one is added later, Boot 3.3 pairs with
Spring Cloud **2023.0.x** (Leyton).

Spring-managed today → what the Boot 3.3 parent resolves (versions from the dry-run build on 3.3.13):

| Library | Boot 2.7.18 | Boot 3.3.13 | Note |
|---|---|---|---|
| Spring Framework / Security | 5.3.31 / 5.7.11 | 6.1.21 / 6.3.10 | Security DSL changes, see below |
| Hibernate ORM | 5.6.15.Final (`org.hibernate`) | 6.5.3.Final (`org.hibernate.orm`) | groupId change; native queries, see below |
| Hibernate Validator | 6.2.5.Final | 8.0.2.Final | `jakarta.validation` 3.0 |
| Tomcat embed | 9.0.83 | 10.1.42 | Servlet 6 / `jakarta.servlet` |
| Flyway | 8.5.13 | 10.10.0 | **Postgres support moved to a separate artifact**: add `org.flywaydb:flyway-database-postgresql` |
| PostgreSQL JDBC | 42.3.8 | 42.7.7 | |
| HikariCP / Lettuce / Micrometer | 4.0.3 / 6.1.10 / 1.9.17 | 5.1.0 / 6.3.2 / 1.13.15 | |
| Jackson | 2.13.5 | 2.17.3 | |
| H2 (test) | 2.1.214 | managed | unused; there are no tests |
| `jakarta.*` API jars already on the classpath (`jakarta.persistence-api` 2.2.3, `jakarta.validation-api` 2.0.2, `jakarta.annotation-api` 1.3.5, `jakarta.transaction-api` 1.3.3, `jakarta.xml.bind-api` 2.3.3) | EE 8 artifacts, still `javax.*` packages | EE 10 artifacts, `jakarta.*` packages | come in via starters; no direct declarations to change |

Non-Spring-managed (version pinned in this `pom.xml`, or not in the Boot BOM):

| Library | Current | Java 21 minimum | jakarta minimum | Action |
|---|---|---|---|---|
| `org.projectlombok:lombok` (pinned by `lombok.version`) | 1.18.36 | 1.18.30 | n/a (no EE dependency) | **OK as is.** Verified: the current pom compiles with `release 21` on JDK 21. Optionally drop the pin and use Boot's managed version. |
| `org.mapstruct:mapstruct` + processor | 1.5.5.Final | 1.5.5.Final works (verified: compiles on JDK 21, `PatientMapperImpl` generated) | n/a (`componentModel = "spring"`; no `javax` in the jar) | **OK as is.** Dry run used 1.6.3 + `lombok-mapstruct-binding` 0.2.0 without issues. |
| `org.springdoc:springdoc-openapi-ui` | 1.7.0 | – | **no jakarta version** of this artifact (1.x is `javax`-only) | **Replace** with `org.springdoc:springdoc-openapi-starter-webmvc-ui` **2.6.0** (the 2.x line for Boot 3.3; 2.0.0 is the first jakarta release). Swagger UI moved to 5.x; verified `/api/swagger-ui/index.html` → 200 on Boot 3.3. |
| `ca.uhn.hapi:hapi-base`, `hapi-structures-v24` (`hapi.version`) | 2.3 | 2.3 runs (no bytecode tricks; app starts on 21) | n/a — the jars reference no Jakarta EE / `javax.*` EE packages | **OK as is**; 2.5.1 is current if a bump is wanted. Brings `joda-time` 2.1 transitively (pure Java, fine). |
| `io.jsonwebtoken:jjwt-api/impl/jackson` | 0.11.5 | 0.11.5 | n/a — no `javax.xml.bind` use since 0.10 | **OK as is.** 0.12.x deprecates `parserBuilder()`/`setSigningKey` (API change, not required; open PR #112 does this). |

No typical offenders are present: no springfox, Hystrix, Sleuth, Swagger 2 annotations (`io.swagger.annotations`), JAXB/JAX-WS
clients, EhCache 2 or Commons FileUpload.

### `javax.*` imports by package

44 import lines in 33 of 91 main files (`rg '^import javax\.' src/main/java`). Paths below are relative to
`src/main/java/com/medchart/ehr/`.

**Moves to `jakarta.*` (Jakarta EE):**

| Package | Imports | Files |
|---|---|---|
| `javax.persistence` → `jakarta.persistence` | 32: `.*` ×21, `EntityManager` ×3, `Query` ×3, `Column` ×2, `Embeddable` ×2, `EntityNotFoundException` ×1 | 27: `audit/AuditEvent`, `domain/auth/User`, `domain/chronic/{ChronicCondition,DiabetesManagement,MedicationAdherence}`, `domain/clinical/{Allergy,ClinicalNote,Diagnosis,Vitals}`, `domain/encounter/{Encounter,EncounterDiagnosis}`, `domain/insurance/InsuranceCoverage`, `domain/medication/{Medication,MedicationAdministration,MedicationOrder}`, `domain/order/{LabOrder,LabResult}`, `domain/patient/{Address,EmergencyContact,Patient,PatientIdentifier}`, `domain/provider/{License,Provider}`, `legacy/{EncounterExportService,LegacyPatientLookup,ReportGenerator}`, `service/PatientService` |
| `javax.validation` → `jakarta.validation` | 6: `Valid` ×2, `constraints.NotBlank` ×2, `constraints.Past` ×2 | 4: `controller/{AuthController,PatientController}`, `domain/patient/Patient`, `dto/PatientDTO` |
| `javax.servlet` → `jakarta.servlet` | 5: `FilterChain`, `ServletException`, `http.HttpServletRequest` ×2, `http.HttpServletResponse` | 2: `config/JwtAuthenticationFilter`, `audit/AuditAspect` |

None found: `javax.annotation`, `javax.transaction`, `javax.inject`, `javax.xml.bind`, `javax.ws.rs`, `javax.mail`,
`javax.activation`. No fully-qualified `javax.` references outside imports, and none in `src/main/resources`.

**Stays `javax.*` (JDK):**

| Package | Imports | Files |
|---|---|---|
| `javax.crypto` | 1: `SecretKey` | `config/JwtTokenProvider` |

### Boot 2.7 deprecations and behaviour changes in use

| Item | Found | Detail |
|---|---|---|
| `WebSecurityConfigurerAdapter` | **No** | `config/SecurityConfig` already exposes a `SecurityFilterChain` bean. |
| `spring.factories` auto-configuration | **No** | No `META-INF/spring.factories` (or `AutoConfiguration.imports`) in the repo. |
| `antMatchers` / `authorizeRequests` / `.and()` chaining | **Yes** | `SecurityConfig`: `.cors().and().csrf().disable().sessionManagement()....and().authorizeRequests().antMatchers("/**").permitAll()`. `antMatchers` is removed in Security 6; the rest is deprecated. OpenRewrite rewrites it to the lambda DSL with `authorizeHttpRequests(r -> r.requestMatchers("/**").permitAll())`, which compiles and keeps today's permit-all behaviour. |
| Trailing-slash URL matching | **Behaviour change confirmed; no in-repo caller relies on it** | No `@*Mapping` path ends in `/`, no `PathMatchConfigurer`/`setUseTrailingSlashMatch`, and the frontend client (`frontend/src/api/*.ts`) never sends a trailing slash. At runtime `GET /api/v1/patients/search/?q=a` → **200 on Boot 2.7, 404 on Boot 3.3**. External consumers that append `/` will break; decide in s3.5 whether to add a redirect/`UrlHandlerFilter` or accept it. |
| Renamed properties (`spring-boot-properties-migrator`) | **Yes, 2 keys** | Run on the Boot 3.3 dry-run build with the migrator on the runtime classpath, started against Postgres: `spring.redis.host` → `spring.data.redis.host` (application.yml line 26), `spring.redis.port` → `spring.data.redis.port` (line 27). No other renamed/removed keys reported for `application.yml`. `application-dev.yml` has no `spring.redis.*` keys; its `spring.autoconfigure.exclude` class names (`RedisAutoConfiguration`, `RedisRepositoriesAutoConfiguration`) still exist in Boot 3.3. The equivalent run on Boot 2.7 could not start (no DB configured for that run) and is superseded by the 3.3 run. |
| Hibernate dialect | Warning only | Boot 3.3 logs `HHH90000025: PostgreSQLDialect does not need to be specified explicitly`; remove `spring.jpa.properties.hibernate.dialect` from both YAML files. |
| Native queries (Hibernate 6) | Checked, no difference | `legacy/*` issues 11 `createQuery`/`createNativeQuery` calls with positional parameters and `Object[]` results. On the same data, `/api/v1/export/encounters` (CSV) and `/api/v1/export/reports/daily` are **byte-identical** between Boot 2.7 and 3.3, and `/reports/encounter-summary` differs only in its `Generated:` timestamp precision (JDK 21 clock gives nanoseconds). Still owned by s3.4 because not every legacy path was exercised. |

Other Hibernate 6 notes for s3.4: all 21 generated ids use `GenerationType.IDENTITY` (unaffected by the Hibernate 6 sequence
default change); `@CreationTimestamp`/`@UpdateTimestamp` keep working; no `@Type`/`@TypeDef` usage.

### Build plugins and agents

| Item | Boot 2.7 effective | Needed for Java 21 | Boot 3.3 managed |
|---|---|---|---|
| `maven-compiler-plugin` | 3.11.0 (pinned in pom, `<release>${java.version}</release>`) | any version with `<release>` support; 3.11.0 verified on JDK 21 | 3.13.0 |
| `maven-surefire-plugin` / `failsafe` | 2.22.2 | 2.22.2 runs, but ≥ 3.0 recommended for JUnit 5.10 / JDK 21 | 3.2.5 |
| `spring-boot-maven-plugin` | 2.7.18 | follows parent | 3.3.x |
| `maven-jar-plugin` | 3.2.2 | fine | 3.4.2 |
| JaCoCo | **not configured** | ≥ 0.8.11 if added (first release that reads class file 65) | not managed |
| APM / `-javaagent` (New Relic, Datadog, Elastic, OpenTelemetry) | **none** in pom, CI, `start*.sh`, `docker-compose.yml` | – | – |
| CI (`.github/workflows/ci.yml`) | `setup-java@v4`, Temurin **11** | switch to `21` (s2.3) | – |
| Byte Buddy (via Hibernate/Mockito) | 1.12.23 | comes with the Boot 3.3 BOM; no pin needed | 1.14.19 |

### OpenRewrite hypothesis

**Confirmed, with caveats.** `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3`
(`rewrite-maven-plugin` 6.46.1, `rewrite-spring` 6.37.1, `rewrite-migrate-java` 3.42.0) on a copy of this commit:

- Rewrote every Jakarta EE import above (all 32 files) and left `javax.crypto.SecretKey` alone; the whole Java diff was 34 files, +270/−58.
- Upgraded the parent to 3.3.13, springdoc to `springdoc-openapi-starter-webmvc-ui` 2.6.0, MapStruct 1.6.3, Lombok 1.18.48,
  added `flyway-database-postgresql`, migrated the Redis keys and the `SecurityConfig` DSL.
- Result compiled and started on JDK 21; `/api/actuator/health`, `/api/swagger-ui/index.html`, patient search and the
  legacy CSV exports all returned 200.

Caveats for s3.1:

- It targets **Java 17**, not 21: the recipe chain includes `UpgradeToJava17`/`UpgradeBuildToJava17`, which set CI to
  `java-version: '17'` and rewrote the Maven Java-version properties. Pin 21 explicitly in s3.1 (or also run
  `org.openrewrite.java.migrate.UpgradeToJava21`).
- It adds unrelated Java 17 language rewrites (pattern-matching `instanceof` in `AuditAspect`, text blocks in
  `MedicationNotificationService`) — review or exclude them to keep the upgrade diff reviewable.
- It adds an explicit `jakarta.servlet:jakarta.servlet-api` dependency that the starters already provide; drop it.
- It does not handle the trailing-slash change, the dialect warning, or the jjwt 0.12 API (none required).

<details>
<summary>Full <code>mvn dependency:tree</code> (Boot 2.7.18, base commit)</summary>

```text
com.medchart:medchart-ehr-api:jar:3.2.1
+- org.springframework.boot:spring-boot-starter-web:jar:2.7.18:compile
|  +- org.springframework.boot:spring-boot-starter:jar:2.7.18:compile
|  |  +- org.springframework.boot:spring-boot:jar:2.7.18:compile
|  |  +- org.springframework.boot:spring-boot-autoconfigure:jar:2.7.18:compile
|  |  +- org.springframework.boot:spring-boot-starter-logging:jar:2.7.18:compile
|  |  |  +- ch.qos.logback:logback-classic:jar:1.2.12:compile
|  |  |  |  \- ch.qos.logback:logback-core:jar:1.2.12:compile
|  |  |  +- org.apache.logging.log4j:log4j-to-slf4j:jar:2.17.2:compile
|  |  |  |  \- org.apache.logging.log4j:log4j-api:jar:2.17.2:compile
|  |  |  \- org.slf4j:jul-to-slf4j:jar:1.7.36:compile
|  |  +- jakarta.annotation:jakarta.annotation-api:jar:1.3.5:compile
|  |  \- org.yaml:snakeyaml:jar:1.30:compile
|  +- org.springframework.boot:spring-boot-starter-json:jar:2.7.18:compile
|  |  +- com.fasterxml.jackson.datatype:jackson-datatype-jdk8:jar:2.13.5:compile
|  |  +- com.fasterxml.jackson.datatype:jackson-datatype-jsr310:jar:2.13.5:compile
|  |  \- com.fasterxml.jackson.module:jackson-module-parameter-names:jar:2.13.5:compile
|  +- org.springframework.boot:spring-boot-starter-tomcat:jar:2.7.18:compile
|  |  +- org.apache.tomcat.embed:tomcat-embed-core:jar:9.0.83:compile
|  |  \- org.apache.tomcat.embed:tomcat-embed-websocket:jar:9.0.83:compile
|  +- org.springframework:spring-web:jar:5.3.31:compile
|  |  \- org.springframework:spring-beans:jar:5.3.31:compile
|  \- org.springframework:spring-webmvc:jar:5.3.31:compile
|     +- org.springframework:spring-context:jar:5.3.31:compile
|     \- org.springframework:spring-expression:jar:5.3.31:compile
+- org.springframework.boot:spring-boot-starter-data-jpa:jar:2.7.18:compile
|  +- org.springframework.boot:spring-boot-starter-jdbc:jar:2.7.18:compile
|  |  +- com.zaxxer:HikariCP:jar:4.0.3:compile
|  |  \- org.springframework:spring-jdbc:jar:5.3.31:compile
|  +- jakarta.transaction:jakarta.transaction-api:jar:1.3.3:compile
|  +- jakarta.persistence:jakarta.persistence-api:jar:2.2.3:compile
|  +- org.hibernate:hibernate-core:jar:5.6.15.Final:compile
|  |  +- org.jboss.logging:jboss-logging:jar:3.4.3.Final:compile
|  |  +- net.bytebuddy:byte-buddy:jar:1.12.23:compile
|  |  +- antlr:antlr:jar:2.7.7:compile
|  |  +- org.jboss:jandex:jar:2.4.2.Final:compile
|  |  +- com.fasterxml:classmate:jar:1.5.1:compile
|  |  +- org.hibernate.common:hibernate-commons-annotations:jar:5.1.2.Final:compile
|  |  \- org.glassfish.jaxb:jaxb-runtime:jar:2.3.9:compile
|  |     +- org.glassfish.jaxb:txw2:jar:2.3.9:compile
|  |     +- com.sun.istack:istack-commons-runtime:jar:3.0.12:compile
|  |     \- com.sun.activation:jakarta.activation:jar:1.2.2:runtime
|  +- org.springframework.data:spring-data-jpa:jar:2.7.18:compile
|  |  +- org.springframework.data:spring-data-commons:jar:2.7.18:compile
|  |  +- org.springframework:spring-orm:jar:5.3.31:compile
|  |  \- org.springframework:spring-tx:jar:5.3.31:compile
|  \- org.springframework:spring-aspects:jar:5.3.31:compile
+- org.springframework.boot:spring-boot-starter-validation:jar:2.7.18:compile
|  +- org.apache.tomcat.embed:tomcat-embed-el:jar:9.0.83:compile
|  \- org.hibernate.validator:hibernate-validator:jar:6.2.5.Final:compile
|     \- jakarta.validation:jakarta.validation-api:jar:2.0.2:compile
+- org.springframework.boot:spring-boot-starter-security:jar:2.7.18:compile
|  +- org.springframework:spring-aop:jar:5.3.31:compile
|  +- org.springframework.security:spring-security-config:jar:5.7.11:compile
|  \- org.springframework.security:spring-security-web:jar:5.7.11:compile
+- org.springframework.boot:spring-boot-starter-data-redis:jar:2.7.18:compile
|  +- org.springframework.data:spring-data-redis:jar:2.7.18:compile
|  |  +- org.springframework.data:spring-data-keyvalue:jar:2.7.18:compile
|  |  +- org.springframework:spring-oxm:jar:5.3.31:compile
|  |  \- org.springframework:spring-context-support:jar:5.3.31:compile
|  \- io.lettuce:lettuce-core:jar:6.1.10.RELEASE:compile
|     +- io.netty:netty-common:jar:4.1.101.Final:compile
|     +- io.netty:netty-handler:jar:4.1.101.Final:compile
|     |  +- io.netty:netty-resolver:jar:4.1.101.Final:compile
|     |  +- io.netty:netty-buffer:jar:4.1.101.Final:compile
|     |  +- io.netty:netty-transport-native-unix-common:jar:4.1.101.Final:compile
|     |  \- io.netty:netty-codec:jar:4.1.101.Final:compile
|     +- io.netty:netty-transport:jar:4.1.101.Final:compile
|     \- io.projectreactor:reactor-core:jar:3.4.34:compile
|        \- org.reactivestreams:reactive-streams:jar:1.0.4:compile
+- org.springframework.boot:spring-boot-starter-aop:jar:2.7.18:compile
|  \- org.aspectj:aspectjweaver:jar:1.9.7:compile
+- org.springframework.boot:spring-boot-starter-actuator:jar:2.7.18:compile
|  +- org.springframework.boot:spring-boot-actuator-autoconfigure:jar:2.7.18:compile
|  |  \- org.springframework.boot:spring-boot-actuator:jar:2.7.18:compile
|  \- io.micrometer:micrometer-core:jar:1.9.17:compile
|     +- org.hdrhistogram:HdrHistogram:jar:2.1.12:compile
|     \- org.latencyutils:LatencyUtils:jar:2.0.3:runtime
+- org.postgresql:postgresql:jar:42.3.8:runtime
|  \- org.checkerframework:checker-qual:jar:3.5.0:runtime
+- org.flywaydb:flyway-core:jar:8.5.13:compile
+- org.projectlombok:lombok:jar:1.18.36:compile
+- org.mapstruct:mapstruct:jar:1.5.5.Final:compile
+- org.springdoc:springdoc-openapi-ui:jar:1.7.0:compile
|  +- org.springdoc:springdoc-openapi-webmvc-core:jar:1.7.0:compile
|  |  \- org.springdoc:springdoc-openapi-common:jar:1.7.0:compile
|  |     \- io.swagger.core.v3:swagger-core:jar:2.2.9:compile
|  |        +- org.apache.commons:commons-lang3:jar:3.12.0:compile
|  |        +- com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:jar:2.13.5:compile
|  |        +- io.swagger.core.v3:swagger-annotations:jar:2.2.9:compile
|  |        \- io.swagger.core.v3:swagger-models:jar:2.2.9:compile
|  \- org.webjars:swagger-ui:jar:4.18.2:compile
+- ca.uhn.hapi:hapi-base:jar:2.3:compile
|  +- org.slf4j:slf4j-api:jar:1.7.36:compile
|  \- joda-time:joda-time:jar:2.1:compile
+- ca.uhn.hapi:hapi-structures-v24:jar:2.3:compile
+- io.jsonwebtoken:jjwt-api:jar:0.11.5:compile
+- io.jsonwebtoken:jjwt-impl:jar:0.11.5:runtime
+- io.jsonwebtoken:jjwt-jackson:jar:0.11.5:runtime
|  \- com.fasterxml.jackson.core:jackson-databind:jar:2.13.5:compile
|     +- com.fasterxml.jackson.core:jackson-annotations:jar:2.13.5:compile
|     \- com.fasterxml.jackson.core:jackson-core:jar:2.13.5:compile
+- com.h2database:h2:jar:2.1.214:test
+- org.springframework.boot:spring-boot-starter-test:jar:2.7.18:test
|  +- org.springframework.boot:spring-boot-test:jar:2.7.18:test
|  +- org.springframework.boot:spring-boot-test-autoconfigure:jar:2.7.18:test
|  +- com.jayway.jsonpath:json-path:jar:2.7.0:test
|  |  \- net.minidev:json-smart:jar:2.4.11:test
|  |     \- net.minidev:accessors-smart:jar:2.4.11:test
|  |        \- org.ow2.asm:asm:jar:9.3:test
|  +- jakarta.xml.bind:jakarta.xml.bind-api:jar:2.3.3:compile
|  |  \- jakarta.activation:jakarta.activation-api:jar:1.2.2:compile
|  +- org.assertj:assertj-core:jar:3.22.0:test
|  +- org.hamcrest:hamcrest:jar:2.2:test
|  +- org.junit.jupiter:junit-jupiter:jar:5.8.2:test
|  |  +- org.junit.jupiter:junit-jupiter-api:jar:5.8.2:test
|  |  |  +- org.opentest4j:opentest4j:jar:1.2.0:test
|  |  |  +- org.junit.platform:junit-platform-commons:jar:1.8.2:test
|  |  |  \- org.apiguardian:apiguardian-api:jar:1.1.2:test
|  |  +- org.junit.jupiter:junit-jupiter-params:jar:5.8.2:test
|  |  \- org.junit.jupiter:junit-jupiter-engine:jar:5.8.2:test
|  |     \- org.junit.platform:junit-platform-engine:jar:1.8.2:test
|  +- org.mockito:mockito-core:jar:4.5.1:test
|  |  +- net.bytebuddy:byte-buddy-agent:jar:1.12.23:test
|  |  \- org.objenesis:objenesis:jar:3.2:test
|  +- org.mockito:mockito-junit-jupiter:jar:4.5.1:test
|  +- org.skyscreamer:jsonassert:jar:1.5.1:test
|  |  \- com.vaadin.external.google:android-json:jar:0.0.20131108.vaadin1:test
|  +- org.springframework:spring-core:jar:5.3.31:compile
|  |  \- org.springframework:spring-jcl:jar:5.3.31:compile
|  +- org.springframework:spring-test:jar:5.3.31:test
|  \- org.xmlunit:xmlunit-core:jar:2.9.1:test
\- org.springframework.security:spring-security-test:jar:5.7.11:test
   \- org.springframework.security:spring-security-core:jar:5.7.11:compile
      \- org.springframework.security:spring-security-crypto:jar:5.7.11:compile
```

</details>
