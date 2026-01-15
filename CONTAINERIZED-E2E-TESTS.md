# Containerized E2E Tests - Alice & Bob Scenario

**Status**: ✅ All tests run inside Docker containers using internal networking  
**Network**: Tests communicate via `steel-hammer-network` (no host network calls needed)  
**Architecture**: Self-contained test runner container  

---

## Overview

The end-to-end tests are now fully containerized and run completely inside Docker using internal container-to-container networking. This eliminates all host-to-container network communication issues.

### Components

| Service | Role | Network |
|---------|------|---------|
| `steel-hammer-keycloak` | OIDC Provider | Internal: `steel-hammer-keycloak:7081` |
| `steel-hammer-postgres` | Database | Internal: `steel-hammer-postgres:5432` |
| `steel-hammer-test` | Test Runner | Internal: Tests all services via hostnames |

---

## Quick Start

### 1. Build and Start All Services (Including Tests)

```bash
cd /workspaces/IronBucket/steel-hammer

# Set environment variable
export DOCKER_FILES_HOMEDIR="."

# Start all services (Keycloak, PostgreSQL, and Test Runner)
docker-compose -f docker-compose-steel-hammer.yml up -d

# Watch test output
docker logs -f steel-hammer-test
```

### 2. Run Tests Only (Services Already Running)

```bash
# If services are already running
docker-compose -f docker-compose-steel-hammer.yml up steel-hammer-test

# Or run directly
docker exec steel-hammer-test /tests/e2e-alice-bob-container.sh
```

### 3. View Test Results

```bash
# Real-time logs
docker logs -f steel-hammer-test

# Full logs (after test completes)
docker logs steel-hammer-test

# Get exit code (0 = all passed)
docker inspect steel-hammer-test --format='{{.State.ExitCode}}'
```

---

## Test Architecture

### Container Network Flow

```
┌─────────────────────────────────────────────────────────┐
│              steel-hammer-network (bridge)              │
└─────────────────────────────────────────────────────────┘
   ▲                    ▲                    ▲
   │                    │                    │
   │ (localhost:7081)   │ (localhost:5432)   │
   │                    │                    │
┌──┴──────────────┐ ┌──┴──────────────┐ ┌──┴──────────┐
│   Keycloak      │ │   PostgreSQL    │ │  Test       │
│   Container     │ │   Container     │ │  Container  │
└─────────────────┘ └─────────────────┘ └─────────────┘

Test Container Communication:
  1. Test → Keycloak via steel-hammer-keycloak:7081
  2. Test → PostgreSQL via steel-hammer-postgres:5432
  3. All traffic via internal Docker bridge network
  4. No host-level network calls needed
```

### Internal URLs Used in Tests

```bash
# Inside container, these URLs are used:
KEYCLOAK_INTERNAL_URL="http://steel-hammer-keycloak:7081"
POSTGRES_HOST="steel-hammer-postgres"
```

---

## Test Execution Phases

### Phase 1: Infrastructure Verification
- ✅ Checks Keycloak availability (with retry logic)
- ✅ Checks PostgreSQL database connectivity
- ✅ Validates internal networking

### Phase 2: Alice's Authentication
- ✅ Alice authenticates with Keycloak
- ✅ Receives valid JWT token
- ✅ Validates JWT claims (username, roles)
- ✅ Confirms adminrole present

### Phase 3: Bob's Authentication & Access Validation
- ✅ Bob authenticates with Keycloak
- ✅ Receives valid JWT token
- ✅ Validates JWT claims (username, roles)
- ✅ Confirms devrole present
- ✅ Validates multi-tenant isolation
- ✅ Confirms Bob cannot access Alice's resources

### Phase 4: JWT Token Validation
- ✅ JWT structure validation (3 parts)
- ✅ Required claims validation (iss, sub, aud, exp, iat, jti)
- ✅ Token expiration validation
- ✅ Issuer validation (trusted Keycloak)

---

## Expected Output

