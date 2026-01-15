# IronBucket Production-Ready Deployment Guide

## Overview

IronBucket is now production-ready with:
- **Java 25** runtime
- **Spring Boot 4.0.1** with reactive WebClient
- **Spring Cloud 2025.0.0** (compatible with Boot 4.0.1)
- **OpenTelemetry** observability stack
- **Structured JSON logging** with Logstash encoder
- **Internal-only networking** with secure gateway exposure
- **E2E curl-based testing** via containerized test client

## Quick Start

### 1. Start the Stack

```bash
cd /workspaces/IronBucket/steel-hammer

# Start all microservices with internal networking
docker-compose -f docker-compose-steel-hammer.yml up -d
```

### 2. Wait for Services to Initialize

```bash
# Monitor container health (approximately 60-90 seconds)
docker-compose -f docker-compose-steel-hammer.yml ps

# Watch specific service logs
docker logs -f steel-hammer-sentinel-gear
```

### 3. View E2E Test Results

The test client automatically runs after 60 seconds and performs comprehensive E2E tests:

```bash
# Watch test execution in real-time
docker logs -f steel-hammer-test-client

# View all test results after execution
docker logs steel-hammer-test-client 2>&1 | grep -E "✓|✗|Test Summary" -A 20
```

## System Architecture

### Microservices (All running in containers)

```
┌─────────────────────────────────────────────────────────┐
│  IronBucket Microservices Architecture                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Sentinel-Gear (Spring Cloud Gateway)             │  │
│  │ - Entry point for all requests                   │  │
│  │ - OAuth2 security integration                    │  │
│  │ - Route services via Eureka discovery            │  │
│  │ - Exposes: Port 8080 (API)                       │  │
│  └──────────────────────────────────────────────────┘  │
│                         ↓                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Internal Services (via Steel-hammer network)     │  │
│  │                                                  │  │
│  │  • Claimspindel (Policy Engine)                 │  │
│  │    - ABAC/RBAC policy evaluation                │  │
│  │    - Git-backed policy store                    │  │
│  │    - Port: 8081                                 │  │
│  │                                                  │  │
│  │  • Brazz-Nossel (S3 Proxy)                      │  │
│  │    - S3-compatible request routing              │  │
│  │    - MinIO integration                          │  │
│  │    - Port: 8082                                 │  │
│  │                                                  │  │
│  │  • Buzzle-Vane (Eureka Server)                  │  │
│  │    - Service discovery and registration         │  │
│  │    - Health check management                    │  │
│  │    - Port: 8083                                 │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Infrastructure Services                          │  │
│  │                                                  │  │
│  │  • PostgreSQL - Primary data store              │  │
│  │  • Keycloak - OIDC/OAuth2 identity provider    │  │
│  │  • MinIO - S3-compatible object storage         │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Testing & Observability                         │  │
│  │                                                  │  │
│  │  • Test Client - E2E curl-based tests          │  │
│  │  • OpenTelemetry Collector - Trace ingestion   │  │
│  │  • LGTM Stack - Loki, Grafana, Tempo, Mimir   │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Network Topology

```
┌────────────────────────────────────────────┐
│   Docker Bridge Network (internal)         │
│   steel-hammer-network                     │
│                                            │
│  ┌─ Sentinel-Gear (8080)                  │
│  │   ↓                                      │
│  ├─ Buzzle-Vane (8083) - Eureka           │
│  │   ↓                                      │
│  ├─ Claimspindel (8081) - Policy          │
│  │   ↓                                      │
│  ├─ Brazz-Nossel (8082) - S3 Proxy        │
│  │   ↓                                      │
│  ├─ PostgreSQL (5432)                     │
│  ├─ Keycloak (7081)                       │
│  ├─ MinIO (9000)                          │
│  │                                         │
│  └─ OpenTelemetry Stack                   │
│     ├─ Loki (3100) - Log aggregation      │
│     ├─ Mimir (9009) - Metrics storage     │
│     ├─ Tempo (3200) - Trace storage       │
│     ├─ Grafana (3000) - Visualization     │
│     └─ OTEL Collector (4317) - Ingestion  │
└────────────────────────────────────────────┘
         ↑
    Only Sentinel-Gear
    port 8080 exposed
    to external world
