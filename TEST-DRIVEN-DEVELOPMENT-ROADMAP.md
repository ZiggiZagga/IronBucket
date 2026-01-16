# Test-Driven Development Roadmap: Issue-to-Test Pattern

**Date**: January 16, 2026  
**Goal**: Each GitHub issue → Failing test → Code implementation → Passing test  
**Deliverable**: Test results uploaded to MinIO via Sentinel-Gear  
**Test Framework**: JUnit 5 + TestContainers + Docker Compose

---

## Phase Overview

### Pattern: Red → Green → Refactor

```
Issue #XX (GitHub)
    ↓
Write Failing Test (RED)
    ↓
Implement Code (GREEN)
    ↓
Verify Test Passes
    ↓
Upload Result to MinIO via Sentinel-Gear
    ↓
Mark Issue CLOSED
```

---

## Batch 1: Core Gateway Functionality (Issues #45-52)

These are critical for the Sentinel-Gear gateway and form the foundation.

### Issue #51: JWT Parser & Claims Extraction
**Title**: Integrate JWT parser to extract claims: `region`, `groups`, `services`; fail-fast on malformed tokens

**Test Name**: `SentinelGearJWTClaimsExtractionTest.java`

**Tests to Write**:
1. ✗ `test_extractValidJWT_parsesAllClaims()` - Parse valid JWT with region, groups, services
2. ✗ `test_malformedJWT_failsFast()` - Reject malformed tokens immediately
3. ✗ `test_missingSignature_rejectToken()` - Reject unsigned tokens
4. ✗ `test_expiredToken_rejectToken()` - Reject expired tokens
5. ✗ `test_invalidIssuer_rejectToken()` - Reject wrong issuer

**Test Data**:
```yaml
Valid JWT:
  Header: { alg: RS256, kid: sentinel-gear-key-1 }
  Payload:
    iss: https://keycloak:7081/auth/realms/iron-bucket
    sub: alice@acme-corp
    region: us-east-1
    groups: ["acme-corp:admins", "acme-corp:devs"]
    services: ["s3", "kms"]
    exp: 1735689600
```

**How to Run Isolated**:
```bash
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml up -d keycloak
# Wait for health check
docker run --network steel-hammer-network \
  --env KEYCLOAK_HOST=steel-hammer-keycloak:8080 \
  maven:3.9 mvn test -Dtest=SentinelGearJWTClaimsExtractionTest
```

**Success Criteria**: All 5 tests pass, JWT claims accessible via `JWTValidator` class

---

### Issue #50: Policy Parsing & REST Calls
**Title**: Add support for parsing and enforcing policies based on extracted identity via REST calls to `policy-engine`

**Test Name**: `SentinelGearPolicyEnforcementTest.java`

**Tests to Write**:
1. ✗ `test_sendPolicyRequest_withValidClaims()` - POST to policy-engine with extracted claims
2. ✗ `test_policyDeny_returns403()` - Denied access returns 403 Access Denied
3. ✗ `test_policyAllow_proxiesRequest()` - Allowed access proxies to backend
4. ✗ `test_policyEvaluation_logsDecision()` - Decision logged to audit trail
5. ✗ `test_policyRequest_includesContext()` - Request includes tenant, action, resource

**Test Data**:
```json
Policy Request:
{
  "principal": "alice@acme-corp",
  "action": "s3:PutObject",
  "resource": "acme-corp:my-bucket/documents/*",
  "context": {
    "region": "us-east-1",
    "groups": ["acme-corp:admins"],
    "tenantId": "acme-corp"
  }
}

Policy Response (Allow):
{
  "decision": "ALLOW",
  "reason": "user in admin group"
}

Policy Response (Deny):
{
  "decision": "DENY",
  "reason": "access restricted to prod hours 9-17"
}
```

**How to Run Isolated**:
```bash
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml \
  up -d keycloak steel-hammer-claimspindel
docker run --network steel-hammer-network \
  maven:3.9 mvn test -Dtest=SentinelGearPolicyEnforcementTest
```

**Success Criteria**: All 5 tests pass, policy evaluation integrated with REST client

---

### Issue #49: Policy Engine Fallback & Retry
**Title**: Implement fallback behavior and retry strategy for `policy-engine` communication failures

**Test Name**: `SentinelGearPolicyFallbackTest.java`

**Tests to Write**:
1. ✗ `test_policyEngineTimeout_retries3x()` - Retry 3 times on timeout
2. ✗ `test_policyEngineDown_fallbackDeny()` - Fail-closed: deny when engine unavailable
3. ✗ `test_retryBackoff_exponential()` - 100ms → 200ms → 400ms retry delays
4. ✗ `test_retryAfterRecovery_succeeds()` - Retry succeeds after engine restarts
5. ✗ `test_circuitBreaker_opensAfter5Failures()` - Circuit breaker pattern prevents cascade

