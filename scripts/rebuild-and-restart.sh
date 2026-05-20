#!/bin/bash
set -e

export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
export PATH="$JAVA_HOME/bin:$PATH"

echo "=== Stopping running services ==="
pkill -f "session-service" 2>/dev/null || true
pkill -f "quiz-service" 2>/dev/null || true
pkill -f "auth-service" 2>/dev/null || true
pkill -f "analytics-service" 2>/dev/null || true
pkill -f "websocket-service" 2>/dev/null || true
sleep 2

echo "=== Rebuilding session-service ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend
mvn package -DskipTests -Dmaven.test.skip=true -pl session-service -am

echo ""
echo "=== Restarting all backend services ==="
cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/auth-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/auth-service.log 2>&1 &
echo "auth-service PID: $!"

cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/quiz-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/quiz-service.log 2>&1 &
echo "quiz-service PID: $!"

cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/session-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/session-service.log 2>&1 &
echo "session-service PID: $!"

cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/analytics-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/analytics-service.log 2>&1 &
echo "analytics-service PID: $!"

cd /Users/arpit.jindal/workspace/opensource/quiz_app/backend/websocket-service
nohup "$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=dev target/*.jar > /tmp/websocket-service.log 2>&1 &
echo "websocket-service PID: $!"

echo ""
echo "=== All services restarted ==="
echo "Wait ~15 seconds for Spring Boot to initialize, then test."
