# IronBucket Comprehensive Test Suite

## Phase 2: Tests First, No Implementation

This document defines the complete test suite that validates all contracts before implementation begins.

---

## 1. Test Structure

```
__tests__/
├── unit/                               # Fast, isolated, no external deps
│   ├── identity/
│   │   ├── jwt-validation.test.ts
│   │   ├── claim-normalization.test.ts
│   │   ├── tenant-isolation.test.ts
│   │   ├── service-account.test.ts
│   │   └── cache.test.ts
│   │
│   ├── policy/
│   │   ├── policy-parsing.test.ts
│   │   ├── policy-evaluation.test.ts
│   │   ├── deny-overrides.test.ts
│   │   ├── conditions.test.ts
│   │   ├── wildcards.test.ts
│   │   ├── validation.test.ts
│   │   └── cache.test.ts
│   │
│   ├── s3proxy/
│   │   ├── request-parsing.test.ts
│   │   ├── header-handling.test.ts
│   │   ├── error-formatting.test.ts
│   │   ├── arn-parsing.test.ts
│   │   └── stream-handling.test.ts
│   │
│   └── gitops/
│       ├── yaml-loading.test.ts
│       ├── git-sync.test.ts
│       └── schema-validation.test.ts
│
├── integration/                        # Medium speed, external services
│   ├── identity-flow.test.ts           # JWT → NormalizedIdentity
│   ├── policy-evaluation.test.ts       # Request → Policy decision
│   ├── s3-proxy-flow.test.ts          # Full request → S3 response
│   ├── tenant-isolation.test.ts       # Multi-tenant enforcement
│   ├── audit-logging.test.ts          # Audit trail verification
│   └── gitops-workflow.test.ts        # Git → Policy deployment
│
├── e2e/                                # Slow, full system tests
│   ├── user-login-flow.test.ts        # Keycloak → S3 access
│   ├── policy-change-flow.test.ts     # Git commit → Enforcement
│   ├── multi-tenant-flow.test.ts      # Multiple tenants isolated
│   └── disaster-recovery.test.ts      # Failover scenarios
│
├── fixtures/                           # Test data
│   ├── jwts/
│   │   ├── valid-jwt.json
│   │   ├── expired-jwt.json
│   │   ├── malformed-jwt.json
│   │   └── keycloak-jwt.json
│   │
│   ├── policies/
│   │   ├── rbac-allow.yaml
│   │   ├── abac-conditions.yaml
│   │   ├── deny-rules.yaml
│   │   └── conflicts.yaml
│   │
│   ├── s3-requests/
│   │   ├── get-object.http
│   │   ├── put-object.http
│   │   ├── delete-object.http
│   │   └── list-bucket.http
│   │
│   └── identities/
│       ├── alice-dev.json
│       ├── bob-admin.json
│       └── sa-ci.json
│
└── performance/                        # Benchmark tests
    ├── jwt-validation.bench.ts
    ├── policy-evaluation.bench.ts
    └── s3-proxy-throughput.bench.ts
```

---

## 2. Identity Tests (Unit & Integration)

### 2.1 JWT Validation Tests

