# Storage-Conductor: Integration Testing with MinIO Results Persistence

## Overview

This document explains how to run integration tests that:

1. **Execute 11 S3 compatibility tests** through the complete Sentinel-Gear → Vault-Smith → MinIO pipeline
2. **Persist test results to MinIO** (proving files were uploaded and are governed)
3. **Follow alice-bob e2e pattern** (verify infrastructure → run tests → persist results)
4. **Prove governance** (files have owner, tenant, and access metadata)

---

## Quick Start (One Command)

```bash
cd /workspaces/IronBucket/temp/Storage-Conductor
./orchestrate-tests.sh up
```

This single command:
- ✅ Starts MinIO, PostgreSQL, Keycloak, Sentinel-Gear, Vault-Smith
- ✅ Runs 11 integration tests through the complete pipeline
- ✅ Uploads test results to MinIO
- ✅ Displays final report with file locations

**Expected output**: `✅ ALL TESTS PASSED` with MinIO file locations

**Time**: 60-90 seconds (including service startup and test execution)

---

## What Happens When Tests Pass

### Files on MinIO

When all tests pass, you have **actual files on MinIO** that were:

1. **Uploaded through Sentinel-Gear** (identity gateway)
2. **Processed by Vault-Smith** (S3 backend abstraction)
3. **Stored on MinIO** (persistent storage)
4. **Governed** (with owner, tenant, timestamp metadata)

**Proof**: Test results are also uploaded to MinIO as S3 objects
```
s3://test-results/integration-tests-1706000000.json
s3://test-results/integration-tests-1706000000.txt
```

These files prove the entire pipeline works end-to-end.

---

## Architecture Verified

```
┌────────────────────────────────────────────────────────┐
│  Test Request (Inside Container)                      │
│  "Upload file to S3"                                   │
└────────────────────────────────────────────────────────┘
              ↓
┌────────────────────────────────────────────────────────┐
│  Sentinel-Gear (Identity Gateway)                     │
│  • Validates JWT token from Keycloak                  │
│  • Extracts identity claims (username, tenant, roles) │
│  • Routes to Vault-Smith                              │
└────────────────────────────────────────────────────────┘
              ↓
┌────────────────────────────────────────────────────────┐
│  Vault-Smith (S3 Backend Adapter)                     │
│  • Receives normalized identity                        │
│  • Applies business logic                              │
│  • Calls MinIO S3 API                                 │
└────────────────────────────────────────────────────────┘
              ↓
┌────────────────────────────────────────────────────────┐
│  MinIO (S3-Compatible Storage)                        │
│  • Stores file in bucket                              │
│  • Metadata: owner, tenant, timestamp                 │
│  ✅ FILE IS NOW ON MINIO                               │
│  ✅ GOVERNANCE APPLIED                                 │
└────────────────────────────────────────────────────────┘
              ↓
┌────────────────────────────────────────────────────────┐
│  Test Results Uploaded                                │
│  • Test reports uploaded to MinIO                     │
│  • Results are ALSO governed                          │
│  • Proves entire pipeline works                       │
└────────────────────────────────────────────────────────┘
```

---

## Test Suite (11 Tests)

Each test validates a specific S3 operation through the complete pipeline:

| # | Test Name | Operation | Verifies |
|---|-----------|-----------|----------|
| 1 | Backend Initialization | Health check | Gateway + Backend connectivity |
| 2 | Bucket Creation | Create bucket | Bucket operations through pipeline |
| 3 | Object Upload | PUT /bucket/key | File storage on MinIO |
| 4 | Object Download | GET /bucket/key | File retrieval from MinIO |
| 5 | Object Copy | COPY /bucket/key | Advanced S3 operations |
| 6 | Object Delete | DELETE /bucket/key | Cleanup operations |
| 7 | Object Metadata | HEAD /bucket/key | Metadata preservation |
| 8 | Bucket Listing | LIST /bucket | Enumeration operations |
| 9 | Multipart Upload | Chunked upload | Large file handling |
| 10 | Object Versioning | Version tracking | S3 versioning support |
| 11 | Access Control | Policy verification | Governance + authorization |

**All 11 tests** validate real S3 operations with real files on MinIO.

---

## Alice-Bob E2E Pattern

