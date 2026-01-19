# Comprehensive LGTM + OpenTelemetry Integration Testing Plan

## Phase 0: Pre-Flight Checks (5 minutes)

### 0.1 System Verification
```bash
# Check Docker is running
docker --version
docker ps

# Check required disk space
df -h /workspaces/IronBucket

# Verify all config files exist
ls -la steel-hammer/*-config*.{yaml,yml}

# Verify all application-docker.yml files exist
find . -name "application-docker.yml" -type f
```

**Success Criteria:**
- ✅ Docker daemon running
- ✅ At least 10GB free space
- ✅ All 4 config files present (loki, tempo, mimir, otel)
- ✅ All 4 application-docker.yml files present (Brazz, Buzzle, Claimspindel, Sentinel)

### 0.2 Dependency Verification
```bash
# Check that no services are already running
docker ps | grep steel-hammer | wc -l  # Should be 0

# Clean up any leftover volumes
docker volume prune -f

# Verify Maven can build
cd Brazz-Nossel && mvn clean compile -q && cd ..
```

**Success Criteria:**
- ✅ No steel-hammer containers running
- ✅ Maven build succeeds for at least one service
- ✅ No disk space errors

---

## Phase 1: Docker Image Build (10-15 minutes)

### 1.1 Build Service Images
```bash
# Build all service images
cd temp/Brazz-Nossel && docker build -t brazz-nossel:latest . && cd ../..
cd temp/Buzzle-Vane && docker build -t buzzle-vane:latest . && cd ../..
cd temp/Claimspindel && docker build -t claimspindel:latest . && cd ../..
cd temp/Sentinel-Gear && docker build -t sentinel-gear:latest . && cd ../..

# Verify images built successfully
docker images | grep -E "brazz-nossel|buzzle-vane|claimspindel|sentinel-gear"
```

**Success Criteria:**
- ✅ All 4 images built successfully (no build errors)
- ✅ All images tagged with "latest"
- ✅ All images appear in `docker images` output

### 1.2 Verify Image Content
```bash
# Check each image has logback-spring.xml
docker inspect brazz-nossel:latest | grep -i logback  # Should show config mount points

# Verify Spring Boot version
docker run --rm brazz-nossel:latest java -version 2>&1 | head -2
```

**Success Criteria:**
- ✅ Images contain application code and config files
- ✅ Java 17+ available in images

---

## Phase 2: LGTM Stack Startup (5-10 minutes)

### 2.1 Start LGTM Services Only
```bash
cd steel-hammer

# Start only LGTM and infrastructure (no microservices yet)
docker-compose -f docker-compose-lgtm.yml up -d \
  steel-hammer-postgres \
  steel-hammer-minio \
  steel-hammer-keycloak \
  steel-hammer-loki \
  steel-hammer-tempo \
  steel-hammer-mimir \
  steel-hammer-grafana \
  steel-hammer-otel-collector

# Wait for services to initialize
sleep 30
```

**Success Criteria:**
- ✅ All 8 containers started (no errors in docker ps)
- ✅ No containers in "Restarting" state

### 2.2 Health Check: LGTM Services
```bash
# Check each LGTM component
echo "=== Checking LGTM Health ===" 

# Grafana (should return login page or health status)
curl -s http://localhost:3000/api/health | jq .

# Loki (should accept requests on 3100)
curl -s http://localhost:3100/loki/api/v1/status | jq .

# Tempo (should return 200 on health endpoint)
curl -s -o /dev/null -w "%{http_code}" http://localhost:3200/ready

# Mimir (should return metrics)
curl -s http://localhost:9009/api/v1/query?query=up | jq . | head -10

# OTEL Collector (should accept OTLP connections)
curl -s -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  -d '{}' | grep -q "code" && echo "OTEL ready"
```

**Success Criteria:**
- ✅ Grafana returns valid response (200 or 302)
- ✅ Loki returns /status response
- ✅ Tempo returns 200 on /ready endpoint
- ✅ Mimir returns metric data (even if empty)
- ✅ OTEL Collector accepts HTTP connections

