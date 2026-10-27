#!/bin/bash

echo "=== CogHealth EHR API ==="

if [ -z "$JWT_SECRET" ]; then
  export JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
  echo "JWT_SECRET not set; generated a random one for this run (tokens reset on restart)."
fi
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