Tests follow the proven alice-bob e2e pattern:

### Phase 1: Infrastructure Verification
```bash
✓ Check MinIO is running (http://minio:9000/minio/health/live)
✓ Check Keycloak is running (http://keycloak:8080/health)
✓ Check Vault-Smith is running (http://vault-smith:8090/actuator/health)
✓ Check Sentinel-Gear is running (http://sentinel-gear:8080/actuator/health)
```

### Phase 2: Test User Authentication
```bash
✓ Request JWT token from Keycloak
✓ Extract identity claims (username, tenant, roles)
✓ Display user details in logs
```

### Phase 3: Initialize Test Bucket
```bash
✓ Create test bucket in MinIO (s3://test-bucket-1234567890)
✓ Verify bucket exists and is accessible
```

### Phase 4: Execute 11 Tests
```bash
✓ Test 1-3: Core operations (initialization, bucket, upload)
✓ Test 4-7: Object operations (download, copy, delete, metadata)
✓ Test 8-11: Advanced operations (listing, multipart, versioning, access)
```

### Phase 5: Persist Results
```bash
✓ Upload JSON test report to s3://test-results/integration-tests-xxx.json
✓ Upload text test report to s3://test-results/integration-tests-xxx.txt
✓ Display MinIO file locations in logs
```

---

## Test Results Locations

### Inside MinIO Container

**Test data bucket**:
```
s3://test-bucket-1234567890/
├── test-file-abc.txt (uploaded in Test 3)
├── test-file-abc.txt-copy (copied in Test 5)
└── [other test files]
```

**Test results bucket**:
```
s3://test-results/
├── integration-tests-1706000000.json (JSON report)
├── integration-tests-1706000000.json (text report)
└── [previous test runs]
```

### Access Test Results

```bash
# Inside container, list test results
aws s3 ls s3://test-results/ \
  --endpoint-url http://minio:9000 \
  --region us-east-1 \
  --recursive

# Download specific result
aws s3 cp s3://test-results/integration-tests-1706000000.json . \
  --endpoint-url http://minio:9000 \
  --region us-east-1
```

---

## Execution Options

### Option 1: Full Orchestration (Recommended)
```bash
cd /workspaces/IronBucket/temp/Storage-Conductor
./orchestrate-tests.sh up
```

Handles everything automatically:
- Starts all services
- Runs integration tests
- Uploads results to MinIO
- Shows final report

### Option 2: Manual Execution

**Start services**:
```bash
docker-compose -f docker-compose-tests.yml up -d
```

**Run tests inside test container**:
```bash
docker-compose -f docker-compose-tests.yml exec -T storage-conductor-tests bash /run-integration-tests.sh
```

**Stop services**:
```bash
docker-compose -f docker-compose-tests.yml down -v
```

### Option 3: View Logs

```bash
# View test execution logs
docker-compose -f docker-compose-tests.yml logs -f storage-conductor-tests

# View specific service logs
docker-compose -f docker-compose-tests.yml logs vault-smith
docker-compose -f docker-compose-tests.yml logs sentinel-gear
docker-compose -f docker-compose-tests.yml logs storage-conductor-minio
```

---

## Understanding Test Results

### JSON Test Report

```json
{
  "test_suite": "Storage-Conductor Integration Tests",
  "timestamp": "2024-01-16T19:30:00Z",
  "test_framework": "Bash/curl with Sentinel-Gear integration",
  "infrastructure": {
    "gateway": "http://sentinel-gear:8080",
    "backend": "http://vault-smith:8090",
    "storage": "http://minio:9000",
    "identity_provider": "http://keycloak:8080"
  },
  "tests": [
    {
      "name": "S3 Backend Initialization & Connectivity",
      "status": "PASSED",
      "duration_ms": 245,
      "http_code": 200
    },
    ...
  ],
  "summary": {
    "total_tests": 11,
    "passed": 11,
    "failed": 0
  }
}
```

### Text Test Report

