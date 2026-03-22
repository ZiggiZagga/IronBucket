# IronBucket Troubleshooting Guide

## Common Issues

### Services Not Starting

**Problem:** Docker containers fail to start or are stuck

**Solutions:**

```bash
# Check container status
docker ps -a

# View logs
docker logs steel-hammer-test

# Remove and restart
docker-compose down -v
docker-compose up -d --build
```

### Port Already in Use

**Problem:** `Address already in use` error

**Solutions:**

```bash
# Find what's using the port (example: 8080)
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or change port in docker-compose.yml
ports:
  - "8081:8080"  # Use 8081 instead
```

### Out of Memory

**Problem:** `java.lang.OutOfMemoryError: Java heap space`

**Solutions:**

```bash
# Increase Docker memory
# Edit docker-compose.yml:
environment:
  - JAVA_OPTS=-Xmx4g  # Allocate 4GB

# Or increase Docker Desktop limit:
# Docker → Preferences → Resources → Memory: 8GB+
```

### Database Connection Refused

**Problem:** `Connection refused` on port 5432

**Solutions:**

```bash
# Wait for PostgreSQL to fully start
sleep 30

# Test PostgreSQL connection
docker exec steel-hammer-postgres psql -U postgres -c "SELECT 1"

# Check PostgreSQL logs
docker logs steel-hammer-postgres
```

### JWT Validation Failures

**Problem:** HTTP 401 or 403 errors

**Solutions:**

1. **Missing JWT Token**
   ```bash
   # Request must include Authorization header
   curl -H "Authorization: Bearer <JWT_TOKEN>" \
        https://localhost:8082/
   ```

2. **Invalid Signature**
   ```bash
   # Ensure JWT is signed with correct secret
   # Check Sentinel-Gear logs:
   docker logs steel-hammer-sentinel-gear | grep -i "signature"
   ```

3. **Expired Token**
   ```bash
   # Get a fresh token from Keycloak
   # Default token expiration: 3600 seconds (1 hour)
   ```

### File Upload Failing

**Problem:** S3 PutObject returns error

**Solutions:**

1. **Check MinIO is running**
   ```bash
   docker ps | grep minio
   ```

2. **Check bucket exists**
   ```bash
   docker exec steel-hammer-minio /opt/minio/minio-binaries/mc ls minio/
   ```

3. **Check permissions**
   ```bash
   # Ensure user has PutObject permission
   # Check policy in Claimspindel
   docker logs steel-hammer-claimspindel | grep -i "policy"
   ```

### Slow Performance

**Problem:** Operations taking longer than expected

**Solutions:**

1. **Check resource usage**
   ```bash
   docker stats
   ```

2. **Increase resource limits**
   ```yaml
   # In docker-compose.yml
   services:
     brazz-nossel:
       deploy:
         resources:
           limits:
             cpus: '2'
             memory: 2G
   ```

3. **Optimize database**
   ```bash
   # Check PostgreSQL query logs
   docker logs steel-hammer-postgres | grep "slow"
   ```

### MinIO Console Not Accessible

**Problem:** Can't access MinIO dashboard

**Solutions:**

```bash
# Check MinIO is running
docker ps | grep minio

# Access MinIO Console
# URL: https://localhost:9001
# Username: minioadmin
# Password: minioadmin

# Check ports are correctly mapped
docker port steel-hammer-minio
```

### Keycloak Login Issues

**Problem:** Can't login to Keycloak

**Solutions:**

```bash
# Check Keycloak is running
docker ps | grep keycloak

# Access Keycloak Admin Console
# URL: https://localhost:8080
# Username: admin
# Password: admin

# Check user exists
docker exec steel-hammer-keycloak /opt/keycloak/bin/kcadm.sh list-users -r dev --admin-server-url https://localhost:8080
```

### Keycloak mTLS Browser Flow Returns No Authorization Redirect

**Problem:** `scripts/e2e/e2e-keycloak-mtls-minio-oidc.sh` fails with:

```text
No redirect location found for bob. Keycloak did not issue an auth redirect.
```

**Diagnosis:** Keycloak serves login HTML (`HTTP 200`) instead of issuing an auth-code redirect with `code=...`. This indicates x509 browser-flow mapping is not completing automatic user binding.

**Solutions:**

```bash
# Reproduce and inspect headers/body in test-network context
cd steel-hammer
docker compose -f docker-compose-steel-hammer.yml run --rm --entrypoint /bin/bash steel-hammer-test -lc '
AUTH_URL="https://steel-hammer-keycloak:7081/realms/dev/protocol/openid-connect/auth?client_id=dev-client&redirect_uri=https://steel-hammer-minio:9001/oauth_callback&response_type=code&scope=openid"
curl -k -sS -D /tmp/h.txt -o /tmp/b.txt --cert /certs/client/bob.crt --key /certs/client/bob.key "$AUTH_URL"
head -n 20 /tmp/h.txt
head -n 40 /tmp/b.txt
'

# Verify realm flow import and browser flow assignment
docker logs --tail 200 steel-hammer-keycloak | grep -Ei 'import|flow|x509|realm|error|warn'
```

