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
# Start all infrastructure (generates a git-ignored .env with random credentials on first run)
./start-infrastructure.sh

# Or manually: create .env from .env.example and fill in every value first
cp .env.example .env
docker compose up -d

# Stop everything
docker compose down

# Stop and remove all data
docker compose down -v
```

## Service URLs

Every published port is bound to `127.0.0.1`, so the services are only reachable from the machine running the
stack. All credentials come from the untracked `.env` file (see `.env.example`); `docker compose` refuses to start
while any required value is missing. Nothing in this repository contains a usable password.

| Service | URL | Credentials (`.env`) |
|---------|-----|----------------------|
| PostgreSQL | `127.0.0.1:5432` | `coghealth` / `POSTGRES_PASSWORD` |
| Redis | `127.0.0.1:6379` | `REDIS_PASSWORD` |
| Keycloak Admin | http://127.0.0.1:8180 | `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` |
| RabbitMQ Management | http://127.0.0.1:15672 | `RABBITMQ_USER` / `RABBITMQ_PASSWORD` |
| Elasticsearch | http://127.0.0.1:9200 | None (security off for local dev; loopback only) |
| Kibana | http://127.0.0.1:5601 | None (loopback only) |
| HAPI FHIR | http://127.0.0.1:8090/fhir | Disabled in compose |

Do not reuse this compose file or `.env` for a shared or production deployment.

## Demo Users

Imported into the `coghealth` realm from `docker/keycloak/coghealth-realm.json`, which only contains
`${KC_*}` placeholders. Keycloak substitutes them from `.env` on first import: demo users get
`KC_DEMO_USER_PASSWORD`, `admin` gets `KC_REALM_ADMIN_PASSWORD`, and the confidential `coghealth-api` client
secret is `KC_API_CLIENT_SECRET`. The realm requires TLS for non-local (`sslRequired: external`) requests.

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

The API expects these environment variables (values for the local stack come from `.env`):

```yaml
# Database
SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/coghealth
SPRING_DATASOURCE_USERNAME: coghealth
SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}

# Redis
SPRING_REDIS_HOST: localhost
SPRING_REDIS_PORT: 6379
SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD}

# Keycloak
KEYCLOAK_AUTH_SERVER_URL: http://localhost:8180
KEYCLOAK_REALM: coghealth
KEYCLOAK_RESOURCE: coghealth-api
KEYCLOAK_CREDENTIALS_SECRET: ${KC_API_CLIENT_SECRET}

# RabbitMQ
SPRING_RABBITMQ_HOST: localhost
SPRING_RABBITMQ_PORT: 5672
SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER}
SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}

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
docker compose logs -f [service_name]
```

### Reset everything
```bash
docker compose down -v
docker system prune -f
./start-infrastructure.sh
```

### Memory issues
Elasticsearch needs at least 512MB. If Docker is constrained:
1. Open Docker Desktop → Settings → Resources
2. Increase Memory to at least 4GB
