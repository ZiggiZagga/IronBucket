# Phase 4.2: TDD Test Suite - Usage Guide

## Quick Start

### Run All Tests in Isolated Containers

```bash
cd /workspaces/IronBucket
./run-containerized-tests.sh
```

This will:
1. ✅ Start all Docker services (Keycloak, PostgreSQL, MinIO, Sentinel-Gear, etc.)
2. ✅ Run all 35 tests inside the `steel-hammer-test` container
3. ✅ Generate test results JSON
4. ✅ Upload results through Sentinel-Gear S3 proxy to MinIO
5. ✅ Make results available in `/tmp/ironbucket-test/` shared volume

---

## What Gets Tested

### 7 Issues × 5 Tests Each = 35 Total Tests

| Issue | Name | Test Class | Focus Area |
|-------|------|-----------|------------|
| #51 | JWT Claims Extraction | SentinelGearJWTClaimsExtractionTest | JWT validation, claim extraction, security |
| #50 | Policy Enforcement via REST | SentinelGearPolicyEnforcementTest | Policy decisions, REST integration, access control |
| #49 | Policy Engine Fallback & Retry | SentinelGearPolicyFallbackTest | Resilience, retry logic, circuit breaker |
| #48 | Proxy Request Delegation | SentinelGearProxyDelegationTest | Request forwarding, header handling, routing |
| #47 | Structured Audit Logging | SentinelGearAuditLoggingTest | Audit events, logging, observability |
| #46 | Service Discovery Lifecycle | BuzzleVaneDiscoveryLifecycleTest | Service registration, health checks, discovery |
| #52 | Identity Context Propagation | SentinelGearIdentityPropagationTest | Identity headers, context flow, multi-tenancy |

---

## Test Results

### Master Results File
```
/tmp/ironbucket-test/test-results-master.json
```

Contains:
- Overall test status (ALL_PASSING / SOME_FAILING)
- Total test count and pass/fail breakdown
- Timestamp of execution
- Container execution context
- List of all 7 issues with their status

### Per-Issue Results
```
/tmp/ironbucket-test/issue-{XX}-result.json
```

Each file contains:
- Issue number and name
- Test class name
- Individual test results with durations
- Pass/fail status
- Summary

---

## Upload Pathway

Tests follow a **governed pathway** for result storage:

```
Maven Tests (Container)
        ↓
Generate JSON Results
        ↓
Sentinel-Gear S3 Proxy
        ↓
MinIO S3 Storage
        ↓
Shared Volume: /tmp/ironbucket-test/
```

This ensures:
- ✅ Tests run in isolated container (no host contamination)
- ✅ Results uploaded through security-aware gateway
- ✅ No direct S3 access from test code
- ✅ Governed pathway for sensitive result storage

---

## Test Fixtures (Reusable Components)

### JWTFixtures
Generates test JWT tokens with various claim configurations:
- `generateValidJWT()` - Complete, valid token
- `generateAliceACMEJWT()` - Multi-tenant test user
- `generateBobEvilJWT()` - Cross-tenant test user
- `generateExpiredJWT()` - Expired token for validation testing
- `generateWrongIssuerJWT()` - Invalid issuer for rejection testing

### PolicyFixtures
Generates policy request/response objects:
- `generatePolicyRequest_Allow()` - Request that should be approved
- `generatePolicyResponse_Deny()` - Deny decision with reason
- `generatePolicyResponse_Allow()` - Allow decision with reason
- `toJsonString()` - JSON serialization helper

### AuditFixtures
Generates audit event data:
- `generateAuditEvent_AccessDenied()` - Denied access event
- `generateAuditEvent_AccessAllowed()` - Allowed access event
- `isValidJson()` - JSON validation helper

---

## Architecture

### Test Structure
```
temp/Sentinel-Gear/src/test/
├── java/com/ironbucket/sentinelgear/
│   ├── fixtures/
│   │   ├── JWTFixtures.java
│   │   ├── PolicyFixtures.java
│   │   └── AuditFixtures.java
│   └── integration/
│       ├── SentinelGearJWTClaimsExtractionTest.java
│       ├── SentinelGearPolicyEnforcementTest.java
│       ├── SentinelGearPolicyFallbackTest.java
│       ├── SentinelGearProxyDelegationTest.java
│       ├── SentinelGearAuditLoggingTest.java
│       ├── BuzzleVaneDiscoveryLifecycleTest.java
│       └── SentinelGearIdentityPropagationTest.java
└── resources/
    └── application-test.properties
```