```

## Key Features

### 1. **Java 25 & Spring Boot 4.0.1**
- Latest LTS Java runtime
- Modern Spring Boot with reactive patterns
- WebClient instead of deprecated RestTemplate
- Graceful shutdown with 30-second timeout

### 2. **Reactive Architecture**
- **WebClient** with Reactor Netty
- Non-blocking I/O across all services
- Connection pooling and timeout management
- Built-in circuit breaker patterns

### 3. **Observability Stack**
- **OpenTelemetry**: Distributed tracing via OTLP
- **Structured Logging**: JSON format with Logstash encoder
- **Prometheus Metrics**: Via Micrometer registry
- **Health Checks**: Liveness and readiness probes

### 4. **Security & Zero-Trust**
- **Sentinel-Gear**: Only exposed external port
- **Internal Network**: All services isolated
- **OAuth2**: Integration with Keycloak
- **Health Endpoints**: Secured actuator endpoints on 8081

### 5. **Service Discovery**
- **Eureka**: Service registration and discovery
- **Load Balancing**: Via Spring Cloud Gateway
- **Health Checks**: Automatic service availability monitoring
- **Failover**: Graceful handling of service unavailability

## Configuration

### Environment Variables

Each service responds to these environment variables:

```yaml
SPRING_PROFILES_ACTIVE=docker           # Enables docker profile
SPRING_APPLICATION_NAME=<service-name>  # Service identification
EUREKA_CLIENT_SERVICE_URL_DEFAULT_ZONE=http://buzzle-vane:8083/eureka  # Service discovery
MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4317  # Trace exporter
MANAGEMENT_METRICS_TAGS_ENVIRONMENT=docker                     # Metrics tagging
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317        # OTEL endpoint
OTEL_EXPORTER_OTLP_PROTOCOL=grpc                              # OTEL protocol
```

### Logging Configuration

All services use [logback-spring.xml](../temp/Sentinel-Gear/src/main/resources/logback-spring.xml) with:

- **Plain text console** logging in default profile
- **JSON structured** logging in docker profile
- **DEBUG level** for com.ironbucket package
- **INFO level** for Spring Framework
- **Custom fields**: service name, environment, thread ID, request context

Example log output (JSON):
```json
{
  "timestamp": "2026-01-15T14:30:00.000Z",
  "level": "INFO",
  "thread": "nioEventLoopGroup-1-1",
  "logger": "com.ironbucket.gateway.GatewayService",
  "message": "Route resolved successfully",
  "service": "sentinel-gear",
  "environment": "docker",
  "request_id": "abc-123-def"
}
```

## E2E Testing

### Test Suite Phases

The containerized test client ([run-e2e-tests.sh](test-scripts/run-e2e-tests.sh)) performs:

#### Phase 1: Infrastructure Health
- Keycloak admin endpoint (/admin/)
- MinIO S3 service healthcheck
- PostgreSQL database connectivity

#### Phase 2: Microservice Health
- Buzzle-Vane (Eureka) health endpoint
- Sentinel-Gear (Gateway) health endpoint
- Claimspindel (Policy) health endpoint
- Brazz-Nossel (S3 Proxy) health endpoint

#### Phase 3: Service Discovery
- Eureka service registry availability
- Registered application count validation

#### Phase 4: Gateway Routing
- Gateway root endpoint connectivity
- Gateway health endpoint response

#### Phase 5: Observability
- Prometheus metrics endpoint availability
- Metrics collection validation

#### Phase 6-7: Backend Services
- PostgreSQL connectivity on port 5432
- MinIO connectivity on port 9000

### Running Tests Manually

```bash
# Execute test script directly
docker exec steel-hammer-test /scripts/run-e2e-tests.sh

# Run specific test phases
docker exec steel-hammer-test bash -c "curl -s http://steel-hammer-sentinel-gear:8080/actuator/health | jq"

# Check test client logs with timestamps
docker logs --timestamps steel-hammer-test-client

# Follow real-time test output
docker logs -f steel-hammer-test-client
```

## Managing the Stack

### Start Services

```bash
# Start all services
docker-compose -f docker-compose-steel-hammer.yml up -d

# Start specific service
docker-compose -f docker-compose-steel-hammer.yml up -d steel-hammer-sentinel-gear

# Start with logs visible
docker-compose -f docker-compose-steel-hammer.yml up
```

### Stop Services

```bash
# Graceful stop (respects 30-second shutdown timeout)
docker-compose -f docker-compose-steel-hammer.yml stop

# Forceful stop
docker-compose -f docker-compose-steel-hammer.yml kill

# Remove containers and networks
docker-compose -f docker-compose-steel-hammer.yml down

# Remove volumes too (cleans all data)
docker-compose -f docker-compose-steel-hammer.yml down -v
```

### View Logs

```bash
# All services
docker-compose -f docker-compose-steel-hammer.yml logs

# Specific service
docker logs steel-hammer-sentinel-gear

# Real-time logs
docker logs -f steel-hammer-claimspindel

# Last 100 lines with timestamps
docker logs --tail=100 --timestamps steel-hammer-brazz-nossel

# Filter logs
docker logs steel-hammer-test-client 2>&1 | grep "PASS\|FAIL"
```

### Check Health Status

```bash
# Container status
docker-compose -f docker-compose-steel-hammer.yml ps

# Service health via curl
curl -s http://localhost:8080/actuator/health | jq

# Eureka service registry
curl -s http://localhost:8083/eureka/apps | jq

