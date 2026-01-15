# IronBucket LGTM Stack Setup Guide

## Overview

This setup includes a complete observability stack with Loki, Grafana, Tempo, and Mimir (LGTM) integrated with IronBucket microservices running in Docker containers.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│           IronBucket Microservices                      │
├─────────────────────────────────────────────────────────┤
│  Sentinel-Gear (Gateway) │ Claimspindel │ Brazz-Nossel  │
│  Buzzle-Vane (Eureka)                                   │
│                                                         │
│  Infrastructure: PostgreSQL, Keycloak, MinIO           │
└─────────────────────────────────────────────────────────┘
                          ↓
        OpenTelemetry Collector (Port 4317)
                          ↓
        ┌────────────────────────────────┐
        │    LGTM Observability Stack    │
        ├────────────────────────────────┤
        │  Loki (Logs)                   │
        │  Grafana (Visualization)       │
        │  Tempo (Traces)                │
        │  Mimir (Metrics)               │
        └────────────────────────────────┘

Test Client:
  - Runs internal E2E curl tests against all services
  - Logs visible via: docker logs steel-hammer-test-client
```

## Network Architecture

- **Internal Network**: `steel-hammer-network` (bridge driver)
- **All Services**: Run on internal network only
- **External Exposure**: 
  - Sentinel-Gear: Port 8080 (API), Port 8081 (Metrics/Actuator) - IF NEEDED
  - Test Client: No external ports (uses internal network)
  - Observability: Internal only (no external ports)

## Quick Start

### 1. **Start the Stack**

```bash
cd /workspaces/IronBucket/steel-hammer

# Make test script executable
chmod +x test-scripts/run-e2e-tests.sh

# Start all services including LGTM stack
docker-compose -f docker-compose-lgtm.yml up -d
```

### 2. **Wait for Services to Initialize**

```bash
# Wait for all services to be healthy (approximately 60-90 seconds)
docker-compose -f docker-compose-lgtm.yml ps

# Check health status
curl -s http://localhost:8080/actuator/health | jq .
```

### 3. **Run E2E Tests**

The test client automatically starts after 60 seconds and runs comprehensive E2E tests:

```bash
# Watch test execution
docker logs -f steel-hammer-test-client

# Get full test results
docker logs steel-hammer-test-client 2>&1 | grep -E "PASS|FAIL|Test Summary" -A 5
```

### 4. **Access Observability Stack**

Currently, the LGTM stack is configured for **internal-only networking**. To access Grafana and other observability tools, you have two options:

#### Option A: Port Forwarding (For Development)

If you need to temporarily access Grafana for verification:

```bash
# Forward Grafana port to localhost
docker run --network steel-hammer-network -p 3000:3000 \
  grafana/grafana:latest --name grafana-proxy

# Access at: http://localhost:3000
# Default: admin / admin
```

#### Option B: Configure External Exposure

Edit `docker-compose-lgtm.yml` and uncomment port mappings:

```yaml
grafana:
  ports:
    - "3000:3000"
```

Then restart:
```bash
docker-compose -f docker-compose-lgtm.yml down
docker-compose -f docker-compose-lgtm.yml up -d
```

### 5. **View Logs**

#### From Test Client

```bash
# All test logs
docker logs steel-hammer-test-client

# Only test results
docker logs steel-hammer-test-client 2>&1 | tail -50

# Real-time logs
docker logs -f steel-hammer-test-client
```

#### From Microservices

```bash
# Sentinel-Gear logs
docker logs -f steel-hammer-sentinel-gear

# Claimspindel logs
docker logs -f steel-hammer-claimspindel

# Brazz-Nossel logs
docker logs -f steel-hammer-brazz-nossel

