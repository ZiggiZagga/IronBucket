# IronBucket E2E Quick Start

## One-Command Spinup 🚀

Run the entire IronBucket stack with E2E tests in one command:

```bash
bash scripts/spinup.sh
```

## Roadmap Proof Gate (Phase 1-4)

Run the same deterministic roadmap E2E gate that CI enforces:

```bash
bash scripts/e2e/prove-phase1-4-complete.sh
```

Artifacts are written to:

```bash
test-results/phase1-4-proof/
```

## Observability Proof Gate (Phase 2)

Run the same observability proof gate that the e2e workflow executes:

```bash
bash scripts/e2e/prove-phase2-observability.sh
```

Artifacts are written to:

```bash
test-results/phase2-observability/
```

## LGTM Fast Diagnostics

Run a one-command diagnostics snapshot for Loki, Tempo, Mimir, Grafana, Keycloak, and MinIO:

```bash
bash scripts/e2e/diagnose-lgtm.sh
```

Artifacts are written to:

```bash
test-results/lgtm-diagnostics/
```

## Phase 1-3 Gate (Keep Containers Running)

Rerun the full Phase 1-3 proof and keep the stack alive for follow-up debugging:

```bash
KEEP_STACK=true bash scripts/e2e/prove-phase1-3-complete.sh
```

Artifacts are written to:

```bash
test-results/phase1-3-proof/
```

## Steel-Hammer Complete Suite (Container Runtime)

For container-runtime parity checks and deep diagnostics, run the steel-hammer suite directly:

```bash
sh steel-hammer/test-scripts/e2e-complete-suite.sh
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
  • Keycloak (OIDC): internal-only by default (https://steel-hammer-keycloak:7081 from Docker network)
  • Sentinel-Gear (Gateway): http://localhost:8080
  • Claimspindel (Policy): internal-only by default
  • Brazz-Nossel (S3 Proxy): internal-only by default
  • Buzzle-Vane (Discovery): internal-only by default
  • MinIO (Storage): internal-only by default (https://steel-hammer-minio:9000 from Docker network)

Management:
  • View logs: docker-compose -f steel-hammer/docker-compose-steel-hammer.yml logs -f
  • Stop services: docker-compose -f steel-hammer/docker-compose-steel-hammer.yml down
  • Restart: bash scripts/spinup.sh

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
bash scripts/spinup.sh
```

### Maven Test Failures

Run selective module tests:

```bash
# Test single module
cd services/Brazz-Nossel
mvn test

# Skip tests and only start services
bash scripts/spinup.sh --test-only
```

## Options

```bash
# Run only Maven tests (no Docker)
bash scripts/spinup.sh --local-only

# Run only container tests
bash scripts/spinup.sh --test-only

# Run only E2E checks
bash scripts/spinup.sh --e2e-only

# Debug mode
bash scripts/spinup.sh --debug

# Follow logs
bash scripts/spinup.sh --logs
```

## What Services Are Running?

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Expected output:
```
steel-hammer-keycloak    Up 2 minutes
steel-hammer-postgres    Up 2 minutes
steel-hammer-minio       Up 2 minutes
steel-hammer-sentinel-gear    Up 90 seconds   0.0.0.0:8080->8080/tcp
steel-hammer-claimspindel     Up 90 seconds
steel-hammer-brazz-nossel     Up 90 seconds
steel-hammer-buzzle-vane      Up 90 seconds
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

1. **Verify internal service endpoints from Docker network**:
  ```bash
  NET=steel-hammer_steel-hammer-network
  docker run --rm --network "$NET" curlimages/curl:8.12.1 -k -sS https://steel-hammer-keycloak:7081/realms/dev/.well-known/openid-configuration
  docker run --rm --network "$NET" curlimages/curl:8.12.1 -k -sS https://steel-hammer-minio:9000/minio/health/live
  ```

2. **Test S3 Operations**:
   ```bash
   # Get token (resource owner flow used by E2E checks)
  NET=steel-hammer_steel-hammer-network
  TOKEN=$(docker run --rm --network "$NET" curlimages/curl:8.12.1 -k -s -X POST "https://steel-hammer-keycloak:7081/realms/dev/protocol/openid-connect/token" \
    -d "client_id=dev-client" \
    -d "client_secret=dev-secret" \
     -d "grant_type=password" \
     -d "username=alice" \
     -d "password=aliceP@ss" \
     | jq -r .access_token)
   
   # List buckets
  docker run --rm --network "$NET" curlimages/curl:8.12.1 -sS -H "Authorization: Bearer $TOKEN" http://steel-hammer-brazz-nossel:8082/s3/buckets
   ```

3. **View Service Discovery**:
   ```bash
  NET=steel-hammer_steel-hammer-network
  docker run --rm --network "$NET" curlimages/curl:8.12.1 -sS http://steel-hammer-buzzle-vane:8083/eureka/apps
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
        run: bash scripts/spinup.sh
      - name: Upload Logs
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-logs
          path: |
            test-execution.log
            spinup-*.log
```
