# Identity Flow Diagram

## Complete Authentication & Authorization Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│                        USER / CLIENT                                 │
│                  (Browser or API Client)                            │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             │ 1. INITIATE LOGIN
                             ↓
┌──────────────────────────────────────────────────────────────────────┐
│              SENTINEL-GEAR (OAuth2 Entrypoint)                       │
│          ├─ Detects: "No auth token" or "Invalid token"             │
│          ├─ Redirects to: ${IDP_PROVIDER}                           │
│          └─ Captures: X-Forwarded-For (IP), User-Agent               │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             │ 2. REDIRECT TO IDP
                             │    (Keycloak or Auth0)
                             ↓
┌──────────────────────────────────────────────────────────────────────┐
│              KEYCLOAK / AUTH0 (Identity Provider)                    │
│          ├─ Shows: Login Form or Passkey/WebAuthn                    │
│          ├─ Validates: Credentials or WebAuthn Attestation           │
│          ├─ Issues: JWT with claims                                  │
│          │   {                                                       │
│          │     "sub": "alice@acme.com",                              │
│          │     "realm_access": { "roles": ["dev", "admin"] },        │
│          │     "tenant": "acme-corp",                                │
│          │     "iat": 1234567890,                                    │
│          │     "exp": 1234571490                                     │
│          │   }                                                       │
│          └─ Redirects back to: Sentinel-Gear                         │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             │ 3. JWT RECEIVED
                             ↓
┌──────────────────────────────────────────────────────────────────────┐
│         SENTINEL-GEAR (JWT Validation & Enrichment)                  │
│                                                                      │
│  3a. VALIDATE JWT                                                    │
│      ├─ Fetch JWKS from IDP                                          │
│      ├─ Verify signature                                             │
│      ├─ Validate: sub, iss, aud, exp                                 │
│      └─ Check: Clock skew (30s tolerance)                            │
│                                                                      │
│  3b. NORMALIZE CLAIMS                                                │
│      ├─ Extract: sub, email, roles, tenant                           │
│      ├─ Auto-detect: Keycloak vs Generic OIDC format                 │
│      └─ Build: NormalizedIdentity object                             │
│                                                                      │
│  3c. ENRICH WITH CONTEXT                                             │
│      ├─ ipAddress: X-Forwarded-For                                   │
│      ├─ userAgent: User-Agent header                                 │
│      ├─ requestId: Generate X-Request-ID                             │
│      ├─ authMethod: Detected from token                              │
│      └─ Add custom claims (if enabled)                               │
│                                                                      │
│  3d. CACHE                                                           │
│      ├─ Store NormalizedIdentity in memory                           │
│      ├─ TTL: Token expiration time                                   │
│      └─ Key: sub + tenant + iss                                      │
│                                                                      │
│  3e. ATTACH TO REQUEST                                               │
│      └─ Add as request context: X-Iron-Identity: {JSON}              │
│      └─ Or keep in Authorization Bearer Token                        │
│                                                                      │
│  RESPONSE: 302 Redirect with JWT in cookie or query param            │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             │ 4. REQUEST TO CLAIMSPINDEL
                             │    (with JWT in Authorization header)
                             ↓
┌──────────────────────────────────────────────────────────────────────┐
│      CLAIMSPINDEL (JWT-Aware Routing Gateway)                        │
│                                                                      │
│  4a. FAST TOKEN VALIDATION                                           │
│      ├─ Check cache first (Sentinel-Gear cache or local)             │
│      ├─ If cache hit: Skip to step 4c                                │
│      ├─ If cache miss: Quick signature check                         │
│      └─ Reject if invalid (401)                                      │
│                                                                      │
│  4b. EXTRACT & NORMALIZE IDENTITY                                    │
│      ├─ Parse Bearer token                                           │
│      ├─ Extract: roles, tenant, region                               │
│      └─ Create lightweight reference                                 │
│                                                                      │
│  4c. ROUTE BASED ON CLAIMS                                           │
│      ├─ IF tenant == "acme-corp" → Route to acme-proxy               │
│      ├─ IF region == "eu-central" → Route to EU instance             │
│      ├─ IF "admin" in roles → Route to admin-endpoint                │
│      ├─ ELSE → Route to standard-endpoint                            │
│      └─ Use ClaimsRoutePredicateFactory                              │
│                                                                      │
│  4d. ATTACH IDENTITY FOR DOWNSTREAM                                  │
│      ├─ Add X-Iron-Identity header                                   │
│      ├─ Add X-Iron-Tenant header                                     │
│      ├─ Add X-Iron-User-ID header                                    │
│      ├─ Add X-Iron-Request-ID header                                 │
│      └─ Preserve original Authorization header                       │
│                                                                      │
│  RESPONSE: Route request to selected downstream service              │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             │ 5. REQUEST TO BRAZZ-NOSSEL
                             │    (S3 Proxy)
                             ↓
