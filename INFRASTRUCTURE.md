# CogHealth EHR Infrastructure

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                 CLIENTS                                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                  │
│  │  Web Browser    │  │  Mobile App     │  │  HL7 Interface  │                  │
│  │  (React)        │  │  (Future)       │  │  (Mirth)        │                  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘                  │
└───────────┼─────────────────────┼─────────────────────┼──────────────────────────┘
            │                     │                     │
            ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              API GATEWAY                                         │
│                         (Spring Boot :8080)                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  Authentication │ Rate Limiting │ Request Logging │ CORS │ Routing     │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           IDENTITY & ACCESS                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                    Keycloak (:8180)                                      │    │
│  │  • OAuth 2.0 / OpenID Connect                                           │    │
│  │  • Role-Based Access Control (RBAC)                                     │    │
│  │  • Multi-Factor Authentication                                          │    │
│  │  • Session Management                                                   │    │
│  │  • Audit Logging                                                        │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  Roles: physician | nurse | medical_assistant | front_desk | billing | admin    │
└─────────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          APPLICATION SERVICES                                    │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐           │
│  │   Patient    │ │   Clinical   │ │  Medication  │ │   Billing    │           │
│  │   Service    │ │   Service    │ │   Service    │ │   Service    │           │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘           │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐           │
│  │  Scheduling  │ │   Chronic    │ │   Audit      │ │  Reporting   │           │
│  │   Service    │ │   Care Svc   │ │   Service    │ │   Service    │           │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘           │
└─────────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            DATA LAYER                                            │
│                                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐              │
│  │   PostgreSQL     │  │     Redis        │  │  Elasticsearch   │              │
│  │   (:5432)        │  │    (:6379)       │  │    (:9200)       │              │
│  │                  │  │                  │  │                  │              │
│  │  • Patient data  │  │  • Session cache │  │  • Audit logs    │              │
│  │  • Clinical data │  │  • API cache     │  │  • Search index  │              │
│  │  • Orders        │  │  • Rate limiting │  │  • Analytics     │              │
│  │  • Audit trail   │  │                  │  │                  │              │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘              │
└─────────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         INTEGRATION LAYER                                        │
│                                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐              │
│  │    RabbitMQ      │  │    HAPI FHIR     │  │    Kibana        │              │
│  │   (:5672/15672)  │  │    (:8090)       │  │    (:5601)       │              │
│  │                  │  │                  │  │                  │              │
│  │  • HL7 messages  │  │  • FHIR R4 API   │  │  • Log viewer    │              │
│  │  • Async tasks   │  │  • Interop       │  │  • Dashboards    │              │
│  │  • Notifications │  │  • Data exchange │  │  • Audit review  │              │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘              │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
# Start all infrastructure
./start-infrastructure.sh

# Or manually with docker-compose
docker-compose up -d

# Stop everything
docker-compose down