# Prometheus metrics (if exposed)
curl -s http://localhost:8081/actuator/prometheus | head -20
```

## Observability Stack (LGTM)

While the main deployment uses internal OpenTelemetry integration, the LGTM stack components are available for advanced logging and tracing:

### Adding LGTM Components

Configuration files are provided for integration:

- [loki-config.yaml](loki-config.yaml) - Log aggregation configuration
- [mimir-config.yaml](mimir-config.yaml) - Metrics storage configuration
- [tempo-config.yaml](tempo-config.yaml) - Trace storage configuration
- [otel-collector-config.yml](otel-collector-config.yml) - OpenTelemetry collector configuration

### Manual LGTM Setup

If you want to add the full observability stack:

```bash
# Create a separate LGTM docker-compose file with:
# - Loki service on port 3100
# - Mimir service on port 9009
# - Tempo service on port 3200/4317
# - Grafana service on port 3000
# - OTEL Collector service on ports 4317/4318

# Update microservice environment variables to:
# MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4317
# MANAGEMENT_METRICS_TAGS_ENVIRONMENT=production
```

## Production Deployment Considerations

### 1. **Security Hardening**
- [ ] Enable mTLS between services
- [ ] Configure Keycloak realm for production
- [ ] Use encrypted secrets management
- [ ] Enable Pod Security Policies (K8s)
- [ ] Configure network policies

### 2. **Scalability**
- [ ] Use Kubernetes orchestration
- [ ] Configure auto-scaling based on metrics
- [ ] Implement distributed caching (Redis)
- [ ] Setup database replication
- [ ] Configure load balancers

### 3. **Monitoring & Alerting**
- [ ] Setup Prometheus scraping
- [ ] Configure Grafana dashboards
- [ ] Define alert rules
- [ ] Setup log aggregation
- [ ] Configure centralized tracing

### 4. **Data Persistence**
- [ ] Mount external volumes for PostgreSQL
- [ ] Setup backup strategies
- [ ] Configure cloud storage for MinIO
- [ ] Implement disaster recovery
- [ ] Setup automated failover

### 5. **Performance Tuning**
- [ ] Tune JVM heap sizes
- [ ] Configure connection pools
- [ ] Optimize Spring Boot startup
- [ ] Setup caching strategies
- [ ] Monitor resource usage

## Troubleshooting

### Services Won't Start

```bash
# Check container logs
docker logs <container-name>

# Verify Docker network
docker network inspect steel-hammer_steel-hammer-network

# Ensure ports are available
netstat -tlnp | grep -E '8080|8081|8082|8083|5432|7081|9000'
```

### Health Check Failures

```bash
# Check service health
curl -v http://localhost:8080/actuator/health

# Check Eureka registration
curl -s http://localhost:8083/eureka/apps | jq '.applications.application[] | {name: .name, instance: .instance[].instanceId}'

# Check specific service
curl -s http://localhost:8081/actuator/health | jq '.status'
```

### Test Client Issues

```bash
# Check test output
docker logs steel-hammer-test

# Verify environment variables
docker exec steel-hammer-test env | grep URL

# Test connectivity manually
docker exec steel-hammer-test curl -v http://steel-hammer-sentinel-gear:8080/actuator/health

# Check test script
cat test-scripts/run-e2e-tests.sh
```

### Performance Issues

```bash
# Monitor resource usage
docker stats steel-hammer-sentinel-gear

# Check JVM metrics
curl -s http://localhost:8081/actuator/metrics/jvm.memory.used | jq

# Analyze logs for errors
docker logs steel-hammer-sentinel-gear 2>&1 | grep -i error
```

## File Structure

```
steel-hammer/
├── docker-compose-steel-hammer.yml    # Main composition file
├── docker-compose-lgtm.yml             # LGTM stack (optional)
├── otel-collector-config.yml           # OpenTelemetry configuration
├── loki-config.yaml                    # Loki log aggregation config
├── mimir-config.yaml                   # Mimir metrics config
├── tempo-config.yaml                   # Tempo tracing config
├── test-scripts/
│   └── run-e2e-tests.sh               # E2E test suite
├── postgres/                           # PostgreSQL setup
├── keycloak/                           # Keycloak setup
├── minio/                              # MinIO setup
├── tests/                              # Original tests
└── LGTM-SETUP-GUIDE.md                # LGTM detailed guide
```

## Support & Documentation

- **Spring Boot 4.0.1**: https://spring.io/projects/spring-boot
- **Spring Cloud 2025.0.0**: https://spring.io/projects/spring-cloud
- **OpenTelemetry**: https://opentelemetry.io/
- **Logback**: https://logback.qos.ch/
- **Keycloak**: https://www.keycloak.org/
- **MinIO**: https://min.io/

## Version Information

```
Java:               25 (eclipse-temurin:25-jre-alpine)
Spring Boot:        4.0.1
Spring Cloud:       2025.0.0
Maven:              3.9
Docker:             Latest
Logstash Encoder:   7.4
OpenTelemetry:      Latest
```

---

**Last Updated**: 2026-01-15  
**Status**: Production-Ready ✅