```typescript
describe('JWT Validation', () => {
  describe('Valid JWT', () => {
    test('accepts valid JWT with all required claims', () => {
      const jwt = fixtures.jwts.validJwt;
      const result = validateJWT(jwt);
      expect(result.valid).toBe(true);
      expect(result.sub).toBe('alice@acme.com');
    });
    
    test('validates JWT signature against JWKS', () => {
      const jwt = fixtures.jwts.validJwt;
      const jwks = fixtures.jwks.keycloakJwks;
      const result = validateSignature(jwt, jwks);
      expect(result.valid).toBe(true);
    });
    
    test('rejects JWT with invalid signature', () => {
      const jwt = fixtures.jwts.invalidSignature;
      const result = validateJWT(jwt);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('signature');
    });
  });
  
  describe('Token Expiration', () => {
    test('rejects expired JWT', () => {
      const jwt = fixtures.jwts.expiredJwt;
      const result = validateJWT(jwt);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('expired');
    });
    
    test('accepts JWT with future exp claim', () => {
      const futureExpiry = Math.floor(Date.now() / 1000) + 3600;
      const jwt = createTestJWT({ exp: futureExpiry });
      const result = validateJWT(jwt);
      expect(result.valid).toBe(true);
    });
    
    test('respects clock skew tolerance (30 seconds)', () => {
      const slightlyFuture = Math.floor(Date.now() / 1000) + 15;
      const jwt = createTestJWT({ iat: slightlyFuture });
      const result = validateJWT(jwt, { clockSkew: 30 });
      expect(result.valid).toBe(true);
    });
  });
  
  describe('Required Claims', () => {
    test('requires sub claim', () => {
      const jwt = createTestJWT({ sub: undefined });
      const result = validateJWT(jwt);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('sub');
    });
    
    test('requires iss claim', () => {
      const jwt = createTestJWT({ iss: undefined });
      const result = validateJWT(jwt);
      expect(result.valid).toBe(false);
    });
    
    test('requires aud claim', () => {
      const jwt = createTestJWT({ aud: undefined });
      const result = validateJWT(jwt);
      expect(result.valid).toBe(false);
    });
  });
  
  describe('Issuer Whitelist', () => {
    test('accepts JWT from whitelisted issuer', () => {
      const jwt = createTestJWT({
        iss: 'https://keycloak.acme.com/realms/production'
      });
      const whitelist = [
        'https://keycloak.acme.com/realms/production',
        'https://auth0.acme.com/'
      ];
      const result = validateJWT(jwt, { issuerWhitelist: whitelist });
      expect(result.valid).toBe(true);
    });
    
    test('rejects JWT from non-whitelisted issuer', () => {
      const jwt = createTestJWT({
        iss: 'https://evil.attacker.com/'
      });
      const whitelist = ['https://keycloak.acme.com/realms/production'];
      const result = validateJWT(jwt, { issuerWhitelist: whitelist });
      expect(result.valid).toBe(false);
    });
  });
});
```

### 2.2 Claim Normalization Tests

```typescript
describe('Claim Normalization', () => {
  test('normalizes Keycloak JWT to NormalizedIdentity', () => {
    const jwt = fixtures.jwts.keycloakJwt;
    const identity = normalizeIdentity(jwt);
    
    expect(identity.userId).toBe('alice@acme.com');
    expect(identity.roles).toContain('dev');
    expect(identity.roles).toContain('admin');
    expect(identity.tenant).toBe('acme-corp');
  });
  
  test('extracts realm roles correctly', () => {
    const jwt = createTestJWT({
      realm_access: { roles: ['realm-admin', 'viewer'] }
    });
    const identity = normalizeIdentity(jwt);
    expect(identity.realmRoles).toEqual(['realm-admin', 'viewer']);
  });
  
  test('extracts resource roles correctly', () => {
    const jwt = createTestJWT({
      resource_access: {
        'sentinel-gear-app': { roles: ['s3-read', 's3-write'] }
      }
    });
    const identity = normalizeIdentity(jwt);
    expect(identity.resourceRoles.get('sentinel-gear-app')).toEqual(
      ['s3-read', 's3-write']
    );
  });
  
  test('handles generic OIDC roles array', () => {
    const jwt = createTestJWT({
      roles: ['admin', 'user'],
      realm_access: undefined
    });
    const identity = normalizeIdentity(jwt);
    expect(identity.roles).toContain('admin');
    expect(identity.roles).toContain('user');
  });
  
  test('handles missing optional claims', () => {
    const jwt = createTestJWT({
      email: undefined,
      groups: undefined
    });
    const identity = normalizeIdentity(jwt);
    expect(identity.email).toBeUndefined();
    expect(identity.groups).toEqual([]);
  });
  
  test('enriches with context (IP, userAgent, requestId)', () => {
    const jwt = fixtures.jwts.validJwt;
    const context = {
      ipAddress: '10.0.1.1',
      userAgent: 'Mozilla/5.0',
      requestId: 'req-12345'
    };
    const identity = normalizeIdentity(jwt, context);
    
    expect(identity.ipAddress).toBe('10.0.1.1');
    expect(identity.userAgent).toBe('Mozilla/5.0');
    expect(identity.requestId).toBe('req-12345');
  });
});
```

### 2.3 Tenant Isolation Tests