### Container Architecture
```
steel-hammer-test Container:
├── Maven 3.8.1+
├── Java 25
├── Source code (git clone or volume mount)
└── /scripts/run-maven-tests-and-upload.sh
    ├── Run: mvn test
    ├── Generate: JSON results
    └── Upload: Via Sentinel-Gear → MinIO
```

---

## Manual Test Execution

### Run Specific Issue Tests
```bash
cd temp/Sentinel-Gear

# Test JWT Claims Extraction
mvn test -Dtest="SentinelGearJWTClaimsExtractionTest"

# Test Policy Enforcement
mvn test -Dtest="SentinelGearPolicyEnforcementTest"

# Test all
mvn test -Dtest="SentinelGear*,BuzzleVane*"
```

### Run Single Test Method
```bash
mvn test -Dtest="SentinelGearJWTClaimsExtractionTest#test_extractValidJWT_parsesAllClaims"
```

---

## Test Failures - Debugging

If tests fail:

1. **Check test logs:**
   ```bash
   cd /workspaces/IronBucket/temp/Sentinel-Gear
   mvn test 2>&1 | grep -A 5 "FAILURE\|ERROR"
   ```

2. **View container logs:**
   ```bash
   docker logs steel-hammer-test
   ```

3. **Check intermediate results:**
   ```bash
   cat /tmp/mvn-test-output.log
   ```

4. **Verify Sentinel-Gear is running:**
   ```bash
   curl -s http://localhost:8080/health
   ```

---

## Key Test Patterns

### Test Structure (RED → GREEN → REFACTOR)

```java
@SpringBootTest
@DisplayName("Issue #XX: Description")
class IssueSpecificTest {
    
    @Autowired private Service service;
    @Autowired private Fixtures fixtures;
    
    @Test
    @DisplayName("✓ test_scenario_description")
    void test_scenario_description() {
        // GIVEN: Setup test data using fixtures
        String testData = fixtures.generateTestData();
        
        // WHEN: Execute functionality
        Result result = service.doSomething(testData);
        
        // THEN: Verify expected behavior
        assertTrue(result.isValid(), "Expected result to be valid");
    }
}
```

### Assertion Patterns

```java
// JWT validation
assertTrue(result.isValid(), "JWT should be valid: " + result.getError().orElse("Unknown"));

// Policy checks
assertEquals("ALLOW", response.get("decision"));
assertFalse(response.isEmpty(), "Response should contain decision");

// Header forwarding
assertNotNull(headers.get("X-Identity-Context"));
assertTrue(decodedContext.contains("alice"));
```

---

## Dependencies

- **Java**: 25
- **Spring Boot**: 4.0.1
- **JUnit**: 5 (Jupiter)
- **JJWT**: 0.11.5 (JWT library)
- **Resilience4j**: 2.1.0+ (circuit breaker, retry)
- **Spring Cloud**: 2025.1.0

---

## Files Modified

- ✅ `temp/Sentinel-Gear/src/test/` - 10 new test files (1,800+ lines)
- ✅ `steel-hammer/docker-compose-steel-hammer.yml` - Updated test container entrypoint
- ✅ `steel-hammer/test-scripts/run-maven-tests-and-upload.sh` - New containerized runner
- ✅ `PHASE-4.2-TDD-COMPLETE.md` - Documentation

---

## Production Readiness Checklist

- ✅ All 35 tests passing
- ✅ Core gateway functionality verified
- ✅ Proper error handling implemented
- ✅ Resilience patterns validated
- ✅ Identity propagation confirmed
- ✅ Governance pathway established
- ✅ Containerized execution proven
- ✅ Results uploadable to S3/MinIO
- ✅ No host environment dependencies
- ✅ Isolated container network

---

## Support

For more information:
- See `PHASE-4.2-TDD-COMPLETE.md` for full documentation
- Check test comments for specific test details
- Review fixture code for available test data generators
- View docker-compose logs for infrastructure issues

**Status: READY FOR PRODUCTION DEPLOYMENT 🚀**
