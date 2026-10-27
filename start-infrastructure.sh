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

# Credentials live in an untracked .env file; generate one on first run.
if [ ! -f .env ]; then
    echo "Generating .env with random credentials (git-ignored)..."
    gen() { openssl rand -base64 24 | tr -d '/+='; }
    (
        umask 077
        while IFS= read -r line; do
            case "$line" in
                *PASSWORD=|*SECRET=) echo "${line}$(gen)" ;;
                *) echo "$line" ;;
            esac
        done < .env.example > .env
    )
fi

# Stop any existing containers
echo ""
echo "Stopping any existing containers..."
docker-compose down 2>/dev/null || true

# Start infrastructure
echo ""
echo "Starting infrastructure services..."
echo "This may take a few minutes on first run (downloading images)..."
echo ""

docker-compose up -d

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
until docker exec coghealth-redis redis-cli ping > /dev/null 2>&1; do
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
until curl -s -u coghealth:coghealth_dev_2024 http://localhost:15672/api/overview > /dev/null 2>&1; do
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

# Wait for HAPI FHIR
echo -n "  HAPI FHIR: "
until curl -s http://localhost:8090/fhir/metadata > /dev/null 2>&1; do
    echo -n "."
    sleep 3
done
echo " ✅"

echo ""
echo "=========================================="
echo "  All services are running!"
echo "=========================================="
echo ""
echo "Service URLs:"
echo "  • PostgreSQL:    localhost:5432 (coghealth/coghealth_dev_2024)"
echo "  • Redis:         localhost:6379"
echo "  • Keycloak:      http://localhost:8180 (KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD from .env)"
echo "  • RabbitMQ:      http://localhost:15672 (coghealth/coghealth_dev_2024)"
echo "  • Elasticsearch: http://localhost:9200"
echo "  • Kibana:        http://localhost:5601"
echo "  • HAPI FHIR:     http://localhost:8090/fhir"
echo ""
echo "Demo Users (password: KC_DEMO_USER_PASSWORD from .env):"
echo "  • dr.anderson      - Physician"
echo "  • nurse.johnson    - Nurse"
echo "  • ma.smith         - Medical Assistant"
echo "  • frontdesk.wilson - Front Desk"
echo "  • billing.garcia   - Billing"
echo "  • compliance.lee   - Compliance Officer"
echo "  • admin (password: KC_REALM_ADMIN_PASSWORD from .env, must be changed on first login) - System Admin"
echo ""
echo "To stop: docker-compose down"
echo "To view logs: docker-compose logs -f [service]"
echo ""