```typescript
describe('Tenant Isolation', () => {
  test('enforces tenant matching between JWT and request header', () => {
    const identity = {
      userId: 'alice@acme.com',
      tenant: 'acme-corp'
    };
    const requestTenant = 'other-corp';
    
    const result = validateTenantMatch(identity, requestTenant);
    expect(result.valid).toBe(false);
    expect(result.error).toContain('tenant');
  });
  
  test('allows request when tenant matches JWT', () => {
    const identity = { tenant: 'acme-corp' };
    const requestTenant = 'acme-corp';
    
    const result = validateTenantMatch(identity, requestTenant);
    expect(result.valid).toBe(true);
  });
  
  test('requires tenant claim in multi-tenant mode', () => {
    const jwt = createTestJWT({ tenant: undefined });
    const result = validateJWT(jwt, { multiTenantMode: true });
    expect(result.valid).toBe(false);
  });
  
  test('allows missing tenant in single-tenant mode', () => {
    const jwt = createTestJWT({ tenant: undefined });
    const result = validateJWT(jwt, { multiTenantMode: false });
    expect(result.valid).toBe(true);
  });
});
```

### 2.4 Service Account Tests

```typescript
describe('Service Account Detection', () => {
  test('identifies service account by client_id claim', () => {
    const jwt = createTestJWT({
      sub: 'sa-ci-deploy',
      client_id: 'ci-deployer',
      client_name: 'CI/CD'
    });
    const identity = normalizeIdentity(jwt);
    expect(identity.isServiceAccount).toBe(true);
  });
  
  test('identifies service account by sa- prefix', () => {
    const jwt = createTestJWT({ sub: 'sa-integration-test-123' });
    const identity = normalizeIdentity(jwt);
    expect(identity.isServiceAccount).toBe(true);
  });
  
  test('identifies service account by role', () => {
    const jwt = createTestJWT({
      realm_access: { roles: ['service-account', 'deployment'] }
    });
    const identity = normalizeIdentity(jwt);
    expect(identity.isServiceAccount).toBe(true);
  });
  
  test('service accounts must have tenant scope', () => {
    const jwt = createTestJWT({
      sub: 'sa-test',
      tenant: undefined
    });
    const result = validateJWT(jwt, { multiTenantMode: true });
    expect(result.valid).toBe(false);
  });
});
```

### 2.5 Caching Tests

```typescript
describe('Identity Cache', () => {
  let cache: IdentityCache;
  
  beforeEach(() => {
    cache = new IdentityCache({ ttlMinutes: 60 });
  });
  
  test('stores identity in cache with user key', () => {
    const identity = createTestIdentity();
    cache.set(identity);
    
    const cached = cache.get('alice', 'acme-corp', 'https://keycloak.com');
    expect(cached).toEqual(identity);
  });
  
  test('returns undefined for cache miss', () => {
    const cached = cache.get('unknown', 'tenant', 'issuer');
    expect(cached).toBeUndefined();
  });
  
  test('respects TTL and expires entries', async () => {
    const identity = createTestIdentity();
    const cache = new IdentityCache({ ttlMinutes: 0.01 }); // 600ms
    cache.set(identity);
    
    await sleep(650); // Wait for expiration
    const cached = cache.get('alice', 'acme-corp', 'https://keycloak.com');
    expect(cached).toBeUndefined();
  });
  
  test('limits cache size to max entries', () => {
    const cache = new IdentityCache({ maxEntries: 100 });
    
    for (let i = 0; i < 150; i++) {
      const identity = createTestIdentity({ userId: `user${i}` });
      cache.set(identity);
    }
    
    expect(cache.size()).toBeLessThanOrEqual(100);
  });
  
  test('evicts least recently used when full', () => {
    const cache = new IdentityCache({ maxEntries: 2 });
    
    const id1 = createTestIdentity({ userId: 'user1' });
    const id2 = createTestIdentity({ userId: 'user2' });
    const id3 = createTestIdentity({ userId: 'user3' });
    
    cache.set(id1);
    cache.set(id2);
    cache.set(id3);
    
    expect(cache.get('user1', 'acme-corp', 'iss')).toBeUndefined(); // Evicted
    expect(cache.get('user2', 'acme-corp', 'iss')).toBeDefined();
    expect(cache.get('user3', 'acme-corp', 'iss')).toBeDefined();
  });
});
```

---

## 3. Policy Engine Tests

### 3.1 Policy Evaluation Tests

