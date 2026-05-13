#!/bin/bash
# Stop all services and clean up

set -e

echo "🛑 Stopping Quiz Platform..."

docker-compose -f podman-compose.yml down

echo "✅ All services stopped."
echo ""
echo "To also remove volumes (database data):"
echo "  docker-compose -f podman-compose.yml down -v"