┌──────────────────────────────────────────────────────────────────────┐
│          BRAZZ-NOSSEL (S3 Policy Enforcement)                        │
│                                                                      │
│  5a. RECEIVE REQUEST WITH IDENTITY                                   │
│      ├─ Read X-Iron-Identity header                                  │
│      ├─ Validate JWT signature again (defense-in-depth)              │
│      ├─ Cache NormalizedIdentity                                     │
│      └─ Extract: userId, roles, tenant, region                       │
│                                                                      │
│  5b. EVALUATE POLICY                                                 │
│      ├─ Load policy from Git (Policy Store)                          │
│      ├─ Query Policy Engine with:                                    │
│      │  {                                                            │
│      │    "action": "s3:GetObject",                                  │
│      │    "resource": "s3://bucket/key",                             │
│      │    "identity": NormalizedIdentity,                            │
│      │    "context": { "ipAddress": "10.0.1.1", ... }                │
│      │  }                                                            │
│      ├─ Policy Engine evaluates DENY overrides ALLOW                 │
│      └─ Return: ALLOW, DENY, or CONDITIONAL                          │
│                                                                      │
│  5c. IF DENY → REJECT REQUEST                                        │
│      ├─ Log audit event (DENIED)                                     │
│      ├─ Return: 403 Forbidden                                        │
│      └─ Response body: error reason                                  │
│                                                                      │
│  5d. IF ALLOW → PROXY TO S3                                          │
│      ├─ Attach request ID for traceability                           │
│      ├─ Forward to S3 backend (MinIO, Ceph, AWS S3)                  │
│      ├─ Stream response back to client                               │
│      └─ Log audit event (ALLOWED)                                    │
│                                                                      │
│  RESPONSE: S3 response or 403 Forbidden                              │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             │ 6. RESPONSE TO USER
                             ↓
┌──────────────────────────────────────────────────────────────────────┐
│              USER RECEIVES S3 OBJECT or ERROR                        │
│                                                                      │
│  ✅ Success: Object data, with audit trail                           │
│  ❌ Denied: 403 with error explanation                               │
│  ❌ Expired: 401 with token refresh hint                             │
│  ❌ Invalid: 400 with claim validation error                         │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Trust Boundaries Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    UNTRUSTED NETWORK                            │
│  (User is anywhere, TLS required, no implicit trust)            │
└────────────────┬──────────────────────────────┬──────────────────┘
                 │                              │
        HTTPS only                      HTTPS + MTLS
                 │                       (inter-service)
                 ↓                              ↓
        ┌────────────────────────────────────────────────────┐
        │ BOUNDARY 1: TLS Termination (Sentinel-Gear)        │
        │  - Enforce HTTPS                                  │
        │  - Drop plain HTTP                                │
        │  - Validate client certs (if MTLS)                │
        └────────────────┬─────────────────────────────────┘
                         │
                         │ Enriched JWT
                         ↓
        ┌────────────────────────────────────────────────────┐
        │ BOUNDARY 2: Authentication (JWT Validation)        │
        │  - Verify signature                               │
        │  - Check expiration                               │
        │  - Validate claims                                │
        │  - Enforce issuer whitelist                       │
        └────────────────┬─────────────────────────────────┘
                         │
                         │ NormalizedIdentity
                         ↓
        ┌────────────────────────────────────────────────────┐
        │ BOUNDARY 3: Tenant Isolation (Claimspindel)        │
        │  - Validate tenant claim                          │
        │  - Route to tenant-specific service               │
        │  - Reject cross-tenant requests                   │
        └────────────────┬─────────────────────────────────┘
                         │
                         │ Tenant-scoped request
                         ↓
        ┌────────────────────────────────────────────────────┐
        │ BOUNDARY 4: Policy Enforcement (Brazz-Nossel)      │
        │  - Evaluate fine-grained policies                 │
        │  - Enforce DENY overrides                         │
        │  - Log audit trail                                │
        │  - Reject unauthorized access                     │
        └────────────────┬─────────────────────────────────┘
                         │
                         │ Authorized request
                         ↓
        ┌────────────────────────────────────────────────────┐
        │ BACKEND: S3-Compatible Storage                     │
        │  - MinIO, Ceph RGW, AWS S3, Wasabi, etc.           │
        │  - Isolated buckets by tenant                      │
        │  - Final audit logging                            │
        └────────────────────────────────────────────────────┘
