# IronBucket E2E Quick Start

## One-Command Spinup 🚀

Run the entire IronBucket stack with E2E tests in one command:

```bash
./spinup.sh
```

## What It Does

1. **Prerequisites Check** (5s)
   - Verifies Docker, docker-compose, Maven
   - Checks required directories

2. **Maven Unit Tests** (~2-3 minutes)
   - Brazz-Nossel (S3 Proxy)
   - Buzzle-Vane (Service Discovery)
   - Claimspindel (Policy Engine)
   - Sentinel-Gear (API Gateway)
   - Vault-Smith (Secrets Management)
   - graphite-admin-shell (Admin)

3. **Docker Services Startup** (~90-120 seconds)
   - PostgreSQL (instant)
   - MinIO (5-10s)
   - Keycloak (**60-90s** - takes longest!)
   - All Spring Boot microservices (15s)

4. **Service Health Checks** (~5s)
   - Keycloak OIDC endpoint
   - PostgreSQL readiness
   - MinIO health
   - Spring Boot actuator/health endpoints

5. **E2E Integration Tests** (~30-60s)
   - Alice & Bob Multi-Tenant Scenario
   - JWT token flow validation
   - S3 operations through gateway
   - Service mesh traces

## Expected Output

```
╔══════════════════════════════════════════════════════════════════╗
║           IronBucket - Complete Test & Spin-Up Suite             ║
╚══════════════════════════════════════════════════════════════════╝

▶ Step 1: Verify Prerequisites
✅ docker installed
✅ docker-compose installed
✅ mvn installed
✅ steel-hammer directory found
✅ temp directory found

▶ Step 2: Run Maven Unit Tests (All 6 Projects)
✅ Brazz-Nossel tests passed
✅ Buzzle-Vane tests passed
✅ Claimspindel tests passed
✅ Sentinel-Gear tests passed
✅ Vault-Smith tests passed
✅ graphite-admin-shell tests passed

▶ Step 3: Build Docker Images
✅ Docker images built successfully

▶ Step 4: Start Docker Services
✅ Docker services started

▶ Step 5: Wait for Service Initialization

Waiting for Keycloak (this takes longest, ~60-90 seconds)...
.....................✅ Keycloak is ready (took 75s)

Checking Spring Boot services (additional 15s for startup)...

Checking PostgreSQL...
✅ PostgreSQL is ready

Checking MinIO...
✅ MinIO is ready

Checking Spring Boot service health endpoints...
✅ Sentinel-Gear is healthy
✅ Claimspindel is healthy
✅ Brazz-Nossel is healthy
✅ Buzzle-Vane is healthy

✅ All services initialized

▶ Step 6: Run E2E Integration Tests

Running E2E Test: Alice & Bob Multi-Tenant Scenario...
✅ E2E Alice & Bob test passed

Running E2E Verification with Service Traces...
✅ E2E verification test passed

╔══════════════════════════════════════════════════════════════════╗

✅ IRONBUCKET SPIN-UP COMPLETE

Summary:
  ✅ Maven Unit Tests: COMPLETED
  ✅ Docker Services: RUNNING
  ✅ E2E Integration Tests: COMPLETED

Services Ready:
  • Keycloak (OIDC): http://localhost:7081
  • Sentinel-Gear (Gateway): http://localhost:8080
  • Claimspindel (Policy): http://localhost:8081
  • Brazz-Nossel (S3 Proxy): http://localhost:8082
  • Buzzle-Vane (Discovery): http://localhost:8083
  • MinIO (Storage): http://localhost:9000

Management:
  • View logs: docker-compose -f steel-hammer/docker-compose-steel-hammer.yml logs -f
  • Stop services: docker-compose -f steel-hammer/docker-compose-steel-hammer.yml down
  • Restart: ./spinup.sh

Full test log: /workspaces/IronBucket/test-execution.log

System ready for development and testing! 🚀

╚══════════════════════════════════════════════════════════════════╝
```