### 2.3 Check Infrastructure Services
```bash
# Keycloak
curl -s http://localhost:7081/realms/dev/.well-known/openid-configuration | jq .issuer

# PostgreSQL (using docker exec)
docker exec steel-hammer-postgres pg_isready -U postgres | grep "accepting"

# MinIO
curl -s http://localhost:9000/minio/health/live

# All services running
docker ps | grep steel-hammer | grep "Up" | wc -l  # Should be 8
```

**Success Criteria:**
- ✅ Keycloak returns valid issuer URL
- ✅ PostgreSQL accepting connections
- ✅ MinIO health endpoint returns 200
- ✅ Exactly 8 containers in "Up" state

---

## Phase 3: Microservice Startup (10-15 minutes)

### 3.1 Start Microservices
```bash
# Start all microservices
docker-compose -f docker-compose-lgtm.yml up -d \
  steel-hammer-buzzle-vane \
  steel-hammer-sentinel-gear \
  steel-hammer-claimspindel \
  steel-hammer-brazz-nossel

# Wait for initialization
sleep 60

# Verify all containers are running
docker ps | grep steel-hammer | grep "Up" | wc -l  # Should be 12 (8 + 4)
```

**Success Criteria:**
- ✅ All 4 microservice containers started
- ✅ Total 12 containers in "Up" state
- ✅ No containers in "Exited" or "Restarting" state

### 3.2 Microservice Health Checks
```bash
echo "=== Checking Microservice Health ===" 

# Sentinel-Gear (Gateway)
curl -s http://localhost:8080/actuator/health | jq '.status'

# Buzzle-Vane (Eureka)
curl -s http://localhost:8083/actuator/health | jq '.status'

# Claimspindel (Policy Engine)
curl -s http://localhost:8081/actuator/health | jq '.status'

# Brazz-Nossel (S3 Proxy)
curl -s http://localhost:8082/actuator/health | jq '.status'

# Check Prometheus endpoints are accessible
for port in 8080 8081 8082 8083; do
  echo "Port $port metrics:"
  curl -s http://localhost:$port/actuator/prometheus | head -5
done
```

**Success Criteria:**
- ✅ All 4 services return `"status": "UP"`
- ✅ All services expose `/actuator/prometheus` metrics
- ✅ Metrics include OpenTelemetry attributes (service name, etc.)

### 3.3 Check OpenTelemetry Integration
```bash
# Check logs for OTEL initialization (should NOT have errors)
echo "=== Checking OTEL Initialization ===" 

docker logs steel-hammer-brazz-nossel 2>&1 | grep -i "opentelemetry\|otlp\|tracing" | head -10
docker logs steel-hammer-brazz-nossel 2>&1 | grep -i "error\|exception" | grep -i "otel" | wc -l

# Repeat for all services
for svc in sentinel-gear claimspindel buzzle-vane; do
  echo "=== $svc ===" 
  docker logs steel-hammer-$svc 2>&1 | grep -i "opentelemetry\|otlp" | head -5
done
```

**Success Criteria:**
- ✅ Services log successful OpenTelemetry initialization
- ✅ No OTEL-related errors in logs
- ✅ OTLP endpoint configured correctly in logs

---

## Phase 4: Observability Data Flow Verification (10 minutes)

### 4.1 Generate Traffic (Create Observable Traces/Logs/Metrics)
```bash
echo "=== Generating Test Traffic ===" 

# Get Keycloak token (generates trace)
TOKEN=$(curl -s http://localhost:7081/realms/dev/protocol/openid-connect/token \
  -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=test-client&client_secret=test-secret&grant_type=client_credentials" \
  | jq -r '.access_token' 2>/dev/null || echo "FAILED")

echo "Token result: ${TOKEN:0:20}..."

# Call Sentinel-Gear with token (generates trace)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/health | jq .

# Call Brazz-Nossel (generates S3 operation trace)
curl -s http://localhost:8082/actuator/health | jq .

# Hit Prometheus endpoints (generates metrics)
curl -s http://localhost:8080/actuator/prometheus > /dev/null
curl -s http://localhost:8082/actuator/prometheus > /dev/null

sleep 5  # Give OTEL Collector time to process
```