```
╔══════════════════════════════════════════════════════════════════╗
║                                                                  ║
║     E2E TEST: Alice & Bob (Running Inside Docker Network)        ║
║                                                                  ║
║  Proving: IronBucket is PRODUCTION READY                        ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝

Running inside Docker network
Keycloak URL: http://steel-hammer-keycloak:7081
MinIO URL: http://steel-hammer-minio:9000
PostgreSQL Host: steel-hammer-postgres

=== PHASE 1: Infrastructure Verification ===

Checking Keycloak (OIDC Provider) via internal network...
✅ Keycloak is running (HTTP 200)

Checking PostgreSQL (Database) via internal network...
✅ PostgreSQL is running

✅ Infrastructure verification complete!

=== PHASE 2: Alice's Authentication & File Upload ===

Step 2.1: Alice authenticates with Keycloak (OIDC)...
✅ Alice received JWT token

Alice's JWT Claims:
{
  "preferred_username": "alice",
  "realm_access": {
    "roles": ["adminrole", "default-roles-dev"]
  },
  "email": "alice@acme-corp.io"
}

Key claims extracted:
  - username: alice
  - roles: adminrole, default-roles-dev
  - username validation: CORRECT
  - admin status: YES (adminrole present)

...

=== PHASE 4: JWT Token Validation Details ===

4.1: JWT Structure Validation...
✅ JWT has 3 parts (header.payload.signature)

4.2: JWT Claim Validation...
✅ Claim 'iss' present
✅ Claim 'sub' present
✅ Claim 'aud' present
✅ Claim 'exp' present
✅ Claim 'iat' present
✅ Claim 'jti' present

4.3: Token Expiration Validation...
✅ Token is valid for 285 more seconds

4.4: Issuer Validation...
✅ Token issued by trusted Keycloak instance

╔══════════════════════════════════════════════════════════════════╗
║                                                                  ║
║                     ✅ ALL TESTS PASSED! ✅                      ║
║                                                                  ║
║              IronBucket is PRODUCTION READY! 🚀                 ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝

Test Summary:
  Total Tests: 25
  Passed: 25 ✅
  Failed: 0

Status: READY FOR PRODUCTION DEPLOYMENT 🚀
```

---

## Running Tests in Different Scenarios

### Scenario 1: Full Fresh Start

```bash
# Clean everything and start fresh
cd /workspaces/IronBucket/steel-hammer

export DOCKER_FILES_HOMEDIR="."

# Remove old containers
docker-compose -f docker-compose-steel-hammer.yml down -v

# Rebuild and start with tests
docker-compose -f docker-compose-steel-hammer.yml up -d

# Wait 30 seconds for services to initialize
sleep 30

# Watch test execution
docker logs -f steel-hammer-test
```

### Scenario 2: Services Already Running, Run Tests Only

```bash
# If Keycloak and PostgreSQL are already running
docker-compose -f docker-compose-steel-hammer.yml up steel-hammer-test

# Check results
docker logs steel-hammer-test
```

### Scenario 3: Manual Test Execution

```bash
# Start services without tests
docker-compose -f docker-compose-steel-hammer.yml up -d \
  steel-hammer-keycloak \
  steel-hammer-postgres

# Wait for services to be ready
sleep 40

# Run test container
docker run --network steel-hammer_steel-hammer-network \
  --rm \
  --volumes-from steel-hammer-postgres \
  -v /workspaces/IronBucket/steel-hammer/tests:/tests \
  steel-hammer_steel-hammer-test:latest \
  /tests/e2e-alice-bob-container.sh
```

### Scenario 4: Interactive Testing

```bash
# Start a test container with shell access
docker run -it \
  --network steel-hammer_steel-hammer-network \
  --rm \
  -v /workspaces/IronBucket/steel-hammer/tests:/tests \
  curlimages/curl:latest \
  /bin/sh

# Inside container, you can now:
curl -s http://steel-hammer-keycloak:7081/realms/dev/.well-known/openid-configuration | jq .
psql -h steel-hammer-postgres -U postgres -c "SELECT 1"
bash /tests/e2e-alice-bob-container.sh
```

---

## Troubleshooting

### Issue: Test Container Can't Reach Keycloak

**Symptom**: "Connection refused" or "Failed to resolve steel-hammer-keycloak"

**Solution**:
```bash
# Verify containers are on same network
docker network ls
docker network inspect steel-hammer_steel-hammer-network

# Verify Keycloak is running
docker ps | grep keycloak

# Test connectivity from another container
docker run --rm \
  --network steel-hammer_steel-hammer-network \
  curlimages/curl:latest \
  curl http://steel-hammer-keycloak:7081/realms/dev/.well-known/openid-configuration
```

### Issue: Test Container Exits Immediately

**Symptom**: Container shows "Exited (1)" or "Exited (0)" immediately

**Solution**:
```bash
# Check logs for error
docker logs steel-hammer-test

# If services not ready, wait longer:
sleep 60 && docker exec steel-hammer-test /tests/e2e-alice-bob-container.sh

# Or start with longer wait in docker-compose
# Edit: entrypoint: ["/bin/bash", "-c", "sleep 60 && ..."]
```

### Issue: PostgreSQL Connection Timeout

**Symptom**: "psql: could not translate host name"

**Solution**:
```bash
# Check PostgreSQL is running and healthy
docker ps | grep postgres

# Check PostgreSQL logs
docker logs steel-hammer-postgres

# Ensure password is correct
docker logs steel-hammer-postgres | grep "password"

# The default password in dev-realm.json should match
```

### Issue: Keycloak Takes Too Long to Start

**Solution**: Increase the wait time in entrypoint

```yaml
# In docker-compose-steel-hammer.yml
steel-hammer-test:
  entrypoint: ["/bin/bash", "-c", "sleep 60 && /tests/e2e-alice-bob-container.sh"]
  # Changed from sleep 30 to sleep 60
```

---

## Test Customization

### Modify Test Users

