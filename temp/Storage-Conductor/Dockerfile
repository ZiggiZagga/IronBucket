# Storage-Conductor: S3-compatible storage backend test orchestrator
# Test container that validates storage operations through identity gateway

FROM maven:3.9-eclipse-temurin-25

WORKDIR /tests

# Copy both modules
COPY Vault-Smith /vault-smith
COPY Storage-Conductor /storage-conductor

# Build Vault-Smith first (test dependency)
RUN cd /vault-smith && mvn clean install -DskipTests -q

# Install curl for logging and reporting
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create test logs directory
RUN mkdir -p /test-reports

# Copy test entry point script (from first COPY, now in /storage-conductor/)
RUN cp /storage-conductor/docker-entrypoint.sh /docker-entrypoint.sh && chmod +x /docker-entrypoint.sh

# Set working directory
WORKDIR /storage-conductor

# Run tests with output to logs (no port exposure)
ENTRYPOINT ["/docker-entrypoint.sh"]
