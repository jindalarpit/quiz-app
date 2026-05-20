#!/bin/bash
set -e

cd /Users/arpit.jindal/workspace/opensource/quiz_app

echo "=== Starting infrastructure (PostgreSQL + Redis) ==="
docker compose -f podman-compose-infra.yml up -d

echo ""
echo "=== Waiting for PostgreSQL to be healthy ==="
sleep 5

echo ""
echo "=== Building backend services ==="
cd backend
mvn clean package -DskipTests -pl auth-service,quiz-service,session-service,websocket-service,analytics-service -am

echo ""
echo "=== Starting auth-service (port 8080) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/auth-service
nohup java -jar -Dspring.profiles.active=dev target/*.jar > /tmp/auth-service.log 2>&1 &
echo "auth-service PID: $!"

echo "=== Starting quiz-service (port 8081) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/quiz-service
nohup java -jar -Dspring.profiles.active=dev target/*.jar > /tmp/quiz-service.log 2>&1 &
echo "quiz-service PID: $!"

echo "=== Starting session-service (port 8082) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/session-service
nohup java -jar -Dspring.profiles.active=dev target/*.jar > /tmp/session-service.log 2>&1 &
echo "session-service PID: $!"

echo "=== Starting analytics-service (port 8083) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/analytics-service
nohup java -jar -Dspring.profiles.active=dev target/*.jar > /tmp/analytics-service.log 2>&1 &
echo "analytics-service PID: $!"

echo "=== Starting websocket-service (port 8084) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/websocket-service
nohup java -jar -Dspring.profiles.active=dev target/*.jar > /tmp/websocket-service.log 2>&1 &
echo "websocket-service PID: $!"

echo ""
echo "=== Starting frontend (port 3000) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/frontend
nohup npm run dev > /tmp/frontend.log 2>&1 &
echo "frontend PID: $!"

echo ""
echo "============================================"
echo "All services starting!"
echo ""
echo "  Frontend:          http://localhost:3000"
echo "  Auth Service:      http://localhost:8080"
echo "  Quiz Service:      http://localhost:8081"
echo "  Session Service:   http://localhost:8082"
echo "  Analytics Service: http://localhost:8083"
echo "  WebSocket Service: http://localhost:8084"
echo "  PostgreSQL:        localhost:5432"
echo "  Redis:             localhost:6379"
echo ""
echo "Logs:"
echo "  tail -f /tmp/auth-service.log"
echo "  tail -f /tmp/quiz-service.log"
echo "  tail -f /tmp/session-service.log"
echo "  tail -f /tmp/analytics-service.log"
echo "  tail -f /tmp/websocket-service.log"
echo "  tail -f /tmp/frontend.log"
echo "============================================"