## Timing Breakdown

- **Total Time**: ~5-7 minutes
  - Maven tests: 2-3 min
  - Docker startup: 2-2.5 min (Keycloak dominates)
  - E2E tests: 30-60s

## Troubleshooting

### Keycloak Taking Too Long

If Keycloak doesn't start after 120 seconds:

```bash
# Check logs
docker logs steel-hammer-keycloak

# Common fixes:
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml restart keycloak
```

### Port Already in Use

```bash
# Stop any running services
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml down

# Kill process on port (e.g., 8080)
lsof -ti:8080 | xargs kill -9

# Restart
./spinup.sh
```

### Maven Test Failures

Run selective module tests:

```bash
# Test single module
cd services/Brazz-Nossel
mvn test

# Skip tests and only start services
./spinup.sh --skip-tests
```

## Options

```bash
# Skip Maven tests, only Docker + E2E
./spinup.sh --skip-tests

# Only Maven tests (no Docker)
./spinup.sh --local-only

# Help
./spinup.sh --help
```

## What Services Are Running?

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Expected output:
```
steel-hammer-keycloak    Up 2 minutes    0.0.0.0:7081->7081/tcp
steel-hammer-postgres    Up 2 minutes    0.0.0.0:5432->5432/tcp
steel-hammer-minio       Up 2 minutes    0.0.0.0:9000-9001->9000-9001/tcp
steel-hammer-sentinel-gear    Up 90 seconds   0.0.0.0:8080->8080/tcp
steel-hammer-claimspindel     Up 90 seconds   0.0.0.0:8081->8081/tcp
steel-hammer-brazz-nossel     Up 90 seconds   0.0.0.0:8082->8082/tcp
steel-hammer-buzzle-vane      Up 90 seconds   0.0.0.0:8083->8083/tcp
```

## Stop Everything

```bash
cd steel-hammer
docker-compose -f docker-compose-steel-hammer.yml down

# With volume cleanup
docker-compose -f docker-compose-steel-hammer.yml down -v
```

## Next Steps

After successful spinup:

1. **Access Keycloak Admin**: http://localhost:7081/admin
   - User: `admin`
   - Password: `admin`

2. **Test S3 Operations**:
   ```bash
   # Get token
   TOKEN=$(curl -s -X POST "http://localhost:7081/realms/dev/protocol/openid-connect/token" \
     -d "client_id=test-client" \
     -d "client_secret=secret" \
     -d "grant_type=client_credentials" \
     | jq -r .access_token)
   
   # List buckets
   curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/s3/buckets
   ```

3. **View Service Discovery**:
   ```bash
   curl http://localhost:8083/eureka/apps
   ```

4. **Check Gateway Routes**:
   ```bash
   curl http://localhost:8080/actuator/gateway/routes
   ```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       spinup.sh                             │
│  One command to rule them all                               │
└──────────────┬──────────────────────────────────────────────┘
               │
               ├──▶ Maven Tests (Unit)
               │    └─ All 6 microservices
               │
               ├──▶ Docker Compose Up
               │    ├─ Keycloak (7081)
               │    ├─ PostgreSQL (5432)
               │    ├─ MinIO (9000)
               │    ├─ Sentinel-Gear (8080)
               │    ├─ Claimspindel (8081)
               │    ├─ Brazz-Nossel (8082)
               │    └─ Buzzle-Vane (8083)
               │
               ├──▶ Service Health Checks
               │    └─ Wait for Keycloak + all services
               │
               └──▶ E2E Tests
                    ├─ e2e-test-standalone.sh (Alice & Bob)
                    └─ e2e-verification.sh (Service traces)
```

## CI/CD Integration

For automated pipelines:

```yaml
# .github/workflows/e2e.yml
name: E2E Tests

on: [push, pull_request]

jobs:
  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run E2E Suite
        run: ./spinup.sh
      - name: Upload Logs
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-logs
          path: |
            test-execution.log
            spinup-*.log
```