```

---

## State Transition Diagram (Request Lifecycle)

```
                        START
                          │
                          ↓
                    ┌──────────┐
                    │ No Token │
                    └──────────┘
                          │
                          ├─ NO TOKEN PRESENT?
                          │  └─ 401 Unauthorized
                          │
                          ↓
                    ┌──────────────┐
                    │ Token Syntax │
                    │   Valid?     │
                    └──────────────┘
                          │
                          ├─ MALFORMED?
                          │  └─ 400 Bad Request
                          │
                          ↓
                    ┌──────────────┐
                    │ Signature    │
                    │   Valid?     │
                    └──────────────┘
                          │
                          ├─ INVALID SIG?
                          │  └─ 401 Unauthorized
                          │
                          ↓
                    ┌──────────────┐
                    │ Not Expired? │
                    └──────────────┘
                          │
                          ├─ EXPIRED?
                          │  └─ 401 Unauthorized
                          │
                          ↓
                    ┌──────────────┐
                    │ Claims OK?   │
                    │ (sub, iss)   │
                    └──────────────┘
                          │
                          ├─ MISSING CLAIMS?
                          │  └─ 400 Bad Request
                          │
                          ↓
                    ┌──────────────┐
                    │ Tenant OK?   │
                    │ (if required)│
                    └──────────────┘
                          │
                          ├─ TENANT MISMATCH?
                          │  └─ 403 Forbidden
                          │
                          ↓
                    ┌──────────────┐
                    │ Cached       │
                    │ Identity?    │
                    └──────────────┘
                          │
                          ├─ YES? Use cached → CONTINUE
                          │ NO?  Parse & store in cache
                          │
                          ↓
            ┌─────────────────────────────┐
            │  Route based on claims      │
            │  (tenant, region, roles)    │
            └─────────────────────────────┘
                          │
                          ↓
            ┌─────────────────────────────┐
            │ Evaluate Policy             │
            │ (fine-grained RBAC/ABAC)    │
            └─────────────────────────────┘
                          │
                    ┌─────┴─────┐
                    │           │
              ALLOW │           │ DENY
                    ↓           ↓
            ┌──────────┐  ┌──────────┐
            │ Forward  │  │ Reject   │
            │ to S3    │  │ 403      │
            └──────────┘  └──────────┘
                    │           │
                    │           └─ Log DENIED
                    │
                    ↓
            ┌──────────────────┐
            │ S3 Operation     │
            │ GET/PUT/DELETE   │
            └──────────────────┘
                    │
                    ├─ S3 Success? → Log ALLOWED + Return
                    ├─ S3 Failure? → Log ERROR + Return Error
                    │
                    ↓
                    END
