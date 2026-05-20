#!/bin/bash
set -e

echo "=== Installing JDK 21 via Homebrew ==="
brew install openjdk@21

echo ""
echo "=== Setting JAVA_HOME to JDK 21 ==="
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Java version:"
java -version

echo ""
echo "=== Infrastructure already running (PostgreSQL + Redis) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app
docker compose -f podman-compose-infra.yml up -d

echo ""
echo "=== Building backend services with JDK 21 ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend
mvn clean package -DskipTests -Dmaven.test.skip=true -pl auth-service,quiz-service,session-service,websocket-service,analytics-service -am

echo ""
echo "=== Starting auth-service (port 8080) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/auth-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/auth-service.log 2>&1 &
echo "auth-service PID: $!"

echo "=== Starting quiz-service (port 8081) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/quiz-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/quiz-service.log 2>&1 &
echo "quiz-service PID: $!"

echo "=== Starting session-service (port 8082) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/session-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/session-service.log 2>&1 &
echo "session-service PID: $!"

echo "=== Starting analytics-service (port 8083) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/analytics-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/analytics-service.log 2>&1 &
echo "analytics-service PID: $!"

echo "=== Starting websocket-service (port 8084) ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/websocket-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/websocket-service.log 2>&1 &
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
