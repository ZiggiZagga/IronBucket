# Identity Model Contract

## 1. Overview

The Identity Model defines how IronBucket represents, normalizes, and validates identity across the entire system. Every request enters through a normalized identity context that drives routing, policy evaluation, and audit logging.

**Goal:** Ensure consistent identity representation across all components (Sentinel-Gear → Claimspindel → Brazz-Nossel → Policy Engine).

---

## 2. JWT Claims Structure

### 2.1 Required Base Claims
Every valid JWT in IronBucket MUST contain:

```json
{
  "sub": "user-id",              // REQUIRED: Subject (unique user identifier)
  "iat": 1234567890,             // REQUIRED: Issued at (Unix timestamp)
  "exp": 1234571490,             // REQUIRED: Expiration (Unix timestamp)
  "iss": "https://idp.example",  // REQUIRED: Issuer (Identity Provider)
  "aud": "sentinel-gear-app"     // REQUIRED: Audience (this application)
}
```

### 2.2 Normalized IronBucket Claims
IronBucket extends the base JWT with normalized claims:

```json
{
  "realm_access": {
    "roles": [
      "dev",
      "admin",
      "viewer"
    ]
  },
  "resource_access": {
    "sentinel-gear-app": {
      "roles": [
        "s3-read",
        "s3-write"
      ]
    }
  },
  "tenant": "acme-corp",         // RECOMMENDED: Tenant identifier
  "region": "eu-central-1",      // RECOMMENDED: Geographic region
  "email": "user@example.com",   // OPTIONAL: User email
  "preferred_username": "alice", // OPTIONAL: Human-readable name
  "given_name": "Alice",         // OPTIONAL: First name
  "family_name": "Smith",        // OPTIONAL: Last name
  "groups": [
    "engineering",
    "platform"
  ]                              // OPTIONAL: Organizational groups
}
```

---

## 3. Normalized Identity Model

After JWT validation, IronBucket normalizes all identity info into a standard `NormalizedIdentity` object:

### 3.1 NormalizedIdentity Structure

```typescript
interface NormalizedIdentity {
  // Core identity
  userId: string;                        // sub claim
  username: string;                      // preferred_username or email
  issuer: string;                        // iss claim
  issuedAt: number;                      // iat (Unix timestamp)
  expiresAt: number;                     // exp (Unix timestamp)
  
  // Role & Permission Context
  roles: string[];                       // All roles (realm + resource)
  realmRoles: string[];                  // Keycloak realm_access.roles
  resourceRoles: Map<string, string[]>;  // Resource-specific roles
  
  // Organizational Context
  tenant: string;                        // Tenant isolation key
  region?: string;                       // Geographic isolation
  groups: string[];                      // Organizational groups
  
  // User Metadata
  email?: string;                        // Email address
  firstName?: string;                    // given_name
  lastName?: string;                     // family_name
  fullName?: string;                     // Computed: firstName + lastName
  
  // Enrichment Context (added by Sentinel-Gear)
  ipAddress?: string;                    // Source IP for policies
  userAgent?: string;                    // Source User-Agent
  requestId?: string;                    // Unique request trace ID
  
  // Service Account Flag
  isServiceAccount: boolean;              // true if this is a service account
  
  // Raw JWT Claims (for policy evaluation)
  rawClaims: Record<string, any>;         // Original JWT claims
}
```

### 3.2 Validation Rules

- **Expiration Check:** `expiresAt > currentTime` or request is rejected (401 Unauthorized)
- **Issuer Whitelist:** `issuer` must match a configured IDP
- **Audience Check:** `aud` must match expected service identifier
- **Tenant Requirement:** If multi-tenant mode enabled, `tenant` claim is REQUIRED
- **Role Validation:** At least one role must be present (`roles.length > 0`)

---

## 4. Tenant Model

### 4.1 Single-Tenant Mode
```
userId: "alice@org.com"
tenant: null (or undefined)
```

### 4.2 Multi-Tenant Mode (Recommended)
```
userId: "alice@acme-corp.com"
tenant: "acme-corp"
```

Every request in multi-tenant mode MUST have:
- A valid `tenant` claim in JWT
- An extracted tenant ID in `NormalizedIdentity`
- Tenant propagation through all downstream services

**Security Rule:** No cross-tenant access. All policies enforce tenant isolation.

---

## 5. Service Account Model

Service accounts represent non-human entities (CI/CD, integrations, scheduled jobs).

### 5.1 Service Account JWT Structure

```json
{
  "sub": "sa-ci-deploy-123",
  "iss": "https://idp.example",
  "aud": "sentinel-gear-app",
  "client_id": "ci-deployer",           // NEW: Service account client ID
  "client_name": "CI/CD Deployment",    // NEW: Human-readable name
  "realms_access": {
    "roles": [
      "s3-write",
      "deployment-role"
    ]
  },
  "tenant": "acme-corp",                // Service accounts are tenant-scoped
  "iat": 1234567890,
  "exp": 1234571490
}
```

### 5.2 Identification Logic

A JWT is classified as a service account if:
- `client_id` claim is present, OR
- `sub` matches pattern `^sa-.*$`, OR
- `realm_access.roles` contains `service-account` role

### 5.3 Service Account Constraints

- **No Interactive Sessions:** Service accounts cannot have session tokens
- **Longer TTL:** May have longer expiration (up to 24 hours)
- **Audit Logging:** All service account actions logged with `client_id` for traceability
- **Rate Limiting:** May be subject to stricter rate limits than human users

---

## 6. Claim Enrichment Rules

Sentinel-Gear may enrich the JWT with additional context:

| Claim | Source | Purpose |
|-------|--------|---------|
| `ipAddress` | HTTP X-Forwarded-For header | Policy evaluation (IP-based access) |
| `userAgent` | HTTP User-Agent header | Browser/client detection |
| `requestId` | Generated X-Request-ID | Request tracing and audit |
| `sessionId` | Session cookie | Session tracking |
| `authMethod` | Authentication flow used | MFA detection, authentication strength |
| `authTime` | Time of successful auth | Fresh token validation |

---

## 7. Identity Flow Sequence

```
User → Keycloak (login) → JWT issued
  ↓
Sentinel-Gear (OAuth2 handler)
  ├─ Validates JWT signature + claims
  ├─ Enriches with ipAddress, requestId, etc.
  ├─ Normalizes to NormalizedIdentity
  └─ Attaches to HTTP Authorization header
  ↓
Claimspindel (JWT-aware router)
  ├─ Validates token again (fast path)
  ├─ Extracts and caches NormalizedIdentity
  ├─ Routes based on claims (region, tenant, roles)
  └─ Passes to downstream
  ↓
Brazz-Nossel (S3 proxy)
  ├─ Validates identity again
  ├─ Enforces policy with NormalizedIdentity
  ├─ Logs audit event
  └─ Proxies request to S3 backend
```

---

## 8. Error Handling

### 8.1 Invalid Token Scenarios

| Scenario | HTTP Status | Response | Logged? |
|----------|------------|----------|---------|
| Missing/malformed Authorization header | 401 | `{ "error": "missing_auth" }` | ❌ |
| Expired token | 401 | `{ "error": "token_expired" }` | ✅ (INFO) |
| Invalid signature | 401 | `{ "error": "invalid_signature" }` | ✅ (WARN) |
| Missing required claims | 400 | `{ "error": "invalid_claims" }` | ✅ (WARN) |
| Tenant mismatch | 403 | `{ "error": "forbidden_tenant" }` | ✅ (WARN) |
| Role insufficient | 403 | `{ "error": "insufficient_role" }` | ✅ (INFO) |

---

## 9. Caching & Performance

### 9.1 Token Validation Caching
- **Cache TTL:** 5 minutes (configurable)
- **Cache Key:** `jwt_signature_hash` + `issuer` + `aud`
- **Invalidation:** On issuer's JWKS rotation (hourly)

### 9.2 Normalized Identity Caching
- **Cache TTL:** Match JWT expiration
- **Cache Key:** `sub` + `tenant` + `iss`
- **Purpose:** Avoid re-parsing JWT on every request

---

## 10. Backward Compatibility

IronBucket MUST support both:

1. **Keycloak Native Claims** (realm_access.roles, resource_access)
2. **Generic OIDC Claims** (roles as array, groups)

Auto-detection logic:
```
IF jwt.realm_access EXISTS:
  roles = jwt.realm_access.roles + (jwt.resource_access[client_id]?.roles || [])
ELSE IF jwt.roles IS ARRAY:
  roles = jwt.roles
ELSE:
  roles = []
```

---

## 11. Testing Requirements

Every implementation of the identity model MUST pass:

1. ✅ Valid JWT with all claims → NormalizedIdentity succeeds
2. ✅ Expired token → 401 Unauthorized
3. ✅ Missing `sub` claim → 400 Bad Request
4. ✅ Invalid signature → 401 Unauthorized
5. ✅ Multi-tenant: same user, different tenants → isolated
6. ✅ Service account identified correctly
7. ✅ Claim enrichment populated correctly
8. ✅ Role extraction from both Keycloak and generic OIDC
9. ✅ Backward compatibility with legacy claims
10. ✅ Cached identity served quickly (< 1ms)

---

## 12. Security Constraints

1. **Never log full JWT** (privacy)
2. **Never log passwords** in any form
3. **Always validate expiration**
4. **Always validate audience**
5. **Always validate issuer** against whitelist
6. **Always enforce tenant isolation** in multi-tenant mode
7. **Rate limit by tenant** (not global)
8. **Audit all failed auth attempts**
9. **Reject tokens with future `iat`** (clock skew tolerance: 30 seconds)
10. **No identity escalation** (roles cannot be modified by client)

---

## 13. Configuration Reference

```yaml
# Sentinel-Gear configuration
ironbucket:
  identity:
    # JWT Validation
    jwk_uri: ${IDP_PROVIDER_PROTOCOL}://${IDP_PROVIDER_HOST}/realms/${IDP_PROVIDER_REALM}/protocol/openid-connect/certs
    issuer_whitelist:
      - https://keycloak.example.com/realms/production
      - https://auth0.example.com/
    audience_expected: sentinel-gear-app
    clock_skew_tolerance_seconds: 30
    
    # Caching
    cache_ttl_minutes: 5
    cache_max_entries: 10000
    
    # Tenant Mode
    multi_tenant_mode: true
    
    # Rate Limiting
    rate_limit_per_tenant_per_minute: 1000
    rate_limit_per_user_per_minute: 100
```

---

## 14. Evolution Roadmap

- [ ] **v1.0:** Basic JWT validation + role extraction
- [ ] **v2.0:** Multi-tenant isolation + claim enrichment
- [ ] **v2.5:** Service account support
- [ ] **v3.0:** Attribute-based access control (ABAC) with custom claims
- [ ] **v3.5:** Token revocation checking (blacklist/CTI)
- [ ] **v4.0:** Delegation flow (user → user with reduced permissions)

---

## References

- [OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html)
- [JWT RFC 7519](https://tools.ietf.org/html/rfc7519)
- [Keycloak Token Introspection](https://www.keycloak.org/docs/latest/server_admin/)
- [OAuth 2.0 Security Best Practices](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics)