```typescript
describe('Policy Evaluation Engine', () => {
  let engine: PolicyEngine;
  
  beforeEach(() => {
    const policies = loadFixture('policies/rbac-allow.yaml');
    engine = new PolicyEngine(policies);
  });
  
  test('evaluates simple allow policy', () => {
    const request = {
      action: 's3:GetObject',
      resource: 'arn:aws:s3:::dev-bucket/file.txt',
      identity: createTestIdentity({ roles: ['dev'] })
    };
    
    const decision = engine.evaluate(request);
    expect(decision.decision).toBe('Allow');
    expect(decision.matchedPolicies).toContain('policy-001');
  });
  
  test('evaluates deny policy that matches', () => {
    const policies = loadFixture('policies/deny-rules.yaml');
    const engine = new PolicyEngine(policies);
    
    const request = {
      action: 's3:DeleteObject',
      resource: 'arn:aws:s3:::prod-bucket/critical.csv',
      identity: createTestIdentity({ roles: ['dev'] })
    };
    
    const decision = engine.evaluate(request);
    expect(decision.decision).toBe('Deny');
    expect(decision.deniedPolicies).toContain('policy-003');
  });
  
  test('returns Deny when no policies match (default deny)', () => {
    const request = {
      action: 's3:GetObject',
      resource: 'arn:aws:s3:::unknown-bucket/file.txt',
      identity: createTestIdentity({ roles: ['viewer'] })
    };
    
    const decision = engine.evaluate(request);
    expect(decision.decision).toBe('Deny');
    expect(decision.reason).toContain('No matching policies');
  });
  
  test('evaluates within acceptable latency (< 100ms)', () => {
    const request = { /* ... */ };
    const start = Date.now();
    engine.evaluate(request);
    const latency = Date.now() - start;
    
    expect(latency).toBeLessThan(100);
  });
});
```

### 3.2 Deny-Overrides-Allow Tests

```typescript
describe('Deny-Overrides-Allow Semantics', () => {
  test('one deny policy overrides multiple allow policies', () => {
    const policies = [
      {
        name: 'allow-dev-s3-write',
        effect: 'Allow',
        principals: { values: ['dev'] },
        actions: ['s3:PutObject'],
        resources: ['arn:aws:s3:::dev-bucket/*']
      },
      {
        name: 'deny-delete-prod',
        effect: 'Deny',
        principals: { values: ['dev'] },
        actions: ['s3:DeleteObject'],
        resources: ['arn:aws:s3:::prod-*/*']
      }
    ];
    const engine = new PolicyEngine(policies);
    
    const request = {
      action: 's3:DeleteObject',
      resource: 'arn:aws:s3:::prod-bucket/file.txt',
      identity: createTestIdentity({ roles: ['dev'] })
    };
    
    const decision = engine.evaluate(request);
    expect(decision.decision).toBe('Deny');
    expect(decision.matchedPolicies).toContain('allow-dev-s3-write');
    expect(decision.deniedPolicies).toContain('deny-delete-prod');
  });
  
  test('deny with specific conditions overrides broad allow', () => {
    const policies = [
      {
        effect: 'Allow',
        principals: { values: ['admin'] },
        actions: ['s3:*'],
        resources: ['arn:aws:s3:::*/*']
      },
      {
        effect: 'Deny',
        principals: { values: ['admin'] },
        actions: ['s3:DeleteObject'],
        resources: ['arn:aws:s3:::prod-*/*'],
        conditions: [
          { type: 'StringNotEquals', key: 'aws:PrincipalTag/DeleteApproval', value: '2025-01-15' }
        ]
      }
    ];
    const engine = new PolicyEngine(policies);
    
    const request = {
      action: 's3:DeleteObject',
      resource: 'arn:aws:s3:::prod-bucket/file.txt',
      identity: createTestIdentity({ roles: ['admin'] })
    };
    
    const decision = engine.evaluate(request);
    expect(decision.decision).toBe('Deny');
  });
});
```

### 3.3 Condition Evaluation Tests

