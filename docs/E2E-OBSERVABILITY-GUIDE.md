# Leveraging Observability for Better E2E Test Reports

## Overview

IronBucket includes a complete **LGTM observability stack** (Loki, Grafana, Tempo, Mimir) that can be integrated with E2E tests to provide comprehensive insights, traces, metrics, and logs in test reports.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              E2E Test Execution Layer                       │
├─────────────────────────────────────────────────────────────┤
│  - Test scripts (bash, curl, native clients)                │
│  - HTTP health checks across all services                   │
│  - Authentication flow validation                           │
│  - Multi-tenant scenario testing                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ↓ Instrumentation
┌──────────────────────────────────────────────────────────────┐
│        OpenTelemetry Collector (Port 4317)                   │
├──────────────────────────────────────────────────────────────┤
│  - Receives spans from all services                          │
│  - Aggregates logs, metrics, traces                          │
│  - Forwards to observability backends                        │
└──────────────────┬──────────────────────────────────────────┘
                   │
       ┌───────────┼───────────┐
       ↓           ↓           ↓
  ┌─────────┐  ┌──────┐  ┌────────┐
  │  Loki   │  │Tempo │  │ Mimir  │
  │(Logs)   │  │(Traces) │(Metrics)
  └────┬────┘  └──┬───┘  └────┬───┘
       │         │           │
       └────────────┬────────┘
                    ↓
           ┌──────────────────┐
           │     Grafana      │
           │ (Visualization)  │
           └──────────────────┘
                    ↓
     ┌──────────────────────────────┐
     │  E2E Test Reports with:       │
     │  - Test results               │
     │  - Service traces             │
     │  - Performance metrics        │
     │  - Log excerpts               │
     │  - Latency analysis           │
     └──────────────────────────────┘
```

## What We're Collecting

### 1. **Logs (Loki)**
- All service logs (Sentinel-Gear, Brazz-Nossel, Claimspindel, etc.)
- Application logs, debug traces, errors
- Structured JSON logs with labels

```bash
# Query logs for a specific service
curl http://localhost:3100/loki/api/v1/query_range \
  ?query='{job="brazz-nossel"}' \
  &start=1626785000 \
  &end=1626788600
```

### 2. **Traces (Tempo)**
- Full request path through all services
- Span timing and duration
- Service-to-service communication
- Error traces with stack traces
- Performance bottlenecks

```bash
# Search for traces
curl http://localhost:3200/api/traces?service=brazz-nossel
```

### 3. **Metrics (Mimir)**
- HTTP request latency (p50, p95, p99)
- Error rates per service
- Request throughput
- Resource utilization (CPU, memory)
- Custom business metrics

```bash
# Query metrics
curl http://localhost:9009/api/v1/query?query=http_request_duration_seconds
```

### 4. **Service Health**
- Health check endpoints on all services
- Startup time measurements
- Dependency readiness verification

## How to Use in E2E Tests

### Quick Start: Run E2E with Observability

```bash
cd /workspaces/IronBucket/steel-hammer

# Start services with LGTM stack
docker-compose -f docker-compose-lgtm.yml up -d

# Wait for initialization (90-120s)
sleep 120

# Run E2E tests with observability integration
bash test-scripts/e2e-with-observability.sh

# Access reports
ls -la /tmp/ironbucket-e2e-reports/
```

### Generated Reports Include:

1. **Test Results Summary**
   - Pass/fail status per test
   - Success rate percentage
   - Detailed error messages

2. **Service Status Timeline**
   - Health check results
   - Response times per service
   - Startup order and timing

3. **Observability Data**
   - Links to service logs in Loki
   - Trace IDs for correlation
   - Metric queries and results

## Practical Examples

### Example 1: Trace a Failed Authentication Test

```bash
# 1. Get trace ID from test report
TRACE_ID="abc123def456"

# 2. Query Tempo for full trace
curl "http://localhost:3200/api/traces/$TRACE_ID" | jq .

# 3. View in Grafana: http://localhost:3000
#    - Go to Explore → Tempo
#    - Select service: keycloak
#    - Search by trace ID: $TRACE_ID
```

### Example 2: Analyze S3 Gateway Latency

```bash
# 1. Query Mimir for latency metrics
curl "http://localhost:9009/api/v1/query?query=\
  histogram_quantile(0.95, \
    http_request_duration_seconds{service=\"brazz-nossel\"})"

# 2. View in Grafana dashboard
#    - Go to Dashboards → Service Latency
#    - Filter by service: brazz-nossel
#    - View p50, p95, p99 latencies

# 3. Compare with baseline
#    - Historical trends
#    - Regression detection
#    - SLA compliance
```

### Example 3: Correlate Errors Across Services

```bash
# 1. Find errors in logs
curl "http://localhost:3100/loki/api/v1/query_range?query=\
  {job=~\".*-nossel\"} | json | level=\"error\""

# 2. Extract trace IDs from logs
# Look for: trace_id=abc123 in log entries

# 3. Fetch full traces
curl "http://localhost:3200/api/traces/abc123" | jq .

# 4. See which services were involved
#    and where the error originated
```

## Report Customization

### Enhanced Test Report Template

Create custom reports that include:

```markdown
# E2E Test Report - [Date]

