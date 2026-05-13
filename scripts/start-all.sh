#!/bin/bash
# Start all services with Podman (full stack)
# Builds and runs everything in containers

set -e

echo "🚀 Starting full Quiz Platform stack with Podman..."

# Ensure podman machine is running (macOS)
if [[ "$(uname)" == "Darwin" ]]; then
    if ! podman machine inspect podman-machine-default &>/dev/null || \
       [[ "$(podman machine inspect podman-machine-default --format '{{.State}}')" != "running" ]]; then
        echo "Starting podman machine..."
        podman machine start 2>/dev/null || true
    fi
fi

# Build and start all services
docker-compose -f podman-compose.yml up -d --build

echo ""
echo "✅ All services starting!"
echo ""
echo "  Frontend:          http://localhost:3000"
echo "  API Gateway:       http://localhost:8080"
echo "  Auth Service:      http://localhost:8081"
echo "  Quiz Service:      http://localhost:8082"
echo "  Session Service:   http://localhost:8083"
echo "  WebSocket Service: http://localhost:8084"
echo "  Analytics Service: http://localhost:8085"
echo "  PostgreSQL:        localhost:5432"
echo "  Redis:             localhost:6379"
echo ""
echo "To include monitoring (Prometheus + Grafana):"
echo "  docker-compose -f podman-compose.yml --profile monitoring up -d"
echo ""
echo "View logs:"
echo "  docker-compose -f podman-compose.yml logs -f [service-name]"
echo ""
echo "Stop all:"
echo "  docker-compose -f podman-compose.yml down"