```typescript
describe('Condition Evaluation', () => {
  describe('StringEquals', () => {
    test('evaluates string equality', () => {
      const condition = {
        type: 'StringEquals',
        key: 'aws:username',
        value: 'alice'
      };
      const identity = { username: 'alice' };
      expect(evaluateCondition(condition, identity)).toBe(true);
    });
    
    test('returns false on mismatch', () => {
      const condition = {
        type: 'StringEquals',
        key: 'aws:username',
        value: 'alice'
      };
      const identity = { username: 'bob' };
      expect(evaluateCondition(condition, identity)).toBe(false);
    });
  });
  
  describe('IpAddress', () => {
    test('matches IP in CIDR range', () => {
      const condition = {
        type: 'IpAddress',
        key: 'aws:SourceIp',
        values: ['10.0.0.0/8']
      };
      const context = { sourceIp: '10.1.2.3' };
      expect(evaluateCondition(condition, {}, context)).toBe(true);
    });
    
    test('rejects IP outside range', () => {
      const condition = {
        type: 'IpAddress',
        key: 'aws:SourceIp',
        values: ['10.0.0.0/8']
      };
      const context = { sourceIp: '203.0.113.1' };
      expect(evaluateCondition(condition, {}, context)).toBe(false);
    });
  });
  
  describe('TimeOfDay', () => {
    test('matches time within business hours', () => {
      const condition = {
        type: 'TimeOfDay',
        key: 'aws:CurrentTime',
        value: '09:00-17:00'
      };
      const mockTime = new Date('2025-01-15T12:30:00Z');
      expect(evaluateCondition(condition, {}, {}, mockTime)).toBe(true);
    });
    
    test('rejects time outside business hours', () => {
      const condition = {
        type: 'TimeOfDay',
        key: 'aws:CurrentTime',
        value: '09:00-17:00'
      };
      const mockTime = new Date('2025-01-15T20:30:00Z');
      expect(evaluateCondition(condition, {}, {}, mockTime)).toBe(false);
    });
  });
});
```

### 3.4 Wildcard & ARN Matching Tests

```typescript
describe('Wildcard & ARN Matching', () => {
  test('matches bucket with * wildcard', () => {
    const resource = 'arn:aws:s3:::my-bucket/*';
    const requested = 'arn:aws:s3:::my-bucket/folder/file.txt';
    expect(arnMatches(requested, resource)).toBe(true);
  });
  
  test('matches multiple levels with **', () => {
    const resource = 'arn:aws:s3:::my-bucket/**';
    const requested = 'arn:aws:s3:::my-bucket/a/b/c/d/file.txt';
    expect(arnMatches(requested, resource)).toBe(true);
  });
  
  test('matches prefix pattern', () => {
    const resource = 'arn:aws:s3:::dev-*/*';
    const requested = 'arn:aws:s3:::dev-bucket/file.txt';
    expect(arnMatches(requested, resource)).toBe(true);
  });
  
  test('does not match outside pattern', () => {
    const resource = 'arn:aws:s3:::dev-bucket/*';
    const requested = 'arn:aws:s3:::prod-bucket/file.txt';
    expect(arnMatches(requested, resource)).toBe(false);
  });
  
  test('matches action wildcards', () => {
    expect(actionMatches('s3:GetObject', 's3:*')).toBe(true);
    expect(actionMatches('s3:GetObject', 's3:Get*')).toBe(true);
    expect(actionMatches('s3:GetObject', 's3:Put*')).toBe(false);
  });
});
```

---

## 4. S3 Proxy Tests

### 4.1 Request Parsing Tests

```typescript
describe('S3 Request Parsing', () => {
  test('parses GET object request', () => {
    const request = createMockRequest({
      method: 'GET',
      path: '/dev-bucket/folder/file.txt'
    });
    
    const parsed = parseS3Request(request);
    expect(parsed.operation).toBe('GetObject');
    expect(parsed.bucket).toBe('dev-bucket');
    expect(parsed.key).toBe('folder/file.txt');
  });
  
  test('parses PUT object request', () => {
    const request = createMockRequest({
      method: 'PUT',
      path: '/dev-bucket/folder/file.txt'
    });
    
    const parsed = parseS3Request(request);
    expect(parsed.operation).toBe('PutObject');
  });
  
  test('parses ListBucket request', () => {
    const request = createMockRequest({
      method: 'GET',
      path: '/dev-bucket'
    });
    
    const parsed = parseS3Request(request);
    expect(parsed.operation).toBe('ListBucket');
    expect(parsed.bucket).toBe('dev-bucket');
  });
  
  test('converts to S3 ARN', () => {
    const request = createMockRequest({
      method: 'GET',
      path: '/my-bucket/folder/file.txt'
    });
    
    const arn = requestToARN(request);
    expect(arn).toBe('arn:aws:s3:::my-bucket/folder/file.txt');
  });
});
```