## Test Results
- ✅ 45 tests passed
- ❌ 2 tests failed
- ⏭️  3 tests skipped

## Performance Metrics
- Average latency: 125ms (p50)
- 95th percentile: 350ms
- 99th percentile: 1200ms

## Service Status
| Service | Status | Latency | Error Rate |
|---------|--------|---------|------------|
| Brazz-Nossel | ✅ | 145ms | 0.1% |
| Claimspindel | ✅ | 112ms | 0% |

## Trace Analysis
- Slowest request: PUT /bucket/key (1250ms)
  - Auth: 45ms (Keycloak)
  - Policy check: 120ms (Claimspindel)
  - S3 write: 1085ms (MinIO)

## Failed Tests Details
### Test: S3 Gateway JWT Validation
- Error: Access token validation failed
- Trace ID: 4a2f8c9d3b1e7f5a
- Logs: View in Grafana → Loki → {job="brazz-nossel"}

## Logs
See: /tmp/ironbucket-e2e-reports/logs/
```

## Integration with CI/CD

### GitHub Actions Example

```yaml
- name: Run E2E Tests with Observability
  run: |
    bash steel-hammer/test-scripts/e2e-with-observability.sh
    
- name: Upload Observability Reports
  if: always()
  uses: actions/upload-artifact@v2
  with:
    name: e2e-reports
    path: /tmp/ironbucket-e2e-reports/
    
- name: Generate Test Summary
  if: always()
  run: |
    cat /tmp/ironbucket-e2e-reports/E2E-Test-Report.md >> $GITHUB_STEP_SUMMARY
```

## Accessing Grafana Dashboards

### Pre-built Dashboards

1. **Service Health Overview**
   - All services on one dashboard
   - Real-time health status
   - Uptime statistics

2. **Request Latency Analysis**
   - Percentile distribution (p50, p95, p99)
   - Slowest endpoints
   - Latency trends over time

3. **Error Rate Monitoring**
   - Errors per service
   - Error rate trends
   - Most common errors

4. **Resource Utilization**
   - CPU and memory per container
   - Network I/O
   - Storage usage

### Access URL

```
http://localhost:3000
Username: admin
Password: admin
```

## Troubleshooting Common Issues

### Issue: Tests Fail But Services Show Healthy

**Diagnosis:**
1. Check Tempo traces to see actual request paths
2. Look for error spans in service traces
3. Verify trace correlation IDs match

**Example:**
```bash
# View failed request trace
curl http://localhost:3200/api/traces?service=brazz-nossel \
  | jq '.traces[] | select(.spans[].tags.error==true)'
```

### Issue: Latency Higher Than Expected

**Steps:**
1. Query Mimir for histogram percentiles
2. Check Tempo for slowest spans
3. Review Loki logs for warnings/debug info

```bash
# Find slowest requests
curl "http://localhost:3200/api/traces?service=brazz-nossel&limit=10" \
  | jq '.traces[] | {traceID, duration: (.spans[].duration | add)}'
```

### Issue: Missing Traces

**Causes:**
1. OpenTelemetry exporter not configured
2. OTEL_EXPORTER_OTLP_ENDPOINT not set
3. Services not instrumented

**Fix:**
```bash
# Verify services are sending traces
docker logs steel-hammer-tempo | grep -i "span\|trace"
```

## Best Practices

1. **Always Export Reports**
   - Save trace IDs and metrics
   - Create reproducible test scenarios
   - Build historical baselines

2. **Correlate Events**
   - Use trace IDs across logs/traces/metrics
   - Link errors to their spans
   - Find root cause of failures

3. **Baseline Metrics**
   - Record healthy state metrics
   - Compare regression detection
   - Monitor SLA compliance

4. **Retention Policies**
   - Set Loki retention to 7-14 days
   - Tempo: keep traces for 48-72 hours
   - Mimir: 1-2 weeks for metrics

5. **Alerting**
   - Set up alert rules in Prometheus
   - Notify on error rate spikes
   - Monitor latency p95/p99

## Advanced: Custom Metrics

Add business metrics to services:

```java
// Example: Track S3 operations
private final MeterRegistry meterRegistry;

public void uploadObject(String bucket, String key) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
        // ... upload logic
        sample.stop(Timer.builder("s3.upload.duration")
            .tag("bucket", bucket)
            .tag("status", "success")
            .register(meterRegistry));
    } catch (Exception e) {
        sample.stop(Timer.builder("s3.upload.duration")
            .tag("bucket", bucket)
            .tag("status", "error")
            .register(meterRegistry));
    }
}
```

Query in Grafana:
```prometheus
rate(s3_upload_duration_seconds_count[1m])
```

## Summary

By integrating LGTM observability into E2E tests, you get:

✅ **Complete Visibility** - See everything that happens during tests
✅ **Root Cause Analysis** - Quickly identify why tests fail
✅ **Performance Insights** - Track latency, throughput, errors
✅ **Historical Trends** - Detect regressions early
✅ **Better Reports** - Data-driven test documentation

This transforms E2E tests from simple pass/fail into comprehensive operational insights!
