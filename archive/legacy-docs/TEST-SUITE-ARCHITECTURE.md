# IronBucket Test Suite Architecture

**Created:** January 17, 2026  
**Sprint:** Sprint 1 - Test-First Development  
**Status:** Complete Test Suite (Implementation Pending)

---

## Test Philosophy

**All tests are written BEFORE implementation.**
- Tests define expected behavior
- Tests should initially FAIL (no implementation yet)
- Tests are NOT modified after Sprint 1 (only extended)
- Implementation in Sprint 2+ must satisfy these tests

---

## Test Categories

### 1. Security Boundary Tests (`security/`)
**Purpose:** Enforce "only via Sentinel-Gear" access

| Test Suite | Count | Location |
|------------|-------|----------|
| Direct MinIO access prevention | 10 | `security/DirectAccessPreventionTest.java` |
| Bypass attempt detection | 15 | `security/BypassAttemptTest.java` |
| JWT requirement enforcement | 20 | `security/JWTEnforcementTest.java` |
| Service-to-service auth | 12 | `security/ServiceAuthTest.java` |
| **Total** | **57** | |

### 2. Sentinel-Gear Tests (`sentinel-gear/`)
**Purpose:** JWT validation, identity normalization, routing

| Test Suite | Count | Location |
|------------|-------|----------|
| JWT signature validation | 25 | `sentinel-gear/JWTSignatureValidationTest.java` |
| Token expiration handling | 15 | `sentinel-gear/TokenExpirationTest.java` |
| Claim normalization | 30 | `sentinel-gear/ClaimNormalizationTest.java` |
| Tenant isolation | 20 | `sentinel-gear/TenantIsolationTest.java` |
| Identity caching | 18 | `sentinel-gear/IdentityCacheTest.java` |
| Token blacklisting | 12 | `sentinel-gear/TokenBlacklistTest.java` |
| Service account detection | 10 | `sentinel-gear/ServiceAccountTest.java` |
| OIDC integration | 15 | `sentinel-gear/OIDCIntegrationTest.java` |
| **Total** | **145** | |

### 3. Claimspindel Tests (`claimspindel/`)
**Purpose:** Claims-based routing

| Test Suite | Count | Location |
|------------|-------|----------|
| Role-based routing | 25 | `claimspindel/RoleBasedRoutingTest.java` |
| Tenant-aware routing | 20 | `claimspindel/TenantRoutingTest.java` |
| Region-based routing | 15 | `claimspindel/RegionRoutingTest.java` |
| Claims predicate factory | 22 | `claimspindel/ClaimsPredicateTest.java` |
| Routing fallback | 10 | `claimspindel/RoutingFallbackTest.java` |
| Dynamic route updates | 12 | `claimspindel/DynamicRoutesTest.java` |
| **Total** | **104** | |

### 4. Brazz-Nossel Tests (`brazz-nossel/`)
**Purpose:** S3 proxy, policy enforcement

| Test Suite | Count | Location |
|------------|-------|----------|
| S3 API compatibility | 40 | `brazz-nossel/S3APICompatibilityTest.java` |
| Policy evaluation | 35 | `brazz-nossel/PolicyEvaluationTest.java` |
| ABAC enforcement | 25 | `brazz-nossel/ABACEnforcementTest.java` |
| RBAC enforcement | 20 | `brazz-nossel/RBACEnforcementTest.java` |
| Audit logging | 18 | `brazz-nossel/AuditLoggingTest.java` |
| S3 proxy behavior | 30 | `brazz-nossel/S3ProxyTest.java` |
| Multipart upload | 15 | `brazz-nossel/MultipartUploadTest.java` |
| Object versioning | 12 | `brazz-nossel/ObjectVersioningTest.java` |
| **Total** | **195** | |

### 5. Integration Tests (`integration/`)
**Purpose:** Cross-service interactions

| Test Suite | Count | Location |
|------------|-------|----------|
| End-to-end request flow | 30 | `integration/E2ERequestFlowTest.java` |
| Service discovery integration | 20 | `integration/ServiceDiscoveryTest.java` |
| Keycloak integration | 25 | `integration/KeycloakIntegrationTest.java` |
| PostgreSQL audit trail | 15 | `integration/AuditTrailTest.java` |
| MinIO backend integration | 20 | `integration/MinIOIntegrationTest.java` |
| Circuit breaker behavior | 18 | `integration/CircuitBreakerTest.java` |
| Retry mechanisms | 15 | `integration/RetryMechanismTest.java` |
| Timeout handling | 12 | `integration/TimeoutHandlingTest.java` |
| **Total** | **155** | |