### 4.2 Request Lifecycle Tests

```typescript
describe('S3 Proxy Request Lifecycle', () => {
  let proxy: S3Proxy;
  let mockBackend: MockS3Backend;
  
  beforeEach(() => {
    mockBackend = new MockS3Backend();
    proxy = new S3Proxy(mockBackend);
  });
  
  test('authenticates request with valid JWT', async () => {
    const request = createMockRequest({
      headers: { authorization: 'Bearer ' + fixtures.jwts.validJwt }
    });
    
    const response = await proxy.handle(request);
    expect(response.status).not.toBe(401);
  });
  
  test('rejects request without JWT (401)', async () => {
    const request = createMockRequest({
      headers: {} // No authorization
    });
    
    const response = await proxy.handle(request);
    expect(response.status).toBe(401);
    expect(response.body).toContain('Unauthorized');
  });
  
  test('enforces policy and returns 403 if denied', async () => {
    const request = createMockRequest({
      method: 'DELETE',
      path: '/prod-bucket/file.txt',
      headers: { authorization: 'Bearer ' + fixtures.jwts.devJwt }
    });
    
    const response = await proxy.handle(request);
    expect(response.status).toBe(403);
    expect(response.body).toContain('AccessDenied');
  });
  
  test('proxies allowed request to S3 backend', async () => {
    const request = createMockRequest({
      method: 'GET',
      path: '/dev-bucket/file.txt',
      headers: { authorization: 'Bearer ' + fixtures.jwts.devJwt }
    });
    
    mockBackend.mockGetObject({ size: 1024 });
    const response = await proxy.handle(request);
    
    expect(response.status).toBe(200);
    expect(mockBackend.getObjectCalled).toBe(true);
  });
  
  test('generates audit log for allowed request', async () => {
    const request = createMockRequest({
      method: 'GET',
      path: '/dev-bucket/file.txt',
      headers: { authorization: 'Bearer ' + fixtures.jwts.devJwt }
    });
    
    mockBackend.mockGetObject({ size: 1024 });
    await proxy.handle(request);
    
    const auditLog = getLastAuditLog();
    expect(auditLog.decision).toBe('Allow');
    expect(auditLog.action).toBe('s3:GetObject');
  });
  
  test('generates audit log for denied request', async () => {
    const request = createMockRequest({
      method: 'DELETE',
      path: '/prod-bucket/file.txt',
      headers: { authorization: 'Bearer ' + fixtures.jwts.devJwt }
    });
    
    await proxy.handle(request);
    
    const auditLog = getLastAuditLog();
    expect(auditLog.decision).toBe('Deny');
  });
});
```

### 4.3 Header Handling Tests

```typescript
describe('S3 Proxy Headers', () => {
  test('adds X-Iron-Request-ID to response', async () => {
    const request = createMockRequest({
      headers: { authorization: 'Bearer ' + fixtures.jwts.validJwt }
    });
    
    const response = await proxy.handle(request);
    expect(response.headers['x-iron-request-id']).toBeDefined();
  });
  
  test('adds identity headers for downstream logging', async () => {
    const request = createMockRequest({
      headers: { authorization: 'Bearer ' + fixtures.jwts.devJwt }
    });
    
    const response = await proxy.handle(request);
    expect(response.headers['x-iron-user-id']).toBe('alice@acme.com');
    expect(response.headers['x-iron-tenant']).toBe('acme-corp');
  });
  
  test('preserves Authorization header to backend', async () => {
    const request = createMockRequest({
      headers: { authorization: 'Bearer ' + fixtures.jwts.validJwt }
    });
    
    mockBackend.mockGetObject({});
    await proxy.handle(request);
    
    const backendRequest = mockBackend.lastRequest();
    expect(backendRequest.headers.authorization).toBeDefined();
  });
});
```

---

## 5. Integration Tests

### 5.1 Complete Identity Flow

