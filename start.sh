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
    echo "Make sure Docker is running: docker-compose -f ../data/docker-compose.yml up -d"
    echo ""
    JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}" mvn spring-boot:run
    ;;
  2)
    if [ -z "$NEON_DB_URL" ]; then
      echo "Error: NEON_DB_URL not set. Export your Neon credentials first."
      exit 1
    fi
    echo ""
    echo "Starting with Neon cloud database..."
    JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}" mvn spring-boot:run -Dspring-boot.run.profiles=dev
    ;;
  *)
    echo "Invalid choice"
    exit 1
    ;;
esac