**Expected fix direction:** ensure x509 authenticator mapping matches certificate subject/username for `bob` and `charly`, and verify `browserFlow` assignment is effective after realm import.

### Service Discovery Not Working

**Problem:** Services can't find each other

**Solutions:**

```bash
# Check Buzzle-Vane (Eureka) is running
docker ps | grep buzzle

# Access Eureka Dashboard
# URL: https://localhost:8083/eureka/web

# Check service registration
# Should see all 6 services registered

# If services missing, restart them:
docker-compose restart brazz-nossel
docker-compose restart sentinel-gear
docker-compose restart claimspindel
```

## LGTM Observability Troubleshooting

### One-Command LGTM Diagnostics

```bash
# Generates readiness, API, and log evidence under test-results/lgtm-diagnostics/<timestamp>
bash scripts/e2e/diagnose-lgtm.sh
```

### LGTM Services Are Running But Host `localhost` Checks Fail

**Problem:** Containers are `Up`, but checks like `curl http://localhost:3100/ready` fail from host shell.

**Why this happens:** In `steel-hammer/docker-compose-lgtm.yml`, most LGTM services are not published to host ports.

**Solutions:**

```bash
# Check LGTM container status first
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'steel-hammer-(loki|tempo|mimir|grafana|promtail|otel-collector)'

# Run checks from inside the compose network instead of host localhost
docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -sS https://steel-hammer-loki:3100/ready
docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -sS https://steel-hammer-tempo:3200/ready
docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -sS https://steel-hammer-mimir:9009/ready
```

### LGTM Readiness Returns 503 During Startup

**Problem:** `tempo`/`mimir`/`loki` return HTTP 503 for a short time right after `docker compose up -d`.

**Diagnosis:** Usually startup warm-up, ring/bootstrap, or storage init delay.

**Solutions:**

```bash
# Retry readiness checks before declaring failure
for i in {1..8}; do
   code=$(docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -sS -o /dev/null -w '%{http_code}' https://steel-hammer-mimir:9009/ready || true)
   echo "mimir:$code attempt:$i"
   [ "$code" = "200" ] && break
   sleep 2
done
```

### Full `run-e2e-complete.sh` Stops On `NOT READY: Loki|Tempo|Mimir`

**Problem:** The complete E2E runner progresses through Java and UI gates but pauses/fails in Phase 2 with lines such as `NOT READY: Loki`, `NOT READY: Tempo`, or `NOT READY: Mimir`.

**Diagnosis:** This is usually a readiness warm-up race in the LGTM stack, not an application regression in Sentinel/Graphite/MinIO flows.

**Solutions:**

```bash
# 1) Verify core app services are already healthy
docker ps --format '{{.Names}} {{.Status}}' | grep 'steel-hammer-' | sort

# 2) Check LGTM readiness endpoints from the compose network
docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -sk https://steel-hammer-loki:3100/ready
docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -sk https://steel-hammer-tempo:3200/ready
docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -sk https://steel-hammer-mimir:9009/ready

# 3) Re-run only the observability gate once readiness is stable
bash scripts/ci/run-observability-infra-gate.sh
```

If you need complete-run evidence with screenshots and logs, run:

```bash
bash steel-hammer/test-scripts/run-e2e-complete.sh
```

Then verify the screenshot bundle:

```bash
find test-results/e2e-complete -maxdepth 3 -type d -name browser-screenshots | sort | tail -1
```

Expected files in that folder:
- `object-browser-baseline-proof.png`
- `ui-governance-methods-proof.png`
- `ui-live-upload-proof.png`
- `ui-s3-methods-proof.png`
- `ui-s3-methods-performance-proof.png`

### Collector Shows "Failed to Scrape Prometheus Endpoint"

**Problem:** `steel-hammer-otel-collector` logs show warnings like `Failed to scrape Prometheus endpoint` for some jobs.

**Diagnosis:** Common during startup race while services are not fully ready yet.

**Solutions:**

```bash
# Inspect collector warnings/errors
docker logs --tail 200 steel-hammer-otel-collector 2>&1 | grep -Ei 'error|warn|failed|refused|timeout'

# Confirm dependencies are healthy, then re-check after warm-up
docker ps --format '{{.Names}}\t{{.Status}}' | grep -E 'steel-hammer-(keycloak|minio|claimspindel|sentinel-gear)'
```

### Loki Query Errors With Instant Query API

**Problem:** Querying logs with `/loki/api/v1/query` returns bad request for log selectors.

**Diagnosis:** Instant query endpoint does not support log queries in the same way as range queries.

**Solutions:**

```bash
# Use label discovery for quick smoke checks
docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -sS \
   'https://steel-hammer-loki:3100/loki/api/v1/label/service_name/values'

# Use query_range for log content retrieval
docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -G -sS \
   --data-urlencode 'query={service_name=~".+"}' \
   --data-urlencode 'limit=100' \
   --data-urlencode "start=$(date -u -d '5 minutes ago' +%s%N)" \
   --data-urlencode "end=$(date -u +%s%N)" \
   'https://steel-hammer-loki:3100/loki/api/v1/query_range'
```

