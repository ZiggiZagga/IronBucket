# Vault-Smith: Cloud-agnostic S3-compatible storage backend
# Multi-stage build for optimized container image

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy JAR from builder
COPY --from=builder /build/target/*.jar app.jar

# Create non-root user
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser
USER appuser

# Expose port for service
EXPOSE 8090

# Health check: verify S3 backend connectivity
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8090/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=docker", "--server.port=8090"]
