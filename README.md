# CogHealth EHR API

Spring Boot REST API for the CogHealth EHR system.

> **Full setup instructions:** See the [main README](https://github.com/cogdeasy/demos-coghealth-ehr-data#readme) for complete setup with all three repos.

## Quick Start (Neon Cloud DB)

No local database setup required. The `dev` profile connects to a shared Neon PostgreSQL instance with pre-seeded test data.

**Prerequisites:** Java 17

```bash
# On macOS with Homebrew
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
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

If you prefer a local PostgreSQL instance, use the default profile with Docker infrastructure from [demos-coghealth-ehr-data](https://github.com/COG-GTM/demos-coghealth-ehr-data).

```bash
mvn spring-boot:run
```

## Tech Stack

- Java 17
- Spring Boot 3.3.x
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