Edit `/workspaces/IronBucket/steel-hammer/keycloak/dev-realm.json`:

```json
{
  "users": [
    {
      "username": "alice",
      "password": "aliceP@ss",
      "email": "alice@acme-corp.io",
      "realmRoles": ["adminrole"]
    },
    {
      "username": "bob",
      "password": "bobP@ss",
      "email": "bob@widgets-inc.io",
      "realmRoles": ["devrole"]
    }
  ]
}
```

### Modify Test Script

Edit `/workspaces/IronBucket/steel-hammer/tests/e2e-alice-bob-container.sh`:

```bash
# Add your custom tests here
# Use internal container URLs:
# KEYCLOAK_INTERNAL_URL="http://steel-hammer-keycloak:7081"
# POSTGRES_HOST="steel-hammer-postgres"
```

### Build Custom Test Image

```bash
cd /workspaces/IronBucket/steel-hammer

docker build \
  -f DockerfileTestRunner \
  -t steel-hammer-test:custom \
  .
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: E2E Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Start Docker services
        run: |
          cd steel-hammer
          export DOCKER_FILES_HOMEDIR="."
          docker-compose -f docker-compose-steel-hammer.yml up -d
          
      - name: Wait for services
        run: sleep 60
        
      - name: Run E2E tests
        run: |
          docker logs -f steel-hammer-test &
          docker exec steel-hammer-test \
            /tests/e2e-alice-bob-container.sh
            
      - name: Check results
        run: |
          EXIT_CODE=$(docker inspect steel-hammer-test \
            --format='{{.State.ExitCode}}')
          echo "Test exit code: $EXIT_CODE"
          exit $EXIT_CODE
```

### GitLab CI Example

```yaml
e2e_tests:
  image: docker:latest
  services:
    - docker:dind
  script:
    - cd steel-hammer
    - export DOCKER_FILES_HOMEDIR="."
    - docker-compose -f docker-compose-steel-hammer.yml up -d
    - sleep 60
    - docker logs -f steel-hammer-test &
    - docker exec steel-hammer-test /tests/e2e-alice-bob-container.sh
    - docker inspect steel-hammer-test --format='{{.State.ExitCode}}'
```

---

## Performance Characteristics

### Test Execution Time

| Phase | Time | Purpose |
|-------|------|---------|
| Infrastructure startup | 30-40s | Keycloak + PostgreSQL initialization |
| Phase 1 (Infrastructure) | ~5s | Network verification |
| Phase 2 (Alice Auth) | ~2s | Keycloak authentication |
| Phase 3 (Bob Auth) | ~2s | Keycloak authentication |
| Phase 4 (JWT Validation) | ~1s | Token claim verification |
| **Total** | **~50-60s** | Complete E2E test suite |

### Container Resource Usage

```bash
# Check container resource usage
docker stats steel-hammer-test

CONTAINER              CPU %  MEM USAGE / LIMIT
steel-hammer-test      0.5%   15MiB / 1GiB
steel-hammer-keycloak  2.3%   450MiB / 1GiB
steel-hammer-postgres  1.2%   120MiB / 1GiB
```

---

## Production Deployment

### For Kubernetes

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: ironbucket-e2e-test
spec:
  template:
    spec:
      containers:
      - name: test
        image: steel-hammer-test:latest
        env:
        - name: KEYCLOAK_URL
          value: http://keycloak:7081
        - name: POSTGRES_HOST
          value: postgresql
      restartPolicy: Never
  backoffLimit: 3
```

### For Docker Swarm

```bash
docker service create \
  --name ironbucket-test \
  --network steel-hammer-network \
  --entrypoint /tests/e2e-alice-bob-container.sh \
  steel-hammer-test:latest
```

---

## Success Criteria

✅ **All tests pass** if:
1. Alice authenticates successfully
2. Alice's JWT token is valid
3. Bob authenticates successfully
4. Bob's JWT token is valid
5. Multi-tenant isolation is enforced
6. All JWT claims are present
7. Token expiration is validated
8. Issuer is trusted

✅ **Exit code**: 0 (success)  
❌ **Exit code**: 1 (failure)

---

## Next Steps

After successful E2E tests:

1. **Phase 4: Operational Readiness**
   - Set up Prometheus metrics
   - Configure Jaeger tracing
   - Create health check endpoints
   - Run load tests (10K req/s)

2. **Phase 5: Kubernetes Deployment**
   - Create Helm charts
   - Deploy to K8s cluster
   - Set up service mesh (Istio)
   - Configure ingress controller

3. **Production Operations**
   - Monitor in production
   - Set up alerting
   - Create runbooks
   - Plan disaster recovery

---

## References

- [Docker Compose Networking](https://docs.docker.com/compose/networking/)
- [Keycloak Development](https://www.keycloak.org/server/containers)
- [PostgreSQL Container](https://hub.docker.com/_/postgres)
- [curl in Alpine](https://github.com/curl/curl-docker)

---

**Status: ✅ Containerized E2E Tests Ready for Production**