**How to Run Isolated**:
```bash
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml \
  up -d steel-hammer-claimspindel
# Start test BEFORE Claimspindel is healthy to trigger timeouts
docker run --network steel-hammer-network \
  -e POLICY_ENGINE_TIMEOUT=2s \
  maven:3.9 mvn test -Dtest=SentinelGearPolicyFallbackTest
```

**Success Criteria**: All 5 tests pass, circuit breaker + retry logic implemented

---

### Issue #48: Proxy Request Delegation
**Title**: Ensure correct proxy request delegation to brazz-nossel or other downstream services after policy validation

**Test Name**: `SentinelGearProxyDelegationTest.java`

**Tests to Write**:
1. ✗ `test_proxyRequest_forwardsToBackend()` - GET /bucket/key proxies to brazz-nossel
2. ✗ `test_proxyHeaders_preserveAuth()` - Authorization header preserved
3. ✗ `test_proxyBody_passthrough()` - Request body passed unchanged
4. ✗ `test_proxyResponse_headersPassed()` - Response headers from backend returned
5. ✗ `test_proxyError_wrappedInS3Format()` - Backend error wrapped in S3 error format

**How to Run Isolated**:
```bash
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml \
  up -d steel-hammer-sentinel-gear steel-hammer-brazz-nossel steel-hammer-minio
docker run --network steel-hammer-network \
  maven:3.9 mvn test -Dtest=SentinelGearProxyDelegationTest
```

**Success Criteria**: All 5 tests pass, requests proxied correctly through chain

---

### Issue #47: Audit Event Emission
**Title**: Emit structured audit events (`JSON` via SLF4J) for all critical actions: registration, resolution, proxy, deny

**Test Name**: `SentinelGearAuditLoggingTest.java`

**Tests to Write**:
1. ✗ `test_auditEvent_jsonStructured()` - Audit logs are valid JSON
2. ✗ `test_auditEvent_containsTimestamp()` - All events have ISO-8601 timestamp
3. ✗ `test_auditEvent_accessDeny_logged()` - Denied access logged with reason
4. ✗ `test_auditEvent_proxySuccess_logged()` - Successful proxy logged
5. ✗ `test_auditEvent_uploadedToPostgres()` - Audit events persisted to PostgreSQL

**Test Data**:
```json
Audit Event:
{
  "timestamp": "2026-01-16T14:23:45.123Z",
  "traceId": "abc123def456",
  "eventType": "ACCESS_DENIED",
  "principal": "alice@acme-corp",
  "action": "s3:PutObject",
  "resource": "acme-corp:my-bucket/secret/*",
  "decision": "DENY",
  "reason": "policy: resource restricted",
  "statusCode": 403
}
```

**How to Run Isolated**:
```bash
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml \
  up -d steel-hammer-postgres steel-hammer-keycloak
docker run --network steel-hammer-network \
  maven:3.9 mvn test -Dtest=SentinelGearAuditLoggingTest
```

**Success Criteria**: All 5 tests pass, audit events structured and persisted

---

### Issue #46: Discovery-Registration Lifecycle
**Title**: Validate full discovery-registration lifecycle using local Buzzle-Vane instance (e.g., via Sentinel-Gear)

**Test Name**: `BuzzleVaneDiscoveryLifecycleTest.java`

**Tests to Write**:
1. ✗ `test_serviceRegistration_withEureka()` - Service registers with Eureka
2. ✗ `test_serviceHeartbeat_30secInterval()` - Heartbeat every 30 seconds
3. ✗ `test_serviceDeregistration_onShutdown()` - Service deregisters on shutdown
4. ✗ `test_metadataTag_regionIncluded()` - Region metadata in registration
5. ✗ `test_sentinelGear_discoversService()` - Sentinel-Gear discovers service via Eureka

**How to Run Isolated**:
```bash
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml \
  up -d steel-hammer-buzzle-vane
# Wait 45s for registrations
docker run --network steel-hammer-network \
  maven:3.9 mvn test -Dtest=BuzzleVaneDiscoveryLifecycleTest
```

**Success Criteria**: All 5 tests pass, service registration validated

---

### Issue #52: Identity Context Propagation
**Title**: Implement identity context propagation logic to forward OIDC/JWT from upstream requests to downstream consumers

**Test Name**: `SentinelGearIdentityPropagationTest.java`

**Tests to Write**:
1. ✗ `test_identityHeader_forwardedDownstream()` - X-Identity-Context forwarded
2. ✗ `test_tenantContext_propagated()` - Tenant ID preserved in chain
3. ✗ `test_auditorContext_propagated()` - Audit context passed to backends
4. ✗ `test_jwtClaims_availableToDownstream()` - Policy engine receives JWT claims
5. ✗ `test_traceContext_correlatedEnd2End()` - TraceID consistent across calls