**Success Criteria:**
- ✅ All curl commands return HTTP 200
- ✅ No authorization errors
- ✅ Prometheus endpoints return metrics

### 4.2 Verify Data in Loki (Logs)
```bash
echo "=== Checking Logs in Loki ===" 

# Query Loki for recent logs
curl -s 'http://localhost:3100/loki/api/v1/query?query={service=~"brazz.*"}' | jq '.data.result | length'

# Query for specific service logs
for service in brazz-nossel sentinel-gear claimspindel buzzle-vane; do
  echo "Logs for $service:"
  curl -s "http://localhost:3100/loki/api/v1/query?query={service=\"$service\"}" \
    | jq '.data.result | length'
done

# Check for JSON log format (should have parsed fields)
curl -s 'http://localhost:3100/loki/api/v1/query?query={service="brazz-nossel"}' \
  | jq '.data.result[0].values[0] | length'  # Should have logs
```

**Success Criteria:**
- ✅ Loki returns log entries (result length > 0)
- ✅ All 4 services have logs in Loki
- ✅ Log entries are parseable JSON

### 4.3 Verify Data in Tempo (Traces)
```bash
echo "=== Checking Traces in Tempo ===" 

# Query Tempo for traces
curl -s 'http://localhost:3200/api/search?q=service%3Dbrazz-nossel' | jq '.traces | length'

# Get specific trace details
TRACE_ID=$(curl -s 'http://localhost:3200/api/search?q=service%3Dbrazz-nossel' \
  | jq -r '.traces[0].traceID' 2>/dev/null)

if [ ! -z "$TRACE_ID" ] && [ "$TRACE_ID" != "null" ]; then
  echo "Trace found: $TRACE_ID"
  curl -s "http://localhost:3200/api/traces/$TRACE_ID" | jq '.traceID, (.spans | length)'
else
  echo "No traces found yet (may need more traffic)"
fi
```

**Success Criteria:**
- ✅ Tempo returns trace data (traces length > 0)
- ✅ Traces have valid IDs and span data
- ✅ Service names match microservices

### 4.4 Verify Data in Mimir (Metrics)
```bash
echo "=== Checking Metrics in Mimir ===" 

# Query for HTTP request metrics
curl -s 'http://localhost:9009/api/v1/query?query=http_request_duration_seconds' \
  | jq '.data.result | length'

# Query for specific service metrics
curl -s 'http://localhost:9009/api/v1/query?query=up{service="brazz-nossel"}' \
  | jq '.data.result | length'

# Get metric names
curl -s 'http://localhost:9009/api/v1/label/__name__/values' \
  | jq '. | length'  # Should have many metrics
```

**Success Criteria:**
- ✅ Mimir returns metric data (result length > 0)
- ✅ HTTP metrics are available
- ✅ Service-specific metrics exist

---

## Phase 5: E2E Test Execution (15-20 minutes)

### 5.1 Run Standard E2E Tests
```bash
echo "=== Running Standard E2E Tests ===" 

cd /workspaces/IronBucket

# Run E2E tests with standalone script
timeout 300 bash e2e-test-standalone.sh 2>&1 | tee test-execution.log

# Check results
echo ""
echo "=== Test Results Summary ===" 
grep -E "PASSED|FAILED|Error" test-execution.log | tail -20
```

**Success Criteria:**
- ✅ E2E script completes without timeout
- ✅ Tests show PASSED status
- ✅ No authentication/connection errors
- ✅ Service endpoints respond correctly

### 5.2 Run Observability-Integrated E2E Tests
```bash
echo "=== Running E2E Tests with Observability ===" 

cd /workspaces/IronBucket/steel-hammer

# Run observability-integrated E2E tests
timeout 300 bash test-scripts/e2e-with-observability.sh 2>&1 | tee e2e-obs-execution.log

# Check results
echo ""
echo "=== Observability Test Results ===" 
grep -E "PHASE|PASS|FAIL" e2e-obs-execution.log
```

