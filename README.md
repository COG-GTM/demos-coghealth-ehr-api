# CogHealth EHR API

Spring Boot REST API for the CogHealth EHR system.

> **Full setup instructions:** See the [main README](https://github.com/cogdeasy/demos-coghealth-ehr-data#readme) for complete setup with all three repos.

## Quick Start

```bash
./start.sh
```

## Tech Stack

- Java 17
- Spring Boot 2.7.x
- PostgreSQL
- Redis

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
└── config/          # Configuration
```

## Security

Authentication is disabled for development. CORS allows `localhost:5173`, `localhost:5178`, `localhost:3000`.

<!-- Dummy change for testing PR workflow -->
