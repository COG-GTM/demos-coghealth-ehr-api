# CogHealth EHR API

Spring Boot REST API for the CogHealth EHR system.

> **Full setup instructions:** See the [main README](https://github.com/COG-GTM/demos-coghealth-ehr-data#readme) for complete setup with all three repos.

## Quick Start (Neon Cloud DB)

No local database setup required. The `dev` profile connects to a shared Neon PostgreSQL instance with pre-seeded test data.

**Prerequisites:** Java 11 (required — later versions are incompatible)

```bash
# Point JAVA_HOME at a Java 11 JDK (use the line for your OS)
export JAVA_HOME=/opt/homebrew/opt/openjdk@11            # macOS with Homebrew
# export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64    # Debian/Ubuntu (apt install openjdk-11-jdk maven); -arm64 on ARM
export PATH="$JAVA_HOME/bin:$PATH"

# Set Neon credentials (get these from your team lead or .env file)
export NEON_DB_URL="jdbc:postgresql://<neon-host>/neondb?sslmode=require"
export NEON_DB_USERNAME="<username>"
export NEON_DB_PASSWORD="<password>"

# Run with dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

API runs at http://localhost:8080/api

### Quick Start (Local DB)

If you prefer a local PostgreSQL instance, use the default profile with Docker infrastructure from [demos-coghealth-ehr-data](https://github.com/COG-GTM/demos-coghealth-ehr-data). This repo ships the same `docker-compose.yml`, so you can also start it from here.

**Prerequisites:** Java 11 (set `JAVA_HOME` as above), Maven, Docker with Compose v2 (`docker compose`)

```bash
# Start PostgreSQL and Redis (database coghealth, user coghealth)
docker compose up -d --wait postgres redis

# Point the API at that database: the default profile otherwise expects
# medchart_ehr / medchart / medchart_dev, which the compose file does not create
export DB_URL="jdbc:postgresql://localhost:5432/coghealth"
export DB_USERNAME="coghealth"
export DB_PASSWORD="coghealth_dev_2024"

mvn spring-boot:run
```

Flyway creates the schema and loads the sample data (`V3__seed_data.sql`) on first start. Start from an empty database: don't load the data repo's `seed.sql` first, because it writes its own `flyway_schema_history` rows and startup then fails with `Migration checksum mismatch for migration version 1`. To reset only the database, run `docker compose rm -sf postgres`, then remove its volume (`docker volume ls -q --filter name=postgres_data` shows the name), and start again. Keycloak stores its data in the same database, so this clears it too.

## Tech Stack

- Java 11
- Spring Boot 2.7.x
- PostgreSQL (Neon cloud or local)
- Flyway (schema migrations)
- JJWT (authentication tokens)

## Database

### Neon Cloud (dev profile)

- Host: Neon cloud instance (see `.env` or ask your team lead for credentials)
- Database: `neondb`
- Region: EU Central (Frankfurt) — expect ~600-800ms latency from other regions
- Connection: Direct (non-pooled), supports Flyway advisory locks
- Schema managed by Flyway migrations in `src/main/resources/db/migration/`
- Hibernate `ddl-auto: validate` — won't modify the schema, only checks it matches entities

### Connection Pooling (HikariCP)

The dev profile tunes HikariCP for a remote database:
- `minimum-idle: 2` — keeps connections warm
- `maximum-pool-size: 5` — small pool for remote DB
- `keepalive-time: 60000` — pings every 60s to prevent Neon from dropping idle connections

## API Documentation

http://localhost:8080/api/swagger-ui/index.html

## Project Structure

```
src/main/java/com/medchart/ehr/
├── controller/      # REST controllers
├── domain/          # JPA entities
├── dto/             # Data transfer objects
├── repository/      # JPA repositories
├── service/         # Business logic
├── config/          # Security & JWT configuration
├── audit/           # HIPAA audit logging
└── mapper/          # Entity/DTO mappers
```

## Security

JWT-based authentication. CORS allows `localhost:5173`, `localhost:5178`, `localhost:3000`.

## Useful Commands

```bash
# Kill stuck API process
lsof -i :8080 | grep LISTEN | awk '{print $2}' | xargs kill -9

# Test API response time
curl -w "Time: %{time_total}s\n" -s -o /dev/null "http://localhost:8080/api/v1/patients/search?q=a&page=0&size=20"

# Connect to Neon directly
PGPASSWORD=$NEON_DB_PASSWORD psql "postgresql://$NEON_DB_USERNAME@<neon-host>/neondb?sslmode=require"
```
