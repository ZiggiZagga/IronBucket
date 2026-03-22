# Multi-stage build for Brazz-Nossel

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY services/Pactum-Scroll /build/services/Pactum-Scroll
COPY services/Brazz-Nossel /build/services/Brazz-Nossel
RUN mvn -f /build/services/Pactum-Scroll/pom.xml clean install -DskipTests -q \
    && mvn -f /build/services/Brazz-Nossel/pom.xml clean package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy JAR from builder
COPY --from=builder /build/services/Brazz-Nossel/target/*.jar app.jar

# Create non-root user
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser
USER appuser

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
    CMD curl -f https://localhost:8082/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=docker"]
