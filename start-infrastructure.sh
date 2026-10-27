#!/bin/bash

# CogHealth EHR - Docker Infrastructure Startup Script

set -e

echo "=========================================="
echo "  CogHealth EHR Infrastructure Setup"
echo "=========================================="

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi

echo "✅ Docker is running"

# Navigate to script directory
cd "$(dirname "$0")"

if docker compose version > /dev/null 2>&1; then
    COMPOSE="docker compose"
else
    COMPOSE="docker-compose"
fi

# Credentials live in an untracked .env file; generate one on first run.
if [ ! -f .env ]; then
    echo "Generating .env with random credentials (git-ignored)..."
    gen() { openssl rand -base64 24 | tr -d '/+='; }
    (
        umask 077
        while IFS= read -r line; do
            case "$line" in
                *PASSWORD=*|KC_API_CLIENT_SECRET=*) echo "${line%%=*}=$(gen)" ;;
                *) echo "$line" ;;
            esac
        done < .env.example > .env
    )
fi
# Read values without sourcing .env, so its contents are never executed as shell.
env_value() {
    local v
    v=$(grep -E "^[[:space:]]*$1=" .env | tail -n 1 | cut -d= -f2-)
    v="${v%\"}"; v="${v#\"}"; v="${v%\'}"; v="${v#\'}"
    printf '%s' "$v"
}
RABBITMQ_USER=$(env_value RABBITMQ_USER)
RABBITMQ_PASSWORD=$(env_value RABBITMQ_PASSWORD)

# Stop any existing containers
echo ""
echo "Stopping any existing containers..."
$COMPOSE down 2>/dev/null || true

# Start infrastructure
echo ""
echo "Starting infrastructure services..."
echo "This may take a few minutes on first run (downloading images)..."
echo ""

$COMPOSE up -d

# Wait for services to be healthy
echo ""
echo "Waiting for services to be ready..."

# Wait for PostgreSQL
echo -n "  PostgreSQL: "
until docker exec coghealth-postgres pg_isready -U coghealth -d coghealth > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo " ✅"

# Wait for Redis
echo -n "  Redis: "
until docker exec coghealth-redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping' 2>/dev/null | grep -q PONG; do
    echo -n "."
    sleep 2
done
echo " ✅"

# Wait for Keycloak
echo -n "  Keycloak: "
until curl -s http://localhost:8180/health/ready > /dev/null 2>&1; do
    echo -n "."
    sleep 3
done
echo " ✅"

# Wait for RabbitMQ
echo -n "  RabbitMQ: "
until curl -sf -u "${RABBITMQ_USER:-coghealth}:$RABBITMQ_PASSWORD" http://127.0.0.1:15672/api/overview > /dev/null 2>&1; do
    echo -n "."
    sleep 3
done
echo " ✅"

# Wait for Elasticsearch
echo -n "  Elasticsearch: "
until curl -s http://localhost:9200/_cluster/health > /dev/null 2>&1; do
    echo -n "."
    sleep 3
done
echo " ✅"

echo ""
echo "=========================================="
echo "  All services are running!"
echo "=========================================="
echo ""
echo "Service URLs (bound to 127.0.0.1 only; credentials are in .env):"
echo "  • PostgreSQL:    localhost:5432 (user coghealth, POSTGRES_PASSWORD)"
echo "  • Redis:         localhost:6379 (REDIS_PASSWORD)"
echo "  • Keycloak:      http://localhost:8180 (KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD)"
echo "  • RabbitMQ:      http://localhost:15672 (RABBITMQ_USER / RABBITMQ_PASSWORD)"
echo "  • Elasticsearch: http://localhost:9200"
echo "  • Kibana:        http://localhost:5601"
echo ""
echo "Demo Users (password: KC_DEMO_USER_PASSWORD):"
echo "  • dr.anderson      - Physician"
echo "  • nurse.johnson    - Nurse"
echo "  • ma.smith         - Medical Assistant"
echo "  • frontdesk.wilson - Front Desk"
echo "  • billing.garcia   - Billing"
echo "  • compliance.lee   - Compliance Officer"
echo "  • admin (password: KC_REALM_ADMIN_PASSWORD) - System Admin"
echo ""
echo "To stop: $COMPOSE down"
echo "To view logs: $COMPOSE logs -f [service]"
echo ""
