#!/bin/bash
# Start infrastructure services only (PostgreSQL + Redis)
# Use this for local development where you run services from IDE

set -e

echo "🚀 Starting infrastructure services with Podman..."

# Ensure podman machine is running (macOS)
if [[ "$(uname)" == "Darwin" ]]; then
    if ! podman machine inspect podman-machine-default &>/dev/null || \
       [[ "$(podman machine inspect podman-machine-default --format '{{.State}}')" != "running" ]]; then
        echo "Starting podman machine..."
        podman machine start 2>/dev/null || true
    fi
fi

# Start only PostgreSQL and Redis
docker-compose -f podman-compose.yml up -d postgresql redis

echo ""
echo "✅ Infrastructure ready!"
echo ""
echo "  PostgreSQL: localhost:5432 (user: quiz, pass: quiz_dev_password, db: quizplatform)"
echo "  Redis:      localhost:6379"
echo ""
echo "Run backend services from your IDE with 'dev' profile, or use:"
echo "  docker-compose -f podman-compose.yml up -d"
echo "to start all services."