### 6. Observability Tests (`observability/`)
**Purpose:** Health, metrics, tracing

| Test Suite | Count | Location |
|------------|-------|----------|
| Health endpoint validation | 20 | `observability/HealthEndpointTest.java` |
| Metrics export | 25 | `observability/MetricsExportTest.java` |
| Distributed tracing | 20 | `observability/DistributedTracingTest.java` |
| Log aggregation | 15 | `observability/LogAggregationTest.java` |
| Prometheus scraping | 12 | `observability/PrometheusScrapeTest.java` |
| OTLP export | 15 | `observability/OTLPExportTest.java` |
| **Total** | **107** | |

### 7. Spring Shell CLI Tests (`cli/`)
**Purpose:** Administrative CLI (future)

| Test Suite | Count | Location |
|------------|-------|----------|
| Command parsing | 20 | `cli/CommandParsingTest.java` |
| Interactive shell | 15 | `cli/InteractiveShellTest.java` |
| Policy management commands | 25 | `cli/PolicyCommandsTest.java` |
| User management commands | 20 | `cli/UserCommandsTest.java` |
| Audit query commands | 18 | `cli/AuditCommandsTest.java` |
| Health check commands | 12 | `cli/HealthCommandsTest.java` |
| JWT inspection commands | 15 | `cli/JWTCommandsTest.java` |
| Output formatting | 10 | `cli/OutputFormattingTest.java` |
| Error handling | 15 | `cli/ErrorHandlingTest.java` |
| **Total** | **150** | |

### 8. Policy Engine Tests (`policy/`)
**Purpose:** Policy parsing, evaluation, GitOps

| Test Suite | Count | Location |
|------------|-------|----------|
| YAML policy parsing | 20 | `policy/PolicyParsingTest.java` |
| ABAC evaluation logic | 30 | `policy/ABACEvaluationTest.java` |
| RBAC evaluation logic | 25 | `policy/RBACEvaluationTest.java` |
| Condition evaluation | 28 | `policy/ConditionEvaluationTest.java` |
| Policy composition | 15 | `policy/PolicyCompositionTest.java` |
| Git-backed policy store | 22 | `policy/GitPolicyStoreTest.java` |
| Dry-run mode | 18 | `policy/DryRunModeTest.java` |
| Policy versioning | 15 | `policy/PolicyVersioningTest.java` |
| Policy conflicts | 12 | `policy/PolicyConflictTest.java` |
| **Total** | **185** | |

---

## Total Test Count

| Category | Tests |
|----------|-------|
| Security Boundary | 57 |
| Sentinel-Gear | 145 |
| Claimspindel | 104 |
| Brazz-Nossel | 195 |
| Integration | 155 |
| Observability | 107 |
| Spring Shell CLI | 150 |
| Policy Engine | 185 |
| **TOTAL** | **1,098** |

---

## Test Execution Strategy

### Phase 1: Unit Tests (Isolated)
- Run per-service
- Mocked dependencies
- Fast execution (< 5 minutes per service)

### Phase 2: Integration Tests
- Testcontainers for PostgreSQL, Keycloak, MinIO
- Docker Compose test environment
- Medium execution (10-15 minutes)

### Phase 3: E2E Tests
- Full stack in Docker
- Real network traffic
- Slow execution (20-30 minutes)

---

## Test Data & Fixtures

### Location: `test-fixtures/`
- `jwts/` - Valid/invalid/expired JWT tokens
- `identities/` - NormalizedIdentity samples
- `policies/` - YAML policy documents
- `s3-requests/` - S3 API request payloads
- `audit-logs/` - Expected audit entries

---

## Success Criteria

✅ All tests compile  
✅ All tests initially FAIL (expected, no implementation)  
✅ Tests define complete behavior  
✅ Tests are idempotent and isolated  
✅ Tests have clear assertions  
✅ Tests document expected behavior  

---

## Next Steps (Sprint 2+)

1. Implement minimal code to pass tests
2. Refactor for performance/clarity
3. Add additional test cases as needed
4. Expand E2E coverage