**How to Run Isolated**:
```bash
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml \
  up -d steel-hammer-sentinel-gear steel-hammer-claimspindel \
       steel-hammer-brazz-nossel
docker run --network steel-hammer-network \
  maven:3.9 mvn test -Dtest=SentinelGearIdentityPropagationTest
```

**Success Criteria**: All 5 tests pass, identity context propagated end-to-end

---

## Test Infrastructure Setup

### Directory Structure
```
tests/
├── java/
│   ├── integration/
│   │   ├── SentinelGearJWTClaimsExtractionTest.java
│   │   ├── SentinelGearPolicyEnforcementTest.java
│   │   ├── SentinelGearPolicyFallbackTest.java
│   │   ├── SentinelGearProxyDelegationTest.java
│   │   ├── SentinelGearAuditLoggingTest.java
│   │   ├── BuzzleVaneDiscoveryLifecycleTest.java
│   │   └── SentinelGearIdentityPropagationTest.java
│   ├── fixtures/
│   │   ├── JWTFixtures.java
│   │   ├── PolicyFixtures.java
│   │   └── AuditFixtures.java
│   └── containers/
│       └── DockerComposeContainer.java
└── resources/
    ├── test-data/
    │   ├── valid-jwt.json
    │   ├── policy-request.json
    │   └── audit-event.json
    └── docker-compose-tests.yml
```

### Test Execution Commands

**Run all Issue #45-52 tests**:
```bash
cd /workspaces/IronBucket
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml up -d
sleep 60  # Wait for all services
mvn test -Dtest=SentinelGear*,BuzzleVane* -DfailIfNoTests=false
```

**Run single test with logs**:
```bash
mvn test -Dtest=SentinelGearJWTClaimsExtractionTest -X
docker logs steel-hammer-sentinel-gear
docker logs steel-hammer-keycloak
```

**Run with coverage**:
```bash
mvn test -Dtest=SentinelGear* jacoco:report
```

---

## MinIO Upload Pattern

### Test Result Structure
Each test suite generates a result JSON file:

**File**: `test-results/{IssueNumber}/{TestName}/result.json`

```json
{
  "issueNumber": 51,
  "issueName": "JWT Claims Extraction",
  "testName": "SentinelGearJWTClaimsExtractionTest",
  "timestamp": "2026-01-16T14:23:45.123Z",
  "status": "CLOSED",  // or OPEN if any test failed
  "testsTotal": 5,
  "testsPassed": 5,
  "testsFailed": 0,
  "testSkipped": 0,
  "executionTimeMs": 2341,
  "container": "steel-hammer-sentinel-gear",
  "tests": [
    {
      "name": "test_extractValidJWT_parsesAllClaims",
      "status": "PASSED",
      "durationMs": 234
    }
  ]
}
```

### Upload via Sentinel-Gear
```bash
# Upload test result to MinIO bucket via Sentinel-Gear gateway
ISSUE_NUMBER=51
TEST_NAME="SentinelGearJWTClaimsExtractionTest"

curl -X PUT \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  --data @test-results/$ISSUE_NUMBER/$TEST_NAME/result.json \
  http://localhost:8080/test-results/issue-${ISSUE_NUMBER}.json
```

**Expected Response** (S3 PutObject):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<PutObjectResult>
  <ETag>"abc123xyz789"</ETag>
</PutObjectResult>
```

---

## Success Criteria

### Per-Issue Success
- ✅ All tests written (RED phase)
- ✅ All tests passing (GREEN phase)
- ✅ Result JSON valid and uploaded to MinIO
- ✅ GitHub issue moved to CLOSED status
- ✅ Audit logged in PostgreSQL

### Overall Success
- ✅ Batch 1: 7 issues, 35 tests, 100% pass rate
- ✅ Batch 2: 10 issues, 50 tests planned
- ✅ Batch 3+: Remaining 35 issues
- ✅ Test results tracked in MinIO with audit trail
- ✅ CI/CD pipeline executes tests on every commit

---

## Timeline

| Week | Batch | Issues | Tests | Status |
|------|-------|--------|-------|--------|
| W1 (Now) | 1 | #45-52 | 35 | Planning |
| W1 | 1 | #45-52 | 35 | Write Tests |
| W1 | 1 | #45-52 | 35 | Implement Code |
| W2 | 2 | #22-44 | 50 | TBD |
| W3 | 3 | #1-21 | 50+ | TBD |

---

## Next Steps

1. **Create test fixture classes** (JWTFixtures, PolicyFixtures, etc.)
2. **Write first batch of tests** (Issue #51 → #52)
3. **Implement code** to make tests pass
4. **Set up MinIO upload automation**
5. **Integrate with CI/CD**
