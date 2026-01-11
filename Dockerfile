# ============================================================
# FixHomi Auth Service - Production Dockerfile
# ============================================================
# Multi-stage build for optimized image size and faster deployments
# Compatible with Render.com and other container platforms
# ============================================================

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (cached if pom.xml hasn't changed)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests -B

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for health checks (more reliable than wget in Alpine)
RUN apk add --no-cache curl

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the default port (Render will override this with PORT env variable)
EXPOSE 8080

# Health check endpoint for Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

# Run the application with optimized JVM settings for containers
# -XX:+UseContainerSupport: Enable container-aware memory settings
# -XX:MaxRAMPercentage=75.0: Use max 75% of available RAM
# -Djava.security.egd: Use non-blocking random for faster startup
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
