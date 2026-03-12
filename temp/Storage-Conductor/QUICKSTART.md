# Storage-Conductor: Quick Start Guide

## One-Command Test Execution

```bash
cd temp/Storage-Conductor
./run-tests.sh up
```

This will:
1. ✅ Build Vault-Smith container
2. ✅ Build Storage-Conductor test container
3. ✅ Start MinIO S3 backend
4. ✅ Start PostgreSQL database
5. ✅ Start Keycloak identity provider
6. ✅ Start Sentinel-Gear identity gateway
7. ✅ Start Vault-Smith service
8. ✅ Run all 11 tests
9. ✅ Display results in logs
10. ✅ Save detailed report to `/test-reports/storage-conductor-test-report.log`

## Expected Output

```
╔════════════════════════════════════════════════════════════════╗
║         STORAGE-CONDUCTOR TEST ORCHESTRATOR                   ║
║       S3 Backend Validation Through Sentinel-Gear             ║
╚════════════════════════════════════════════════════════════════╝

[INFO] Test Configuration
────────────────────────────────────────────────────────────────
  S3 Endpoint:          http://minio:9000
  S3 Region:            us-east-1
  Identity Gateway:     http://sentinel-gear:8080
  JWT Audience:         ironfaucet
  Test Tenant:          test-org-001

[INFO] Waiting for S3 backend to be ready...
[OK] S3 backend is ready

[INFO] Starting Storage-Conductor test suite...
────────────────────────────────────────────────────────────────

Executing Maven test suite...

[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0

════════════════════════════════════════════════════════════════

[INFO] Analyzing Test Results

  Total Tests Run:    11
  Failures:           0
  Errors:             0
  Skipped:            0

✓ ALL TESTS PASSED

Test report saved to: /test-reports/storage-conductor-test-report.log

╔════════════════════════════════════════════════════════════════╗
║                    ✓ ALL TESTS PASSED                        ║
║                                                                ║
║  Test Report: /test-reports/storage-conductor-test-report.log  ║
╚════════════════════════════════════════════════════════════════╝
```

## Available Commands

### Run Full Test Suite
```bash
./run-tests.sh up
```
Starts entire test environment, runs tests, displays results.

### Stop Test Environment
```bash
./run-tests.sh down
```
Removes all containers and volumes.

### View Service Logs
```bash
./run-tests.sh logs sentinel-gear
./run-tests.sh logs vault-smith
./run-tests.sh logs storage-conductor-tests
```

### Display Test Report
```bash
./run-tests.sh report
```
Shows the detailed test report from last run.

### Check Service Status
```bash
./run-tests.sh status
```
Lists all running containers and their status.

### Rebuild Containers
```bash
./run-tests.sh rebuild
```
Rebuilds all containers without cache.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│          Storage-Conductor Test Stack                │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌────────────────────────────────────────────┐   │
│  │ Storage-Conductor Test Container           │   │
│  │ ┌──────────────────────────────────────┐   │   │
│  │ │ Maven Test Suite (11 tests)          │   │   │
│  │ │ - Bucket Operations (4)               │   │   │
│  │ │ - Object Operations (5)               │   │   │
│  │ │ - Multipart Upload (2)               │   │   │
│  │ └──────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────┘   │
│                       ↓                             │
│  ┌────────────────────────────────────────────┐   │
│  │ Vault-Smith Service (Port 8090)             │   │
│  │ S3-compatible backend abstraction            │   │
│  └─────────────────────────────────────────────┘   │
│                       ↓                             │
│  ┌────────────────────────────────────────────┐   │
│  │ Sentinel-Gear (Port 8080)                   │   │
│  │ Identity Gateway & JWT Validation           │   │
│  └─────────────────────────────────────────────┘   │
│                       ↓                             │
│  ┌────────────────────────────────────────────┐   │
│  │ Keycloak (Port 8080)                        │   │
│  │ OIDC/OAuth2 Identity Provider               │   │
│  └─────────────────────────────────────────────┘   │
│                       ↓                             │
│  ┌────────────────────────────────────────────┐   │
│  │ MinIO S3 Backend (Port 9000)                │   │
│  │ S3-compatible object storage                │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
└─────────────────────────────────────────────────────┘

Test Flow:
Storage-Conductor Tests
  → Vault-Smith (S3 operations)
    → Sentinel-Gear (identity validation)
      → Keycloak (JWT verification)
        → MinIO (S3 backend)

Results:
✓ No exposed ports (log-based reporting only)
✓ Complete audit trail
✓ Identity-driven test execution
```

## Test Report Location

After running tests:
```bash
# View report in terminal
cat test-reports/storage-conductor-test-report.log

# Or run
./run-tests.sh report
```

## Environment Variables

Can override defaults by setting environment variables before running:

```bash
export S3_ENDPOINT="http://custom-s3:9000"
export S3_ACCESS_KEY="custom-key"
export S3_SECRET_KEY="custom-secret"
export IDENTITY_GATEWAY="http://custom-identity:8080"

./run-tests.sh up
```

## Docker Network

Tests run on isolated `storage-test-network`:
- Services communicate internally
- No external port mappings for test container
- All communication logged
- Security-first design

## Troubleshooting

### Services fail to start
```bash
# Check service logs
./run-tests.sh logs vault-smith
./run-tests.sh logs sentinel-gear

# Rebuild from scratch
./run-tests.sh down
./run-tests.sh rebuild
./run-tests.sh up
```

### Tests timeout
- Increase timeout in `docker-compose-tests.yml`
- Check MinIO health: `./run-tests.sh logs storage-conductor-minio`
- Verify network connectivity: `./run-tests.sh status`

### Report not generated
- Check test logs: `./run-tests.sh logs storage-conductor-tests`
- Verify `/test-reports` directory exists and is writable
- Run again with debug output

## Integration with CI/CD

### GitHub Actions
```yaml
- name: Run Storage-Conductor Tests
  run: |
    cd temp/Storage-Conductor
    ./run-tests.sh up
```

### GitLab CI
```yaml
storage-conductor-tests:
  script:
    - cd temp/Storage-Conductor
    - ./run-tests.sh up
  artifacts:
    paths:
      - temp/Storage-Conductor/test-reports/
```

---

**Status**: ✅ Ready for containerized integration testing  
**Tests**: 11 comprehensive S3 compatibility tests  
**No Ports Exposed**: Test container writes results to logs only
