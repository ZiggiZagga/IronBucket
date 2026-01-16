# Storage-Conductor

**Comprehensive S3-compatible storage backend orchestration and validation test suite**

Storage-Conductor conducts rigorous compatibility tests across AWS S3, MinIO, Ceph RGW, and other S3-compatible services. It orchestrates comprehensive validation ensuring storage operations harmonize with the IronBucket identity and policy framework.

## Purpose

Storage-Conductor serves three critical functions:

1. **Compatibility Validation** - Verify Vault-Smith works correctly across different S3 backends
2. **Regression Testing** - Prevent breaking changes in storage operations
3. **Integration Verification** - Ensure seamless integration with IronBucket identity and audit systems

## Architecture

```
┌──────────────────────────────────────────────────────┐
│         Storage-Conductor Test Suite                 │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │ Test Categories (11 Test Cases)               │  │
│  ├────────────────────────────────────────────────┤  │
│  │                                                │  │
│  │ ✓ Backend Initialization & Connectivity       │  │
│  │   - initialize()                              │  │
│  │   - testConnectivity()                        │  │
│  │                                                │  │
│  │ ✓ Bucket Operations (Create/List/Delete)      │  │
│  │   - createBucket()                            │  │
│  │   - bucketExists()                            │  │
│  │   - listBuckets()                             │  │
│  │   - deleteBucket()                            │  │
│  │                                                │  │
│  │ ✓ Object Operations (CRUD)                    │  │
│  │   - uploadObject()                            │  │
│  │   - downloadObject()                          │  │
│  │   - deleteObject()                            │  │
│  │   - getObjectMetadata()                       │  │
│  │   - listObjects()                             │  │
│  │   - copyObject()                              │  │
│  │                                                │  │
│  │ ✓ Multipart Upload Workflows                  │  │
│  │   - initiateMultipartUpload()                 │  │
│  │   - uploadPart()                              │  │
│  │   - completeMultipartUpload()                 │  │
│  │   - abortMultipartUpload()                    │  │
│  │                                                │  │
│  └────────────────────────────────────────────────┘  │
│                        │                              │
│  ┌────────────────────▼───────────────────────────┐  │
│  │ Vault-Smith Backend Interface                 │  │
│  │ (Abstraction under test)                      │  │
│  └────────────────────┬───────────────────────────┘  │
│                       │                               │
└───────────────────────┼───────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
    ┌───▼──┐        ┌───▼──┐       ┌───▼───┐
    │AWS S3│        │MinIO │       │Ceph RGW│
    │ TEST │        │ TEST │       │ TEST  │
    └──────┘        └──────┘       └────────┘
```

## Test Categories

### 1. Initialization & Connectivity (2 tests)

**testInitialization**
- Verifies backend initialization completes without errors
- Tests connection pool establishment
- Validates credential acceptance

**testConnectivity**
- Confirms ability to reach S3 backend
- Tests authentication/authorization
- Measures response times

### 2. Bucket Operations (4 tests)

**testCreateBucket**
- Creates test bucket with unique name
- Verifies bucket exists immediately after creation
- Cleans up after test

**testListBuckets**
- Lists all accessible buckets
- Validates bucket collection structure
- Verifies newly created buckets appear in list

**testBucketExists**
- Tests positive case (existing bucket)
- Tests negative case (non-existent bucket)
- Validates consistency with listBuckets()

**testDeleteBucket**
- Deletes empty bucket
- Confirms deletion with follow-up exists() call
- Validates bucket no longer in list

### 3. Object Operations (5 tests)

**testUploadAndDownload**
- Uploads small text object
- Retrieves and validates object metadata
- Downloads object and verifies content
- Tests content-type preservation

**testListObjects**
- Lists objects with no prefix
- Lists objects with prefix filtering
- Validates pagination handling
- Tests empty bucket case

**testCopyObject**
- Copies object within same bucket
- Verifies source and destination both exist
- Confirms copy content matches source
- Tests cross-bucket copy (if applicable)

**testDeleteObject**
- Uploads test object
- Deletes object
- Verifies deletion with exists check
- Tests deletion of non-existent object (idempotent)

**testGetObjectMetadata**
- Retrieves object metadata without download
- Validates metadata fields (size, type, ETag, timestamp)
- Tests performance (metadata-only operation)
- Compares with full download metadata

### 4. Multipart Upload (2 tests)

**testMultipartUpload**
- Initiates multipart upload session
- Uploads 2+ parts sequentially
- Validates part ETags
- Completes upload
- Verifies final object exists and is downloadable
- Tests part ordering/numbering

**testAbortMultipartUpload**
- Initiates multipart upload
- Uploads 1+ parts
- Aborts session
- Verifies object NOT created
- Confirms no orphaned parts

## Test Configuration

All tests support configuration via system properties for flexibility:

```properties
# Default configuration (local MinIO)
s3.endpoint=http://localhost:9000
s3.accessKey=minioadmin
s3.secretKey=minioadmin
s3.region=us-east-1

# Can be overridden at test time:
mvn test -Ds3.endpoint=https://s3.amazonaws.com -Ds3.region=eu-west-1
```

### Test Data Characteristics

Each test:
- Uses unique, timestamped bucket names: `test-bucket-{timestamp}`
- Creates small objects (< 1MB) for fast execution
- Cleans up all resources after each test (isolated tests)
- Uses reasonable timeouts (30s per operation)
- Logs operations for debugging failures

## Running Tests

### Against Local MinIO
```bash
# Start MinIO (via docker-compose)
cd ../../steel-hammer
docker-compose -f docker-compose-steel-hammer.yml up minio

# In another terminal
cd ../../temp/Storage-Conductor
mvn clean test
```