# Buzzle-Vane (Eureka) logs
docker logs -f steel-hammer-buzzle-vane
```

## Configuration Files

### OpenTelemetry Collector
**File**: `otel-collector-config.yml`

Receives OTLP telemetry from microservices and exports to:
- **Tempo**: Traces (gRPC on port 4317)
- **Mimir**: Metrics (Prometheus remote write on port 9009)
- **Loki**: Logs (HTTP on port 3100)

### Service Configurations

Each microservice sends telemetry to the collector via environment variable:
```yaml
MANAGEMENT_OTLP_TRACING_ENDPOINT=http://steel-hammer-otel-collector:4317
OTEL_EXPORTER_OTLP_ENDPOINT=http://steel-hammer-otel-collector:4317
```

### Structured Logging

Each service uses `logback-spring.xml` for JSON-formatted logs with:
- Service name
- Environment tag
- Request context/MDC data
- Structured fields for querying

## E2E Test Suite

The test client (`steel-hammer-test-client`) performs:

### Phase 1: Health Checks
- Keycloak admin endpoint
- MinIO S3 service
- PostgreSQL database connectivity

### Phase 2: Microservice Health
- Buzzle-Vane (Eureka) health
- Sentinel-Gear (Gateway) health
- Claimspindel (Policy Engine) health
- Brazz-Nossel (S3 Proxy) health

### Phase 3: Service Discovery
- Eureka service registry
- Registered application count

### Phase 4: Gateway Routing
- Gateway root endpoint
- Gateway health endpoint

### Phase 5: Observability
- Prometheus metrics endpoint
- Metrics collection validation

### Phase 6-7: Backends
- PostgreSQL connectivity
- MinIO S3 connectivity

## Data Retention & Cleanup

### Stopping the Stack

```bash
docker-compose -f docker-compose-lgtm.yml down
```

### Full Cleanup (Removes Data)

```bash
docker-compose -f docker-compose-lgtm.yml down -v
```

### Partial Cleanup (Keep Data)

```bash
docker-compose -f docker-compose-lgtm.yml stop
```

## Troubleshooting

### Services Won't Start

```bash
# Check logs
docker-compose -f docker-compose-lgtm.yml logs [service-name]

# Ensure ports are free
netstat -tlnp | grep -E '4317|4318|3100|9009|3000'
```

### Test Client Fails

```bash
# Wait longer (services take 60-90 seconds to fully initialize)
sleep 30

# Check test output
docker logs steel-hammer-test-client
```

### Metrics Not Appearing

1. Verify OTEL Collector is running:
```bash
docker ps | grep otel-collector
```

2. Check collector logs:
```bash
docker logs steel-hammer-otel-collector
```

3. Verify microservice environment variables:
```bash
docker inspect steel-hammer-sentinel-gear | jq '.[0].Config.Env'
```

### Performance Issues

Adjust in config files:
- **Loki**: Reduce `ingestion_rate_mb` in `loki-config.yaml`
- **Mimir**: Reduce `ingestion_rate` in `mimir-config.yaml`
- **Tempo**: Reduce `max_chunk_age` in `tempo-config.yaml`

## Production Considerations

1. **External Exposure**: The current setup is internal-only. For production:
   - Add proper TLS certificates
   - Configure authentication (OAuth2, mTLS)
   - Use external load balancer
   - Expose only Sentinel-Gear gateway

2. **Data Persistence**:
   - Mount external volumes for Loki, Mimir, Tempo, Grafana
   - Use cloud-based storage backends
   - Implement backup strategies

3. **Scalability**:
   - Current: Single-process LGTM stack
   - Production: Use Kubernetes, distributed microservices, external backends

4. **Security**:
   - Enable mTLS between services
   - Implement RBAC in Grafana
   - Secure Keycloak realm configuration
   - Use secrets management

## Next Steps

1. **Access Grafana** (see "Access Observability Stack" above)
2. **Create Dashboards**:
   - Add Prometheus data source: `http://mimir:9009`
   - Add Loki data source: `http://loki:3100`
   - Add Tempo data source: `http://tempo:3200`
   - Build custom dashboards

3. **Set Up Alerts**:
   - Configure alert rules in Mimir
   - Route alerts to Alertmanager
   - Send notifications to Slack/PagerDuty

4. **E2E Testing**:
   - Extend test scripts in `test-scripts/`
   - Add custom test scenarios
   - Integrate with CI/CD pipeline

## Files Reference

- `docker-compose-lgtm.yml`: Main composition file
- `otel-collector-config.yml`: OpenTelemetry collector configuration
- `loki-config.yaml`: Loki log aggregation config
- `mimir-config.yaml`: Mimir metrics storage config
- `tempo-config.yaml`: Tempo trace storage config
- `test-scripts/run-e2e-tests.sh`: E2E test suite

## Support

For issues or questions:
1. Check Docker logs: `docker logs [container-name]`
2. Verify network: `docker network inspect steel-hammer-network`
3. Check service connectivity: `docker exec [container] curl http://[service]:port`
4. Review configurations in respective `.yaml` files