```

---

## Component Interaction Sequence

```
Keycloak        Sentinel-Gear     Claimspindel       Brazz-Nossel      S3 Backend
    │                  │                  │                  │              │
    │◄─ login request ──┤                  │                  │              │
    │                  │                  │                  │              │
    ├─ show login form─►│                  │                  │              │
    │                  │                  │                  │              │
    │◄─ credentials ────┤                  │                  │              │
    │                  │                  │                  │              │
    ├─ validate & issue JWT                │                  │              │
    │                  │                  │                  │              │
    │ JWT + redirect ──►│                  │                  │              │
    │                  │                  │                  │              │
    │                  ├─ validate JWT ───┤                  │              │
    │                  ├─ normalize claims─┤                  │              │
    │                  ├─ enrich context ──┤                  │              │
    │                  │                  │                  │              │
    │                  │ NormalizedIdentity                   │              │
    │                  ├─ route decision ──►X-Iron-*         │              │
    │                  │                  ├─ validate JWT ───┤              │
    │                  │                  ├─ policy eval ────►X-Policy    │
    │                  │                  │                  ├─ check policy
    │                  │                  │                  │◄─ allow/deny
    │                  │                  │                  │              │
    │                  │                  │  if ALLOW       │              │
    │                  │                  ├─────────────────►S3 Proxy ─────►
    │                  │                  │                  │              │
    │                  │                  │                  │◄─ S3 Response
    │                  │                  │◄─────────────────┤              │
    │                  │                  │                  │              │
    │                  │◄────────── Response ───────────────┤              │
    │                  │                  │                  │              │
    │                  ├─ audit log ──────┤                  │              │
    │                  │                  │                  │              │
    │ user receives response             │                  │              │
```

---

## Caching Strategy

```
REQUEST ARRIVES
      │
      ├─ Hash JWT signature
      │
      ├─ Check: Is (JWT_hash, issuer) in VALIDATION_CACHE?
      │         TTL: JWKS rotation (hourly)
      │
      ├─YES? Skip to step 2
      │ NO?  Fetch JWKS, verify signature → store in cache
      │
      ├─ Check: Is (sub, tenant, iss) in IDENTITY_CACHE?
      │          TTL: Token expiration
      │
      ├─YES? Use cached NormalizedIdentity
      │ NO?  Parse claims, normalize → store in cache
      │
      ├─ Policy evaluation (not cached - changes frequently)
      │
      └─ CACHE STATS:
         - Validation cache: 95%+ hit rate (same token from same user)
         - Identity cache: 80%+ hit rate
         - Total latency: < 1ms per request (with cache)
```

---

## Configuration Example

```yaml
# Keycloak Realm Configuration
# ironbucket-lab realm

Clients:
  - Name: sentinel-gear-app
    ClientID: sentinel-gear-app
    AccessType: confidential
    DirectAccessGrantsEnabled: true
    ServiceAccountsEnabled: true
    
Mappers:
  - Name: tenant-mapper
    ProtocolMapper: User Property
    Property: organization  → Claim Name: tenant
    
  - Name: region-mapper
    ProtocolMapper: User Attribute
    Attribute: region  → Claim Name: region
    
  - Name: groups-mapper
    ProtocolMapper: User Group Mapper
    Groups: [...] → Claim Name: groups

Roles:
  - Realm Roles:
    - dev
    - admin
    - viewer
    - service-account
  
  - Client Roles (for sentinel-gear-app):
    - s3-read
    - s3-write
    - admin

User Example:
  - Username: alice@acme-corp.com
    Email: alice@acme-corp.com
    Attributes:
      organization: acme-corp
      region: eu-central-1
    RealmRoles: [dev, viewer]
    ClientRoles: [s3-read, s3-write]
    Groups: [engineering, platform]
```

---

## Performance Expectations

| Operation | Latency | Notes |
|-----------|---------|-------|
| Token validation (cached) | < 0.5ms | JWKS cached, signature pre-validated |
| Identity parsing (cached) | < 0.1ms | Memory-only lookup |
| Claim-based routing | < 2ms | Pattern matching in memory |
| Policy evaluation | 50-200ms | Network call to policy engine |
| S3 proxy overhead | < 10ms | Header injection + audit logging |
| **Total end-to-end** | **100-300ms** | Most time on policy + S3 backend |

---

## Security Checklist

- [x] JWT signature validation mandatory
- [x] JWKS fetched over HTTPS only
- [x] Token expiration enforced
- [x] Issuer whitelist prevents token reuse
- [x] Tenant isolation enforced
- [x] No token logging (privacy)
- [x] Rate limiting per tenant
- [x] Audit trail immutable
- [x] Clock skew tolerance (30s)
- [x] Service account detection