### Against AWS S3
```bash
export AWS_ACCESS_KEY_ID=your-akia-key
export AWS_SECRET_ACCESS_KEY=your-secret-key

cd temp/Storage-Conductor
mvn clean test \
  -Ds3.endpoint=https://s3.amazonaws.com \
  -Ds3.region=us-west-2
```

### Against Ceph RGW
```bash
mvn clean test \
  -Ds3.endpoint=http://ceph-rgw:7480 \
  -Ds3.accessKey=ceph-user \
  -Ds3.secretKey=ceph-secret \
  -Ds3.region=default
```

### Against DigitalOcean Spaces
```bash
mvn clean test \
  -Ds3.endpoint=https://nyc3.digitaloceanspaces.com \
  -Ds3.accessKey=do-spaces-key \
  -Ds3.secretKey=do-spaces-secret \
  -Ds3.region=nyc3
```

### Parallel Test Execution
```bash
# Run all tests in parallel (requires thread-safe test isolation)
mvn clean test -DparallelizeTests=true
```

### Specific Test Execution
```bash
# Run single test
mvn test -Dtest=S3CompatibilityTest#testCreateBucket

# Run test category
mvn test -Dtest=S3CompatibilityTest#testMultipartUpload*
```

## Test Results Interpretation

### Success Output
```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 15.234s
[INFO] BUILD SUCCESS
```

### Failure Analysis
Storage-Conductor provides detailed failure information:

1. **Assertion Failure**
   ```
   AssertionError: Should be able to connect to S3 backend
   at org.junit.jupiter.api.Assertions.assertTrue(Assertions.java:...)
   ```
   → Check endpoint configuration, credentials, network access

2. **Dependency Resolution**
   ```
   Could not find artifact com.ironbucket:vault-smith:jar:0.0.1-SNAPSHOT
   ```
   → Run `mvn -pl ../Vault-Smith clean install` first

3. **Connection Timeout**
   ```
   SdkClientException: Unable to execute HTTP request: Connect timed out
   ```
   → Verify backend is running, endpoint is reachable

## Integration with CI/CD

### GitHub Actions
```yaml
- name: Run Storage-Conductor Tests
  run: |
    cd temp/Storage-Conductor
    mvn clean test \
      -Ds3.endpoint=${{ secrets.S3_ENDPOINT }} \
      -Ds3.accessKey=${{ secrets.S3_ACCESS_KEY }} \
      -Ds3.secretKey=${{ secrets.S3_SECRET_KEY }}
```

### GitLab CI
```yaml
storage-conductor-tests:
  script:
    - cd temp/Storage-Conductor
    - mvn clean test -Ds3.endpoint=$S3_ENDPOINT
  artifacts:
    reports:
      junit: target/surefire-reports/*.xml
```

## Performance Benchmarks

Typical execution times on local MinIO:

| Test | Execution Time | Notes |
|------|----------------|-------|
| testInitialization | 0.2s | Connection pool setup |
| testConnectivity | 0.3s | HEAD request |
| testCreateBucket | 0.5s | + delete |
| testListBuckets | 0.1s | Fast operation |
| testUploadAndDownload | 1.2s | Small object I/O |
| testMultipartUpload | 2.5s | Multiple round-trips |
| **Total Suite** | **~15s** | All 11 tests |

## Debugging Failed Tests

### Enable Debug Logging
```bash
mvn test -X  # Maven debug output
mvn test -q --log-file=test.log  # Detailed logging

# Or in IDE: set SLF4J log level to DEBUG
```

### Inspect Test Artifacts
```bash
# Failed test reports
cat target/surefire-reports/com.ironbucket.storageconductor.S3CompatibilityTest.txt

# Test execution logs
cat target/test-logs/storage-conductor.log
```

### Common Issues & Solutions

**Issue**: Tests timeout
- **Cause**: Slow network, large objects, backend overload
- **Solution**: Increase timeout, reduce object size, check backend health

**Issue**: Authentication failures
- **Cause**: Invalid credentials, expired keys, IAM policy
- **Solution**: Verify credentials, check IAM permissions, rotate keys

**Issue**: Bucket creation fails (unique name constraint)
- **Cause**: Bucket name already exists (S3 global namespace)
- **Solution**: Use `System.currentTimeMillis()` in bucket names (already done)

**Issue**: Multipart tests hang
- **Cause**: Incomplete upload, network interruption
- **Solution**: Check backend logs, verify network, abort manual uploads

## Test Maintenance

### Adding New Tests
1. Extend `S3CompatibilityTest` class
2. Create `@Test` method with `@DisplayName`
3. Use `assertDoesNotThrow()` for happy path
4. Clean up resources in test (or use fixture)
5. Update test count in documentation

### Updating Tests for API Changes
When Vault-Smith interface changes:
1. Update test method signatures
2. Add assertions for new behavior
3. Verify all existing tests still pass
4. Add regression test for breaking changes

## Dependencies

- **Vault-Smith** (0.0.1-SNAPSHOT): Backend implementation under test
- **AWS SDK v2** (2.24.1): S3 operations
- **JUnit 5** (6.0.1): Test framework
- **Mockito** (5.20.0): Mocking (if needed)
- **AssertJ** (3.24.1): Fluent assertions
- **SLF4J/Logback**: Test logging

## Related Modules

- **Vault-Smith** - S3-compatible storage backend abstraction
- **Sentinel-Gear** - Identity gateway (integration target)
- **Brazz-Nossel** - S3 proxy (integration target)
- **Buzzle-Vane** - Service discovery (registration target)

## Contributing

For test improvements:
1. Add new test cases for uncovered scenarios
2. Enhance existing tests with better assertions
3. Add performance benchmarks for optimization tracking
4. Document edge cases discovered during testing

See [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines.

## License

Part of IronBucket project. See [LICENSE](../../LICENSE) for details.