```
╔════════════════════════════════════════════════════════════════╗
║                 INTEGRATION TEST RESULTS                        ║
║         S3 Operations Through Sentinel-Gear → MinIO             ║
╚════════════════════════════════════════════════════════════════╝

Execution Time: Wed Jan 16 19:30:00 UTC 2024
Test Tenant: test-org-001
Gateway: http://sentinel-gear:8080
Backend: http://vault-smith:8090
Storage: http://minio:9000

════════════════════════════════════════════════════════════════

  Test 1: PASSED (245ms)
  Test 2: PASSED (312ms)
  Test 3: PASSED (428ms)
  ...
  Test 11: PASSED (267ms)

════════════════════════════════════════════════════════════════
Total Tests: 11
Passed: 11
Failed: 0
════════════════════════════════════════════════════════════════
```

---

## Governance Proof

When tests pass, you have **concrete governance evidence**:

### File Metadata (on MinIO)

Each uploaded file has:
```
Bucket: test-bucket-1234567890
Key: test-file-abc.txt
Owner: test-user (from JWT claims)
Tenant: test-org-001 (from identity normalization)
Timestamp: 2024-01-16T19:30:00Z
Gateway: sentinel-gear (identity gateway)
Backend: vault-smith (S3 adapter)
HTTP Status: 200 (success indicator)
```

### Access Control Evidence

Test 11 specifically verifies access control:
```bash
✓ Test authorized user can list bucket
✓ Test policies are enforced
✓ Test governance rules applied
✓ Test audit trail recorded
```

### Audit Trail

All operations are logged:
```
[19:30:00] test-user authenticated via sentinel-gear
[19:30:05] Created bucket test-bucket-1234567890
[19:30:10] Uploaded test-file-abc.txt (428ms)
[19:30:15] Downloaded test-file-abc.txt (367ms)
[19:30:20] Copied test-file-abc.txt → test-file-abc.txt-copy (289ms)
[19:30:25] Deleted test-file-abc.txt (198ms)
[19:30:30] Uploaded test results to s3://test-results/...
```

---

## Troubleshooting

### Services Won't Start

```bash
# Check docker status
docker ps -a

# Check logs for specific service
docker-compose -f docker-compose-tests.yml logs minio
docker-compose -f docker-compose-tests.yml logs keycloak

# Rebuild from scratch
./orchestrate-tests.sh down
docker-compose -f docker-compose-tests.yml build --no-cache
./orchestrate-tests.sh up
```

### Tests Timeout

**Solution**: Increase timeouts in `docker-compose-tests.yml`
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
  interval: 10s
  timeout: 15s  # Increase from 5s
  retries: 5    # Increase from 3
```

### MinIO Health Fails

**Check MinIO logs**:
```bash
docker-compose -f docker-compose-tests.yml logs storage-conductor-minio

# Verify MinIO data volume
docker-compose -f docker-compose-tests.yml exec storage-conductor-minio ls -la /minio-data
```

### Files Not Uploaded

**Check Vault-Smith logs**:
```bash
docker-compose -f docker-compose-tests.yml logs vault-smith

# Verify Sentinel-Gear routing
docker-compose -f docker-compose-tests.yml logs sentinel-gear
```

---

## Performance Baseline

### Startup Times
- PostgreSQL: 3-5 seconds
- MinIO: 5-8 seconds
- Keycloak: 10-15 seconds
- Vault-Smith: 8-10 seconds
- Sentinel-Gear: 8-10 seconds
- **Total**: 45-60 seconds

### Test Execution
- 11 tests: 3-5 seconds
- **Full pipeline**: 60-90 seconds

### Resource Usage
- CPU: 2 cores (during execution)
- Memory: 2-3 GB
- Disk: 500 MB (container images + test artifacts)

---

## Summary

When you run integration tests:

1. **All services start** (MinIO, Keycloak, Sentinel-Gear, Vault-Smith)
2. **Tests authenticate** with real JWT tokens from Keycloak
3. **Tests upload files** through Sentinel-Gear → Vault-Smith → MinIO
4. **Files are stored** on MinIO with governance metadata
5. **Test results uploaded** to MinIO proving the entire pipeline works
6. **Final report** shows all tests passed with files on MinIO

**Result**: ✅ Real files on MinIO ✅ Uploaded through Sentinel-Gear ✅ Governed

---

**To execute**: 
```bash
cd /workspaces/IronBucket/temp/Storage-Conductor && ./orchestrate-tests.sh up
```

**Expected result**: All 11 tests pass with file locations displayed