### Fast LGTM API Smoke Test

```bash
# Mimir API status and sample count
docker run --rm --network steel-hammer_steel-hammer-network curlimages/curl:8.7.1 -sS \
   'https://steel-hammer-mimir:9009/prometheus/api/v1/query?query=up' | jq '.status, (.data.result|length)'

# Grafana logs for provisioning hints (datasources/dashboards)
docker logs --tail 200 steel-hammer-grafana 2>&1 | grep -Ei 'error|warn|provision|datasource'
```

## Network Troubleshooting

### Test Connectivity Between Services

```bash
# Test from test container
docker exec steel-hammer-test curl https://brazz-nossel:8082/health

# Test MinIO connectivity
docker exec steel-hammer-test curl https://minio:9000/minio/health/live
```

### Check Network

```bash
# List networks
docker network ls

# Inspect network
docker network inspect steel-hammer-network

# Should see all containers connected
```

## Data Troubleshooting

### PostgreSQL Data Issues

```bash
# Connect to database
docker exec -it steel-hammer-postgres psql -U postgres -d postgres

# List tables
\dt

# Query audit logs
SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 10;

# Check data integrity
SELECT COUNT(*) FROM audit_logs;
```

### MinIO Data Issues

```bash
# List buckets
docker exec steel-hammer-minio /opt/minio/minio-binaries/mc ls minio/

# List objects
docker exec steel-hammer-minio /opt/minio/minio-binaries/mc ls minio/bucket-name/

# Download file for inspection
docker exec steel-hammer-minio /opt/minio/minio-binaries/mc cat minio/bucket/object
```

## Logs and Debugging

### View Real-time Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f brazz-nossel

# Last 100 lines
docker-compose logs --tail=100 brazz-nossel
```

### Search Logs

```bash
# Find errors
docker logs steel-hammer-brazz-nossel 2>&1 | grep -i error

# Find JWT issues
docker logs steel-hammer-sentinel-gear | grep -i jwt

# Find database issues
docker logs steel-hammer-postgres | grep -i error
```

### Export Logs

```bash
# Save logs to file
docker logs steel-hammer-brazz-nossel > brazz-nossel.log

# Archive all logs
docker-compose logs > all-services.log
```

## Health Checks

### Check Service Health Endpoints

```bash
# Brazz-Nossel
curl https://localhost:8082/actuator/health

# Sentinel-Gear
curl https://localhost:8080/actuator/health

# Claimspindel
curl https://localhost:8081/actuator/health

# Buzzle-Vane
curl https://localhost:8083/actuator/health
```

### Verify All Services

```bash
#!/bin/bash
SERVICES=("8080" "8081" "8082" "8083" "9000" "5432")

for PORT in "${SERVICES[@]}"; do
  if nc -z localhost "$PORT" 2>/dev/null; then
    echo "✅ Service on port $PORT is running"
  else
    echo "❌ Service on port $PORT is NOT running"
  fi
done
```

## Reset Everything

### Complete Clean Restart

```bash
# Stop all services
docker-compose down

# Remove all data
docker-compose down -v

# Remove all containers and images
docker system prune -a

# Rebuild and start
docker-compose up -d --build
```

### Reset Database Only

```bash
# Stop PostgreSQL
docker-compose stop postgres

# Remove database volume
docker volume rm steel-hammer_postgres-data

# Restart
docker-compose up -d postgres
```

### Reset MinIO Only

```bash
# Stop MinIO
docker-compose stop minio

# Remove data volume
docker volume rm steel-hammer_minio-data

# Restart
docker-compose up -d minio
```

## Performance Optimization

### Enable Connection Pooling

```yaml
# docker-compose.yml environment variables
DATABASE_URL: "jdbc:postgresql://postgres:5432/ironbucket?maxPoolSize=20&minPoolSize=5"
```

### Increase Timeouts

```yaml
environment:
  SERVER_SERVLET_SESSION_TIMEOUT: "1800s"
  SPRING_JPA_HIBERNATE_JDBC_BATCH_SIZE: "25"
  SPRING_JPA_HIBERNATE_ORDER_INSERTS: "true"
  SPRING_JPA_HIBERNATE_ORDER_UPDATES: "true"
```

## Getting Help

### Debug Checklist

Before reporting issues:
1. [ ] Run latest version: `git pull && docker-compose build`
2. [ ] Clean restart: `docker-compose down -v && docker-compose up`
3. [ ] Check logs: `docker logs service-name`
4. [ ] Verify prerequisites: Java 25+, Maven 3.9+, Docker latest
5. [ ] Check port conflicts: `lsof -i :8080`
6. [ ] Check disk space: `df -h`
7. [ ] Check memory: `docker stats`

### Report Issues

When reporting issues, include:
1. OS and version
2. Docker version: `docker --version`
3. Docker Compose version: `docker-compose --version`
4. Full error message from logs
5. Steps to reproduce
6. Output of `docker ps -a`

## Status

**Troubleshooting Guide:** ✅ Comprehensive  
**Common Issues:** ✅ Covered  
**Solutions:** ✅ Provided  
**Self-Service:** ✅ Enabled