```typescript
describe('Identity Flow Integration', () => {
  test('keycloak jwt → normalized identity → policy evaluation', async () => {
    const keycloakJwt = fixtures.jwts.keycloakJwt;
    
    // Step 1: Validate and normalize
    const identity = normalizeIdentity(keycloakJwt, {
      ipAddress: '10.0.1.1',
      requestId: 'req-123'
    });
    
    expect(identity.userId).toBe('alice@acme.com');
    expect(identity.roles).toContain('dev');
    expect(identity.ipAddress).toBe('10.0.1.1');
    
    // Step 2: Use in policy evaluation
    const policyRequest = {
      action: 's3:GetObject',
      resource: 'arn:aws:s3:::dev-bucket/file.txt',
      identity
    };
    
    const decision = policyEngine.evaluate(policyRequest);
    expect(decision.decision).toBe('Allow');
  });
});
```

### 5.2 Complete Policy Evaluation Flow

```typescript
describe('Policy Evaluation Integration', () => {
  test('request → policy load → evaluation → decision', async () => {
    const policies = await loadPoliciesFromGit('main');
    const engine = new PolicyEngine(policies);
    
    const request = createEvaluationRequest();
    const decision = engine.evaluate(request);
    
    expect(decision.decision).toBeDefined();
    expect(decision.matchedPolicies).toBeDefined();
    expect(decision.evaluationTime).toBeLessThan(100);
  });
});
```

---

## 6. Performance Benchmarks

### 6.1 JWT Validation Benchmark

```typescript
describe('JWT Validation Performance', () => {
  test('validates 1000 JWTs in < 100ms total', () => {
    const start = Date.now();
    for (let i = 0; i < 1000; i++) {
      validateJWT(fixtures.jwts.validJwt);
    }
    const duration = Date.now() - start;
    expect(duration).toBeLessThan(100); // With caching
  });
});
```

### 6.2 Policy Evaluation Benchmark

```typescript
describe('Policy Evaluation Performance', () => {
  test('evaluates 100 complex policies in < 5ms', () => {
    const policies = generateTestPolicies(100);
    const engine = new PolicyEngine(policies);
    const request = createTestEvaluationRequest();
    
    const start = Date.now();
    engine.evaluate(request);
    const duration = Date.now() - start;
    
    expect(duration).toBeLessThan(5);
  });
});
```

---

## 7. Failure Scenarios (Negative Tests)

```typescript
describe('Failure Scenarios', () => {
  test('policy engine offline → fail-closed (deny all)', async () => {
    const proxy = new S3Proxy(backend, {
      policyEngineFallback: 'deny'
    });
    
    // Simulate policy engine timeout
    policyEngine.simulate({ offline: true });
    
    const request = createMockRequest();
    const response = await proxy.handle(request);
    expect(response.status).toBe(500);
  });
  
  test('s3 backend unavailable → 503 Service Unavailable', async () => {
    backend.simulate({ offline: true });
    
    const request = createMockRequest();
    const response = await proxy.handle(request);
    expect(response.status).toBe(503);
  });
  
  test('policy file corrupted → validation error', async () => {
    const corruptedPolicy = 'invalid: {yaml';
    const result = validatePolicies(corruptedPolicy);
    expect(result.valid).toBe(false);
  });
});
```

---

## 8. Testing Guidelines

### Run Unit Tests
```bash
npm run test:unit
# Expected: < 5 seconds, all passing
```

### Run Integration Tests
```bash
npm run test:integration
# Expected: < 30 seconds, docker-compose required
```

### Run E2E Tests
```bash
npm run test:e2e
# Expected: < 2 minutes, full infrastructure required
```

### Check Coverage
```bash
npm run test:coverage
# Expected: > 80% code coverage
```

---

## 9. Test Data Fixtures

All fixtures should be version-controlled in `__tests__/fixtures/`:

- Valid JWTs (from Keycloak, Auth0, generic OIDC)
- Expired/invalid JWTs
- Complex policies with conditions
- Real S3 request examples
- Test identities for all user types
- Expected decisions for policy evaluation

---

## 10. CI/CD Integration

Every PR MUST:
1. ✅ Pass all unit tests
2. ✅ Pass all integration tests (with docker-compose)
3. ✅ Achieve > 80% code coverage
4. ✅ Pass linting
5. ✅ Run performance benchmarks (fail if > 10% slower)

---

## 11. Test Maintenance

- Update tests when contracts change (Phase 1 docs)
- Add test for every bug fix (regression prevention)
- Review test coverage monthly
- Keep fixtures in sync with real production tokens
