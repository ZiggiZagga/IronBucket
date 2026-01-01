# Phase 3 Implementation Roadmap

## Objective
Implement the minimum code that passes all Phase 2 tests.

## Implementation Priority Order

### 1. JWT Validation Module (Week 1)
**Goal:** Pass all 59 JWT validation tests

**Scope:**
- JWT signature verification (HS256, RS256)
- Token expiration validation
- Required claims validation (sub, iss, aud, iat, exp)
- Issuer whitelist
- Audience validation
- Malformed JWT detection
- Clock skew tolerance (30s)
- JWKS endpoint support
- Service account detection

**Acceptance Criteria:**
- [ ] All 59 JWT validation tests pass
- [ ] Latency < 1ms (cached validation)
- [ ] Handles 1000 validations in < 5ms average
- [ ] 100% test coverage for jwt-validation.test.ts

**Implementation Files:**
```
Sentinel-Gear/
└── src/main/java/com/ironbucket/sentinelgear/
    ├── identity/
    │   ├── JWTValidator.java
    │   ├── JWKSProvider.java
    │   └── JWTValidationResult.java
    └── config/
        └── JWTValidationConfig.java
```

---

### 2. Claim Normalization Module (Week 1-2)
**Goal:** Pass all 40 claim normalization tests

**Scope:**
- Extract JWT claims to NormalizedIdentity
- Role normalization (realm + resource)
- Tenant extraction
- Organizational context (groups, region)
- Service account detection
- Username resolution chain
- Enrichment context (IP, User-Agent, requestId)
- Name composition
- Raw claims preservation

**Acceptance Criteria:**
- [ ] All 40 claim normalization tests pass
- [ ] NormalizedIdentity immutable after creation
- [ ] Full traceability (requestId, timestamps)
- [ ] 100% test coverage

**Implementation Files:**
```
Sentinel-Gear/
└── src/main/java/com/ironbucket/sentinelgear/
    └── identity/
        ├── NormalizedIdentity.java
        ├── ClaimNormalizer.java
        └── ClaimNormalizationResult.java
```

---

### 3. Tenant Isolation Module (Week 2)
**Goal:** Pass all 36 tenant isolation tests

**Scope:**
- Single-tenant mode enforcement
- Multi-tenant mode isolation
- Tenant identifier validation
- Tenant-aware policy filtering
- Cross-tenant access prevention
- Shared resource isolation
- Audit log tenant isolation
- Tenant-aware caching
- Header-based tenant override
- Tenant migration support

**Acceptance Criteria:**
- [ ] All 36 tenant isolation tests pass
- [ ] Impossible to access other tenant's data
- [ ] Multi-tenant deployments verified
- [ ] 100% test coverage

**Implementation Files:**
```
Sentinel-Gear/
└── src/main/java/com/ironbucket/sentinelgear/
    ├── identity/
    │   ├── TenantContext.java
    │   ├── TenantValidator.java
    │   └── TenantIsolationPolicy.java
    └── config/
        └── TenantIsolationConfig.java
```

---

### 4. Identity Cache Module (Week 2-3)
**Goal:** Pass service account and cache tests

**Scope:**
- Cache normalized identities
- Per-tenant cache isolation
- TTL-based expiration
- LRU eviction policy
- Cache size limits
- Per-tenant size tracking
- Cache invalidation by tenant
- Thread-safe caching

**Performance Target:**
- Cached JWT validation < 0.5ms
- Cache hit rate > 95% for typical workloads
- Memory-efficient (< 1MB per 1000 identities)

**Implementation Files:**
```
Sentinel-Gear/
└── src/main/java/com/ironbucket/sentinelgear/
    └── identity/
        ├── IdentityCache.java
        ├── CacheEntry.java
        └── CacheStats.java
```

---

### 5. Policy Engine Module (Week 3-4)
**Goal:** Pass policy parsing and evaluation tests

**Scope:**
- Parse YAML/JSON policies
- Validate policy schema
- Condition evaluation (StringEquals, IpAddress, DateGreaterThan, etc.)
- Deny-overrides-allow algorithm
- Wildcard support (s3:*, *, etc.)
- ARN pattern matching
- Resource and action validation
- Audit decision logging

**Acceptance Criteria:**
- [ ] All policy tests pass
- [ ] Policy evaluation < 100ms
- [ ] Support all documented condition types
- [ ] 100% test coverage

**Implementation Files:**
```
policy-engine/ (new service)
├── pom.xml
└── src/main/java/com/ironbucket/policyengine/
    ├── model/
    │   ├── Policy.java
    │   ├── PolicyEffect.java
    │   ├── Condition.java
    │   └── PolicyEvaluationResult.java
    ├── engine/
    │   ├── PolicyParser.java
    │   ├── PolicyValidator.java
    │   └── PolicyEvaluator.java
    ├── conditions/
    │   ├── ConditionEvaluator.java
    │   ├── StringEqualsCondition.java
    │   ├── IpAddressCondition.java
    │   └── DateCondition.java
    └── arn/
        ├── ARNParser.java
        └── ARNMatcher.java
```

