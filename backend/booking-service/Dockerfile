# ==============================================================================
# Booking Service Dockerfile
# ==============================================================================
# Multi-stage build for optimal image size and security
#
# Build Command:
#   docker build -t booking-service:1.0.0 .
#
# Run Command:
#   docker run -p 8082:8082 \
#     -e MONGODB_URI=mongodb://localhost:27017/event_ticketing_prod \
#     -e REDIS_HOST=localhost \
#     booking-service:1.0.0
# ==============================================================================

# ==============================================================================
# Stage 1: Build Stage
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy POM and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Runtime Stage
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 appuser && \
    adduser -D -u 1001 -G appuser appuser

WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# JVM options for containerized environment
# Higher memory for booking service (handles payments, QR codes)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
