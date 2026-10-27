#!/bin/bash

echo "=== CogHealth EHR API ==="
echo ""
echo "Select database:"
echo "  1) Local PostgreSQL (Docker) - default"
echo "  2) Neon (cloud) - requires NEON_DB_* env vars"
echo ""
read -p "Choice [1]: " choice

case "${choice:-1}" in
  1)
    echo ""
    echo "Starting with local PostgreSQL..."
    echo "Make sure the infrastructure is running: ./start-infrastructure.sh"
    echo ""
    # Pass the Redis/RabbitMQ credentials generated in .env to the API (values are
    # read, not sourced; anything already exported wins).
    if [ -f .env ]; then
      for key in REDIS_PASSWORD RABBITMQ_USER RABBITMQ_PASSWORD; do
        if [ -z "${!key}" ]; then
          value="$(grep -m1 "^${key}=" .env | cut -d= -f2-)"
          [ -n "$value" ] && export "${key}=${value}"
        fi
      done
    fi
    JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}" mvn spring-boot:run
    ;;
  2)
    if [ -z "$NEON_DB_URL" ]; then
      echo "Error: NEON_DB_URL not set. Export your Neon credentials first."
      exit 1
    fi
    echo ""
    echo "Starting with Neon cloud database..."
    JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}" mvn spring-boot:run -Dspring-boot.run.profiles=dev
    ;;
  *)
    echo "Invalid choice"
    exit 1
    ;;
esac