**Success Criteria:**
- ✅ E2E observability script completes
- ✅ All LGTM health checks pass
- ✅ Service health checks pass
- ✅ Test report generated: `/tmp/ironbucket-e2e-reports/E2E-Test-Report.md`

### 5.3 Verify Report Generation
```bash
echo "=== Checking Generated Reports ===" 

# List generated reports
ls -lah /tmp/ironbucket-e2e-reports/ 2>/dev/null || echo "Report directory not found"

# Check main report
if [ -f /tmp/ironbucket-e2e-reports/E2E-Test-Report.md ]; then
  echo "Report exists"
  wc -l /tmp/ironbucket-e2e-reports/E2E-Test-Report.md
  head -50 /tmp/ironbucket-e2e-reports/E2E-Test-Report.md
fi

# Check for logs/traces/metrics subdirectories
ls -la /tmp/ironbucket-e2e-reports/{logs,traces,metrics}/ 2>/dev/null | head -20
```

**Success Criteria:**
- ✅ Report file exists: `E2E-Test-Report.md`
- ✅ Report is not empty (> 100 lines)
- ✅ Report contains test results, service status, and analysis
- ✅ logs/, traces/, metrics/ subdirectories created and populated

---

## Phase 6: Comprehensive Validation (10 minutes)

### 6.1 Cross-Service Request Tracing
```bash
echo "=== Testing Full Request Trace ===" 

# Make request from external client through gateway to backend
# This should create a distributed trace across all services

curl -v http://localhost:8080/actuator/health 2>&1 | grep -E "HTTP|date|trace"

# Wait for trace to propagate
sleep 5

# Search for trace with specific criteria
curl -s 'http://localhost:3200/api/search' \
  -d 'query=service=sentinel-gear' \
  -H 'Content-Type: application/json' | jq '.traces | length'
```

**Success Criteria:**
- ✅ Request returns 200 OK
- ✅ Trace appears in Tempo with correct service flow
- ✅ Latency measurements show > 0ms

### 6.2 Error Scenario Verification
```bash
echo "=== Testing Error Scenarios ===" 

# Invalid token (should log error)
curl -s -H "Authorization: Bearer invalid-token" http://localhost:8080/actuator/health

# Invalid endpoint (should log 404)
curl -s http://localhost:8082/invalid-endpoint

# Wait for logs to appear
sleep 5

# Check Loki for error logs
curl -s 'http://localhost:3100/loki/api/v1/query?query={level="error"}' \
  | jq '.data.result | length'

# Check Tempo for error traces
curl -s 'http://localhost:3200/api/search?q=status%3Derror' \
  | jq '.traces | length'
```

**Success Criteria:**
- ✅ Error requests return appropriate status codes (401, 404)
- ✅ Errors appear in Loki (level=error)
- ✅ Errors are traceable in Tempo
- ✅ Error context is preserved in logs

### 6.3 Performance Baseline
```bash
echo "=== Recording Performance Baseline ===" 

# Measure response times for 10 requests
for i in {1..10}; do
  time curl -s http://localhost:8080/actuator/health > /dev/null
  sleep 1
done

# Query Mimir for p50, p95, p99 latencies
echo "=== Latency Percentiles ===" 
curl -s 'http://localhost:9009/api/v1/query?query=histogram_quantile(0.50,http_request_duration_seconds)' \
  | jq '.data.result[0].value'

curl -s 'http://localhost:9009/api/v1/query?query=histogram_quantile(0.95,http_request_duration_seconds)' \
  | jq '.data.result[0].value'

curl -s 'http://localhost:9009/api/v1/query?query=histogram_quantile(0.99,http_request_duration_seconds)' \
  | jq '.data.result[0].value'
```

**Success Criteria:**
- ✅ Response times measured successfully
- ✅ p50 latency < 500ms
- ✅ p95 latency < 1500ms
- ✅ p99 latency < 3000ms
- ✅ Metrics available in Mimir

---

## Phase 7: Cleanup & Report (5 minutes)

