# Multi-stage build for minimal production image
# Compatible with Podman and Docker

# Stage 1: Build
FROM docker.io/library/maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy parent POM and common module first (for dependency caching)
COPY ../pom.xml ./pom.xml
COPY ../common/pom.xml ./common/pom.xml
COPY ../common/src ./common/src

# Copy this service's POM
COPY pom.xml ./auth-service/pom.xml

# Download dependencies (cached layer)
RUN mvn -f pom.xml dependency:go-offline -pl common,auth-service -am -q || true

# Copy source and build
COPY src ./auth-service/src
RUN mvn -f pom.xml package -pl common,auth-service -am -DskipTests -q

# Stage 2: Runtime
FROM docker.io/library/eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: run as non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy built JAR
COPY --from=builder /app/auth-service/target/*.jar app.jar

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=3 --start-period=30s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
