# CogHealth EHR API

Spring Boot REST API for the CogHealth EHR demo, with a React/Vite frontend workspace.
See the [architecture and infrastructure guide](INFRASTRUCTURE.md) for the runtime
diagram, PostgreSQL and Flyway, HR PTO sync, FHIR contract checks, and legacy exports.

## Quick start

Use JDK 11 and Maven for the API (the same Java version as CI). For the local profile,
install Docker with Compose v2. Use synthetic demo data only: the current API filter
chain does not require authentication.

### Local PostgreSQL and Redis

From the repository root, start the two infrastructure services and use the Compose
database settings. The API runs on the host, not in Compose.

```bash
docker compose up -d --wait postgres redis
export DB_URL='jdbc:postgresql://localhost:5432/coghealth'
export DB_USERNAME=coghealth
export DB_PASSWORD="$(docker compose exec -T postgres printenv POSTGRES_PASSWORD)"
mvn spring-boot:run
```

Flyway applies schema and seed migrations from [`src/main/resources/db/migration/`](src/main/resources/db/migration/).
To use another PostgreSQL instance, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`
for that instance instead. The default profile expects Redis on `localhost:6379`.

### Shared Neon PostgreSQL (optional)

Use the `dev` profile only if you have an approved shared demo database. It disables
Redis auto-configuration, runs Flyway, and validates the schema with Hibernate.

```bash
export NEON_DB_URL='jdbc:postgresql://<neon-host>/neondb?sslmode=require'
export NEON_DB_USERNAME='<username>'
export NEON_DB_PASSWORD='<password>'
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### API and frontend

- API base: [`http://localhost:8080/api`](http://localhost:8080/api)
- Swagger UI: [`http://localhost:8080/api/swagger-ui/index.html`](http://localhost:8080/api/swagger-ui/index.html)
- Health: [`http://localhost:8080/api/actuator/health`](http://localhost:8080/api/actuator/health)

In a separate terminal, use Node.js 20.19+ on the 20.x line, or Node.js 22.12+:

```bash
cd frontend
npm ci
npm run dev -- --host localhost --strictPort
```

Open [`http://localhost:5173`](http://localhost:5173). The frontend currently renders
the Vite starter screen; the EHR components are not wired into it. The API client
code defaults to `http://localhost:8080/api` and supports `VITE_API_URL`.

## Project structure

```text
src/main/java/com/medchart/ehr/
├── controller/        REST endpoints, including interfaces and exports
├── service/           Clinical and supporting services
├── domain/            JPA entities
├── repository/        Spring Data repositories
├── dto/, mapper/      REST data and entity mapping
├── config/, audit/    Security configuration, JWT code, and audit logging
├── legacy/            Batch exports, reports, and the HR PTO sync
└── interop/fhir/      FHIR R4 exporter and contract validator
src/main/resources/
├── application*.yml   Default and Neon dev profiles
├── db/migration/      Flyway schema and seed migrations
└── interfaces/hr/    Workday export fixtures and department mapping
src/test/              Backend unit and tagged interface contract tests
frontend/             Vite entry point, EHR components, hooks, and API client
docker-compose.yml    Local infrastructure, not application containers
docs/                 Architecture diagram, HR PTO runbook, migration baseline
scripts/              Standalone interface contract check
```

## Build and test

```bash
mvn -B clean verify                   # default backend tests and executable JAR
scripts/interface-contract.sh         # separate tagged suite; latest checked-in HR export
# Or select one export: scripts/interface-contract.sh YYYYMMDD
(cd frontend && npm ci && npm run build)
```

The frontend has no test script. See the [HR PTO runbook](docs/interfaces/hr-pto-sync.md)
for the contract report and workflow.