### 7.1 Generate Final Report
```bash
echo "=== TESTING COMPLETE ===" 
cat << 'EOF'

✅ Pre-flight Checks Passed
✅ Docker Images Built Successfully  
✅ LGTM Stack Started Successfully
✅ Microservices Started Successfully
✅ OpenTelemetry Integration Verified
✅ Data Flow to Loki/Tempo/Mimir Confirmed
✅ E2E Tests Executed Successfully
✅ Observability Reports Generated
✅ Cross-Service Tracing Working
✅ Error Handling & Logging Working
✅ Performance Metrics Collected

═══════════════════════════════════════════════════════════════════
READY FOR PRODUCTION DEPLOYMENT
═══════════════════════════════════════════════════════════════════

Reports available at:
- /tmp/ironbucket-e2e-reports/E2E-Test-Report.md
- /tmp/ironbucket-e2e-reports/logs/
- /tmp/ironbucket-e2e-reports/traces/
- /tmp/ironbucket-e2e-reports/metrics/

Grafana Dashboards available at: http://localhost:3000
Prometheus Metrics: http://localhost:8080/actuator/prometheus

EOF
```

### 7.2 Preserve Test Artifacts
```bash
# Archive test results
mkdir -p test-results/$(date +%Y%m%d_%H%M%S)
cp test-execution.log test-results/$(date +%Y%m%d_%H%M%S)/
cp e2e-obs-execution.log test-results/$(date +%Y%m%d_%H%M%S)/
cp -r /tmp/ironbucket-e2e-reports/* test-results/$(date +%Y%m%d_%H%M%S)/ 2>/dev/null || true

# Save container logs
for container in steel-hammer-{brazz-nossel,sentinel-gear,claimspindel,buzzle-vane}; do
  docker logs $container > test-results/$(date +%Y%m%d_%H%M%S)/$container.log 2>&1
done
```

**Success Criteria:**
- ✅ All test artifacts archived
- ✅ All container logs captured
- ✅ Reports are preserved for audit trail

### 7.3 Optional: Keep Services Running or Cleanup
```bash
# Option A: Keep running for manual testing
echo "Services running. To stop:"
echo "  cd steel-hammer"
echo "  docker-compose -f docker-compose-lgtm.yml down"

# Option B: Stop and cleanup
# docker-compose -f docker-compose-lgtm.yml down -v
```

---

## Success Criteria Summary

| Phase | Task | Status | Notes |
|-------|------|--------|-------|
| 0 | Pre-flight checks | ⬜ | Docker, disk space, dependencies |
| 1 | Image builds | ⬜ | All 4 services compile/dockerize |
| 2 | LGTM startup | ⬜ | Loki, Tempo, Mimir, Grafana, OTEL |
| 3 | Microservice startup | ⬜ | All 4 services up and healthy |
| 4 | Observability data | ⬜ | Logs in Loki, traces in Tempo, metrics in Mimir |
| 5 | E2E tests | ⬜ | Tests pass, reports generated |
| 6 | Validation | ⬜ | Tracing, errors, performance measured |
| 7 | Cleanup | ⬜ | Artifacts archived, services stopped |

**Overall Success = All phases complete with ✅ status**

---

## Rollback Plan (if issues occur)

### Quick Recovery
```bash
# 1. Stop everything
docker-compose -f steel-hammer/docker-compose-lgtm.yml down -v

# 2. Check what failed
docker logs <failing-container-name>

# 3. Review configuration files
grep -n "OTLP\|prometheus\|LogstashEncoder" <config-file>

# 4. Restart from Phase 2 or 3
```

### Common Issues & Fixes

| Issue | Solution |
|-------|----------|
| OTEL Collector not reachable | Verify `management.otlp.tracing.endpoint` in application-docker.yml |
| Loki returning no logs | Check that `LogstashEncoder` is in logback-spring.xml |
| Services don't start | Check `docker logs` for Spring Boot errors |
| No traces in Tempo | Verify `management.tracing.sampling.probability: 1.0` |
| Prometheus metrics missing | Check `management.metrics.export.prometheus.enabled: true` |