---

### 6. S3 Proxy Module (Week 4-5)
**Goal:** Pass S3 request parsing tests

**Scope:**
- Parse S3 requests (GET, PUT, DELETE, HEAD, LIST)
- Extract bucket and key
- Validate S3 paths
- Header parsing
- Error formatting (401, 403, 500)
- Stream handling
- Backpressure support

**Acceptance Criteria:**
- [ ] All S3 proxy tests pass
- [ ] Proxy overhead < 10ms
- [ ] Full S3 API compatibility
- [ ] Streaming support verified

**Implementation Files:**
```
Brazz-Nossel/
└── src/main/java/com/ironbucket/brazznossel/
    ├── proxy/
    │   ├── S3RequestParser.java
    │   ├── S3Request.java
    │   ├── S3Response.java
    │   └── S3ErrorFormatter.java
    └── controller/
        └── S3ProxyController.java
```

---

### 7. ARN Validation Module (Week 5)
**Goal:** Pass ARN parsing and validation tests

**Scope:**
- Parse AWS ARN format
- Validate ARN components
- Wildcard pattern matching
- Resource prefix validation
- Cross-bucket detection

**Acceptance Criteria:**
- [ ] All ARN tests pass
- [ ] Support wildcards (*, *:*, etc.)
- [ ] Efficient matching for policies

**Implementation Files:**
```
Claimspindel/
└── src/main/java/com/ironbucket/claimspindel/
    ├── arn/
    │   ├── ARN.java
    │   ├── ARNParser.java
    │   └── ARNMatcher.java
```

---

### 8. Audit Logging Module (Week 5-6)
**Goal:** Implement structured audit logging

**Scope:**
- Log all access decisions
- Include decision reasoning
- Tenant isolation in logs
- Immutable audit trail
- JSON structured format
- Async log writing

**Acceptance Criteria:**
- [ ] All audit tests pass
- [ ] Tenant isolation verified
- [ ] No performance impact (async)
- [ ] Immutable log storage

**Implementation Files:**
```
Brazz-Nossel/
└── src/main/java/com/ironbucket/brazznossel/
    ├── audit/
    │   ├── AuditLogger.java
    │   ├── AuditEvent.java
    │   └── AuditStore.java
    └── config/
        └── AuditConfig.java
```

---

## Testing Strategy

### Unit Tests (Fast Feedback)
- Run on every commit
- < 10ms total time
- 100% coverage required

### Integration Tests (Before PR)
- Test across components
- < 100ms total time
- Verify contracts

### E2E Tests (Before Release)
- Full system tests
- Multi-tenant scenarios
- Disaster recovery

---

## Definition of Done (per feature)

- [ ] All tests pass (unit + integration)
- [ ] Performance SLAs met
- [ ] Security requirements verified
- [ ] Code review completed
- [ ] Documentation updated
- [ ] No test-related deprecation warnings
- [ ] Backward compatibility maintained

---

## Milestone Timeline

| Milestone | Target Date | Deliverables |
|-----------|-------------|--------------|
| M1: Identity | Jan 2, 2026 | JWT + Normalization + Tenant |
| M2: Cache | Jan 5, 2026 | Identity Cache |
| M3: Policy | Jan 9, 2026 | Policy Engine |
| M4: Proxy | Jan 12, 2026 | S3 Proxy Module |
| M5: Complete | Jan 15, 2026 | All tests passing |

---

## Risk Mitigation

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| JWT library incompatibility | Use standard `jsonwebtoken` + test early |
| Performance regression | Benchmark every feature |
| Tenant isolation bugs | Comprehensive test coverage |
| Cross-service integration | Define clear contracts |

---

## Success Criteria

✅ **All Phase 2 Tests Pass**
- 135+ test cases running green
- 0 test flakes or timeouts
- Performance SLAs verified

✅ **Performance Targets Met**
- JWT validation: < 1ms cached
- Policy evaluation: < 100ms
- S3 proxy overhead: < 10ms
- End-to-end: < 500ms (without S3 backend)

✅ **Security Verified**
- Tenant isolation confirmed
- JWT validation secure
- No data leakage in logs
- Cross-tenant access blocked

✅ **Code Quality**
- 100% test coverage for identity/policy modules
- Zero security warnings
- All lint checks pass
- Documentation complete

---

## Branching Strategy

```
main (protected)
  ↑
feature/phase-3-identity (PR for review)
  ├─ feature/jwt-validation
  ├─ feature/claim-normalization
  └─ feature/tenant-isolation

feature/phase-3-policy
  ├─ feature/policy-engine
  └─ feature/arn-parser

feature/phase-3-proxy
  ├─ feature/s3-proxy
  └─ feature/audit-logging
```

Each feature merges to `main` only after:
1. All related tests pass
2. Code review approved
3. Performance verified