# Stop and remove all data
docker-compose down -v
```

## Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| PostgreSQL | `localhost:5432` | `coghealth` / `coghealth_dev_2024` |
| Redis | `localhost:6379` | No auth (dev mode) |
| Keycloak Admin | http://localhost:8180 | `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` from `.env` |
| RabbitMQ Management | http://localhost:15672 | `coghealth` / `coghealth_dev_2024` |
| Elasticsearch | http://localhost:9200 | No auth (dev mode) |
| Kibana | http://localhost:5601 | No auth (dev mode) |
| HAPI FHIR | http://localhost:8090/fhir | No auth (dev mode) |

## Demo Users

Passwords are not committed. Keycloak substitutes them from the environment when it imports
`docker/keycloak/coghealth-realm.json`; `./start-infrastructure.sh` generates random values in the
git-ignored `.env` on first run (see `.env.example`):

- Demo users: `KC_DEMO_USER_PASSWORD`
- `admin`: `KC_REALM_ADMIN_PASSWORD` (temporary, must be changed at first login)

These accounts exist only in Keycloak. The bundled frontend does not use Keycloak yet (it logs in
through the API's `/auth/login`). For Keycloak clients, the direct password grant
(`grant_type=password`) is disabled on both `coghealth-api` and `coghealth-web`; sign in through the
Keycloak login page (authorization code flow).

Keycloak only imports the realm when it does not exist yet. A database that imported an older
version of `coghealth-realm.json` keeps its old users, client secret and grant settings; reset it
with `docker compose down -v` (this deletes local data) so the realm is imported again.

| Username | Role | Access Level |
|----------|------|--------------|
| `dr.anderson` | Physician | Full clinical access, prescribing |
| `nurse.johnson` | Nurse | Clinical access, limited prescribing |
| `ma.smith` | Medical Assistant | Vitals, scheduling, limited charts |
| `frontdesk.wilson` | Front Desk | Scheduling, demographics only |
| `billing.garcia` | Billing | Claims, payments, limited PHI |
| `compliance.lee` | Compliance Officer | Audit logs, reports, read-only |
| `admin` | System Admin | Full system access |

## Role-Based Access Control (RBAC)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ACCESS MATRIX                                         │
├─────────────────┬──────┬───────┬─────┬───────┬─────────┬───────┬───────────┤
│ Resource        │ Phys │ Nurse │ MA  │ Front │ Billing │ Compl │ Admin     │
├─────────────────┼──────┼───────┼─────┼───────┼─────────┼───────┼───────────┤
│ Patient Demo    │  RW  │  RW   │  R  │   RW  │    R    │   R   │    RW     │
│ Clinical Notes  │  RW  │  RW   │  -  │   -   │    -    │   R   │    RW     │
│ Medications     │  RW  │  R    │  R  │   -   │    R    │   R   │    RW     │
│ Prescribe       │  ✓   │  ✗    │  ✗  │   ✗   │    ✗    │   ✗   │    ✗      │
│ Controlled Rx   │  ✓*  │  ✗    │  ✗  │   ✗   │    ✗    │   ✗   │    ✗      │
│ Lab Orders      │  RW  │  RW   │  R  │   -   │    R    │   R   │    RW     │
│ Scheduling      │  RW  │  RW   │  RW │   RW  │    R    │   R   │    RW     │
│ Billing/Claims  │  R   │  -    │  -  │   -   │    RW   │   R   │    RW     │
│ Audit Logs      │  -   │  -    │  -  │   -   │    -    │   R   │    RW     │
│ System Config   │  -   │  -    │  -  │   -   │    -    │   -   │    RW     │
├─────────────────┴──────┴───────┴─────┴───────┴─────────┴───────┴───────────┤
│ R = Read, W = Write, RW = Read/Write, - = No Access, * = Requires DEA      │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Data Flow

### Authentication Flow
```
User → Frontend → Keycloak → JWT Token → API → Validate Token → Process Request
```

### PHI Access Flow
```
Request → Auth Check → RBAC Check → Audit Log → Data Access → Response → Audit Log
```

### HL7/FHIR Integration Flow
```
External System → RabbitMQ → Message Processor → Validate → Transform → Store → Acknowledge
```

## Environment Variables

The API expects these environment variables (set in application.yml or docker-compose):

```yaml
# Database
SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/coghealth
SPRING_DATASOURCE_USERNAME: coghealth
SPRING_DATASOURCE_PASSWORD: coghealth_dev_2024

# Redis
SPRING_REDIS_HOST: localhost
SPRING_REDIS_PORT: 6379

# Keycloak
KEYCLOAK_AUTH_SERVER_URL: http://localhost:8180
KEYCLOAK_REALM: coghealth
KEYCLOAK_RESOURCE: coghealth-api
KEYCLOAK_CREDENTIALS_SECRET: ${KC_API_CLIENT_SECRET}  # from .env, never committed

# RabbitMQ
SPRING_RABBITMQ_HOST: localhost
SPRING_RABBITMQ_PORT: 5672
SPRING_RABBITMQ_USERNAME: coghealth
SPRING_RABBITMQ_PASSWORD: coghealth_dev_2024

# Elasticsearch
ELASTICSEARCH_HOST: localhost
ELASTICSEARCH_PORT: 9200

# FHIR
HAPI_FHIR_URL: http://localhost:8090/fhir
```

## Troubleshooting

### Services won't start
```bash
# Check Docker is running
docker info

# Check for port conflicts
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :8180  # Keycloak

# View logs
docker-compose logs -f [service_name]
```

### Reset everything
```bash
docker-compose down -v
docker system prune -f
./start-infrastructure.sh
```

### Memory issues
Elasticsearch needs at least 512MB. If Docker is constrained:
1. Open Docker Desktop → Settings → Resources
2. Increase Memory to at least 4GB
