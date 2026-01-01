# S3 Proxy Contract

## 1. Overview

Brazz-Nossel is the S3-compatible proxy that sits between clients and any S3 backend (MinIO, Ceph RGW, AWS S3, etc.). It transparently enforces identity and policy while maintaining full S3 API compatibility.

**Goal:** Drop-in replacement for S3 that adds identity-aware authorization and audit logging without requiring client code changes.

---

## 2. Supported Operations

### 2.1 Object Operations

| Operation | HTTP Method | Path | Status | Notes |
|-----------|------------|------|--------|-------|
| `GetObject` | GET | `/bucket/key` | ✅ MVP | Read object |
| `PutObject` | PUT | `/bucket/key` | ✅ MVP | Upload object |
| `DeleteObject` | DELETE | `/bucket/key` | ✅ MVP | Delete object |
| `CopyObject` | PUT (x-amz-copy-source) | `/bucket/dest` | 🔜 Phase 2 | Copy within/across buckets |
| `GetObjectVersion` | GET | `/bucket/key?versionId=xyz` | 🔜 Phase 2 | Read specific version |
| `DeleteObjectVersion` | DELETE | `/bucket/key?versionId=xyz` | 🔜 Phase 2 | Delete version |
| `ListBucketVersions` | GET | `/bucket?versions` | 🔜 Phase 2 | List object versions |

### 2.2 Bucket Operations

| Operation | HTTP Method | Path | Status | Notes |
|-----------|------------|------|--------|-------|
| `ListBucket` | GET | `/bucket` | ✅ MVP | List objects |
| `GetBucketLocation` | GET | `/bucket?location` | ✅ MVP | Get region |
| `GetBucketVersioning` | GET | `/bucket?versioning` | 🔜 Phase 2 | Check version status |
| `HeadBucket` | HEAD | `/bucket` | ✅ MVP | Check bucket exists |
| `GetBucketPolicy` | GET | `/bucket?policy` | 🔜 Phase 3 | Read S3 policy |
| `PutBucketPolicy` | PUT | `/bucket?policy` | 🔜 Phase 3 | Modify S3 policy |

### 2.3 Multipart Upload (Phase 3)

```
InitiateMultipartUpload   POST    /bucket/key?uploads
UploadPart                PUT     /bucket/key?partNumber=1&uploadId=xyz
CompleteMultipartUpload   POST    /bucket/key?uploadId=xyz
AbortMultipartUpload      DELETE  /bucket/key?uploadId=xyz
ListMultipartUploads      GET     /bucket?uploads
```

---

## 3. Request Lifecycle

### 3.1 Request Flow Diagram

```
┌──────────────────────────────────────────────────────┐
│  CLIENT REQUEST                                      │
│  GET /s3/bucket/key                                  │
│  Authorization: Bearer {JWT}                         │
└──────────────────────────┬──────────────────────────┘
                           │
                           ↓
        ┌──────────────────────────────────┐
        │ 1. ACCEPT & PARSE REQUEST        │
        │  ├─ Extract: path, method, headers
        │  ├─ Validate: HTTPS or internal  │
        │  ├─ Parse: S3 path to bucket/key │
        │  └─ Generate: requestId          │
        └──────────────────┬───────────────┘
                           │
                           ↓
        ┌──────────────────────────────────┐
        │ 2. AUTHENTICATE                  │
        │  ├─ Extract JWT from Authorization
        │  ├─ Validate: signature, expiry  │
        │  ├─ Normalize: to NormalizedIdentity
        │  └─ Check: tenant isolation      │
        └──────────────────┬───────────────┘
                           │
                    ┌──NO JWT? ──┐
                    │            │
                    ↓            ↓
              REJECT         [Retry with JWT]
              401             (client refreshes)
                │
                ↓
        ┌──────────────────────────────────┐
        │ 3. AUTHORIZE WITH POLICY ENGINE  │
        │  ├─ Build evaluation request:    │
        │  │  {                            │
        │  │    action: s3:GetObject,      │
        │  │    resource: arn:aws:s3:::... │
        │  │    identity: NormalizedIdentity
        │  │  }                            │
        │  ├─ Call Policy Engine (REST)   │
        │  ├─ Evaluate: ALLOW/DENY         │
        │  └─ Get: decision, reason        │
        └──────────────────┬───────────────┘
                           │
                  ┌────────┴────────┐
                  │                 │
            ┌──DENY────┐     ┌──ALLOW──┐
            │           │     │          │
            ↓           │     │          ↓
      ┌──────────┐      │     │  ┌──────────────┐
      │ 4. DENY  │      │     │  │ 4. PROXY     │
      │ ├─ Log:  │      │     │  │ ├─ Forward:  │
      │ │ DENIED  │      │     │  │ │ HTTP req   │
      │ ├─ Return │      │     │  │ ├─ to S3     │
      │ │ 403     │      │     │  │ │ backend    │
      │ └─        │      │     │  │ ├─ Stream:   │
      │           │      │     │  │ │ response   │
      └───────┬───┘      │     │  │ │ back       │
              │          │     │  └──┬───────────┘
              │          │     │     │
              └──┬───────┘     └─────┼─────
                 │                   │
                 │                   ↓
              ┌──┴─────────────────────────────┐
              │ 5. AUDIT LOG                   │
              │  ├─ timestamp                  │
              │  ├─ requestId                  │
              │  ├─ userId                     │
              │  ├─ action (s3:GetObject)      │
              │  ├─ resource (bucket/key)      │
              │  ├─ decision (Allow/Deny)      │
              │  ├─ evaluationTime             │
              │  ├─ responseCode               │
              │  └─ tenant                     │
              └──────────┬──────────────────────┘
                         │
                         ↓
        ┌──────────────────────────────────┐
        │ 6. RETURN RESPONSE TO CLIENT     │
        │  ├─ Status: 200, 403, 500, etc.  │
        │  ├─ Headers: S3-compatible       │
        │  ├─ Body: S3 response or error   │
        │  └─ X-Request-ID: Trace ID       │
        └──────────────────────────────────┘
```

### 3.2 Request Timing SLA

| Stage | Typical | P99 | Notes |
|-------|---------|-----|-------|
| Parse request | 1ms | 2ms | Fast |
| JWT validation (cached) | 0.5ms | 1ms | Fast path |
| Policy evaluation | 50-100ms | 200ms | Network call |
| S3 proxy overhead | 5-10ms | 20ms | Request translation |
| S3 backend (GET 1MB) | 50-500ms | 2s | Varies by backend |
| Audit log write | 1-5ms | 10ms | Async queue |
| **Total (GET 1MB)** | **100-700ms** | **2.5s** | **Client perceivable** |

---

## 4. HTTP Headers Contract

### 4.1 Required Request Headers

```
Authorization: Bearer {JWT}                  # REQUIRED
X-Request-ID: req-12345678                   # Recommended
Content-Type: application/octet-stream       # For PUT/POST

Accept-Encoding: gzip, deflate               # OPTIONAL
User-Agent: aws-cli/2.x or similar           # For metrics
```

### 4.2 Proxy-Added Headers (To S3 Backend)

```
X-Forwarded-For: {clientIP}                  # Client IP
X-Forwarded-Proto: https                     # Protocol
X-Iron-Request-ID: req-12345678              # Trace ID
X-Iron-User-ID: alice@acme.com               # User ID
X-Iron-Tenant: acme-corp                     # Tenant
X-Iron-Identity: {NormalizedIdentity}        # Full identity
```

### 4.3 Response Headers (From S3)

```
Content-Length: {size}                       # Object size
Content-Type: {type}                         # Media type
ETag: "{etag}"                               # S3 ETag
Last-Modified: {date}                        # Modification date
x-amz-version-id: {versionId}                # If versioning
x-amz-request-id: {s3RequestId}              # S3 request ID

X-Iron-Request-ID: req-12345678              # Our trace ID
X-Iron-Evaluated-By: policy-engine           # Which service decided
```

### 4.4 Response Headers (Errors)

```
Content-Type: application/xml or application/json

X-Iron-Error-Code: access_denied              # Our error
X-Iron-Error-Reason: Deny policy matched      # Why denied
X-Iron-Policy-Matched: policy-003             # Which policy
X-Iron-Request-ID: req-12345678               # Trace ID
```

---

## 5. Error Model

### 5.1 Brazz-Nossel Errors (Before S3)

#### 401 Unauthorized
```json
{
  "Code": "Unauthorized",
  "Message": "No valid JWT provided",
  "RequestId": "req-12345678",
  "IronBucketError": {
    "reason": "missing_auth",
    "action": "Provide JWT in Authorization header",
    "docsUrl": "https://ironbucket.dev/docs/auth"
  }
}
```

#### 400 Bad Request
```json
{
  "Code": "BadRequest",
  "Message": "Invalid request format",
  "RequestId": "req-12345678",
  "IronBucketError": {
    "reason": "invalid_jwt",
    "details": "JWT signature invalid",
    "action": "Refresh JWT and retry"
  }
}
```

#### 403 Forbidden (Policy Denied)
```json
{
  "Code": "AccessDenied",
  "Message": "Your request was denied by policy",
  "RequestId": "req-12345678",
  "IronBucketError": {
    "reason": "policy_denied",
    "matchedPolicies": [],
    "deniedPolicies": ["policy-003"],
    "policy_reason": "Deny policy 'deny-delete-prod' prevents deletion on prod-* buckets",
    "action": "Contact your admin or request policy change",
    "policyEngine": {
      "decision": "Deny",
      "evaluatedAt": "2025-01-15T14:30:00.123Z",
      "evaluationTime": 45.3
    }
  }
}
```

#### 500 Internal Server Error (Policy Engine Down)
```json
{
  "Code": "InternalError",
  "Message": "Policy engine is unavailable",
  "RequestId": "req-12345678",
  "IronBucketError": {
    "reason": "policy_engine_unavailable",
    "action": "Wait and retry; contact ops if persists",
    "fallbackBehavior": "DENY (fail-closed)"
  }
}
```

### 5.2 S3 Backend Errors (Passed Through)

If S3 returns an error, Brazz-Nossel proxies it back with added context:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Error>
  <Code>NoSuchKey</Code>
  <Message>The specified key does not exist.</Message>
  <Key>missing-file.txt</Key>
  <BucketName>my-bucket</BucketName>
  <RequestId>TX123456789</RequestId>
  
  <!-- Added by Brazz-Nossel for traceability -->
  <IronBucketContext>
    <RequestId>req-12345678</RequestId>
    <UserId>alice@acme.com</UserId>
    <Tenant>acme-corp</Tenant>
    <Policy>Allowed</Policy>
  </IronBucketContext>
</Error>
```

---

## 6. Tenant Isolation

### 6.1 Tenant Routing

```
Request path: /bucket/key
             │
             ├─ Extract: X-Iron-Tenant header (from Claimspindel)
             │
             ├─ Validate: Matches JWT tenant claim
             │
             ├─ Route to: S3 backend for this tenant
             │            (e.g., minio-tenant-acme-corp)
             │
             └─ Enforce: No cross-tenant bucket access
```

### 6.2 Bucket Naming Convention

Option 1: Explicit tenant prefix
```
acme-corp:data-bucket       → /acme-corp-data-bucket
acme-corp:logs-bucket       → /acme-corp-logs-bucket
```

Option 2: Namespace isolation
```
/acme-corp/bucket-1/key     → MinIO namespace acme-corp/bucket-1
/other-corp/bucket-1/key    → MinIO namespace other-corp/bucket-1
```

### 6.3 Tenant Mismatch

If client requests bucket from wrong tenant:

```
Request: GET /bucket/key
X-Iron-Tenant: acme-corp    (from Claimspindel)

JWT claims:
  tenant: other-corp         (from identity)

Result: 403 Forbidden
Response:
{
  "Code": "Forbidden",
  "Message": "Tenant mismatch",
  "IronBucketError": {
    "reason": "tenant_isolation",
    "expectedTenant": "other-corp",
    "requestTenant": "acme-corp"
  }
}
```

---

## 7. Backend Adapter Interface

### 7.1 Backend Types

Brazz-Nossel MUST support:

1. **MinIO** (primary - S3-compatible)
2. **AWS S3** (full S3 API)
3. **Ceph RGW** (S3-compatible)
4. **Wasabi** (S3-compatible)
5. **Backblaze B2** (S3-compatible API)
6. **Local filesystem** (dev/test only)

### 7.2 Backend Configuration

```yaml
ironbucket:
  s3proxy:
    default_backend: "minio"
    
    backends:
      minio:
        type: "minio"
        endpoint: "http://minio:9000"
        access_key: "${MINIO_ACCESS_KEY}"
        secret_key: "${MINIO_SECRET_KEY}"
        region: "us-east-1"
        tls_verify: false              # Dev only
        connect_timeout: 5s
        read_timeout: 30s
      
      aws_s3:
        type: "aws-s3"
        region: "us-east-1"
        access_key_id: "${AWS_ACCESS_KEY_ID}"
        secret_access_key: "${AWS_SECRET_ACCESS_KEY}"
        # Optional: assume_role_arn
      
      ceph_rgw:
        type: "ceph-rgw"
        endpoint: "http://ceph-rgw:7480"
        access_key: "${CEPH_ACCESS_KEY}"
        secret_key: "${CEPH_SECRET_KEY}"
        region: "default"
    
    # Routing: Select backend by tenant/bucket
    tenant_routing:
      acme-corp: "minio"
      other-corp: "aws_s3"
      default: "minio"
```

### 7.3 Backend Adapter Interface (TypeScript)

```typescript
interface S3Backend {
  // Connection
  connect(): Promise<void>;
  disconnect(): Promise<void>;
  healthCheck(): Promise<boolean>;
  
  // Object Operations
  getObject(
    bucket: string,
    key: string,
    options?: GetObjectOptions
  ): Promise<S3Object>;
  
  putObject(
    bucket: string,
    key: string,
    body: Stream | Buffer,
    options?: PutObjectOptions
  ): Promise<S3Response>;
  
  deleteObject(
    bucket: string,
    key: string,
    options?: DeleteObjectOptions
  ): Promise<S3Response>;
  
  listObjects(
    bucket: string,
    prefix?: string,
    options?: ListObjectsOptions
  ): Promise<S3Object[]>;
  
  // Bucket Operations
  headBucket(bucket: string): Promise<S3Response>;
  getBucketLocation(bucket: string): Promise<string>;
  getBucketVersioning(bucket: string): Promise<VersioningConfig>;
  
  // Metadata
  headObject(bucket: string, key: string): Promise<ObjectMetadata>;
}
```

---

## 8. Caching Strategy

### 8.1 What to Cache

```
┌─ JWT validation (cached by Sentinel-Gear) ─┐
├─ Token valid until: expiration             │
├─ Cache key: jwt_signature_hash             │
└─ Hit rate: 95%+ (same user making requests)│

┌─ Normalized Identity (cached by Brazz) ────┐
├─ Valid until: token expiration             │
├─ Cache key: userId + tenant + issuer       │
└─ Hit rate: 80%+ (typical session)          │

┌─ Policy evaluation (Brazz local cache)     ─┐
├─ DO NOT cache individual decisions         │
├─ Policy itself changes (GitOps)            │
├─ Conditions are dynamic (IP, time)         │
└─ Always call Policy Engine (fast)          │

┌─ S3 Metadata (optional)                    ─┐
├─ Cache bucket location (rarely changes)    │
├─ Cache versioning config                   │
├─ TTL: 1 hour                               │
└─ Invalidate on explicit requests           │
```

### 8.2 Cache Invalidation

```
JWKS Update (hourly):
  └─ Invalidate JWT cache
  └─ Fetch fresh keys from IDP

Token Expiration:
  └─ Auto-expire from cache (based on exp claim)

Policy Change (on git push):
  └─ Policy Engine loads new policies
  └─ Brazz refetches (no local caching)

Manual Invalidation (if needed):
  └─ Admin API: POST /admin/cache/clear
```

---

## 9. Streaming & Backpressure

### 9.1 Large File Handling

For files > 100MB:

```
GET /bucket/large-file.iso (1GB file)
    │
    ├─ Authenticate & authorize (< 100ms)
    │
    ├─ S3 backend streams file chunks
    │
    ├─ Brazz relays chunks to client
    │   (non-blocking, backpressure-aware)
    │
    ├─ Client receives streaming response
    │   (no full buffering in memory)
    │
    └─ Log complete transaction
```

### 9.2 Backpressure Handling

```typescript
interface StreamOptions {
  // Pause reading if buffer > 64MB
  highWaterMark: 64 * 1024 * 1024;
  
  // Auto-close on timeout
  streamTimeout: 5 * 60 * 1000;  // 5 minutes
  
  // Handle socket drain events
  onDrain: () => void;
}
```

---

## 10. Audit Logging

### 10.1 Audit Log Schema

```json
{
  "timestamp": "2025-01-15T14:30:00.123Z",
  "requestId": "req-12345678",
  "auditType": "S3ProxyRequest",
  "s3Operation": {
    "action": "s3:GetObject",
    "bucket": "dev-bucket",
    "key": "data/file.csv",
    "method": "GET",
    "path": "/dev-bucket/data/file.csv"
  },
  "identity": {
    "userId": "alice@acme.com",
    "username": "alice",
    "tenant": "acme-corp",
    "roles": ["dev", "viewer"]
  },
  "context": {
    "sourceIp": "10.0.1.1",
    "userAgent": "aws-cli/2.13.0"
  },
  "authorization": {
    "decision": "Allow",
    "evaluationTime": 45.3,
    "matchedPolicies": ["policy-001"],
    "deniedPolicies": []
  },
  "response": {
    "statusCode": 200,
    "contentLength": 1024,
    "responseTime": 150
  }
}
```

### 10.2 Log Destination Options

```yaml
ironbucket:
  audit:
    destinations:
      - type: "syslog"
        endpoint: "syslog.acme.com:514"
        protocol: "udp"
      
      - type: "elasticsearch"
        endpoint: "https://logs.acme.com"
        index: "ironbucket-audit"
      
      - type: "file"
        path: "/var/log/ironbucket/audit.log"
        rotation: "daily"
        retention_days: 90
    
    format: "json"
    sensitive_redaction: true  # Don't log full JWT
```

---

## 11. Performance & Scalability

### 11.1 Scaling Dimensions

```
1. Horizontal Scaling (multiple Brazz instances)
   ├─ Behind load balancer
   ├─ Stateless (all state external)
   ├─ Share JWT cache via Redis (optional)
   └─ Can scale to 1000s req/sec

2. S3 Backend Scalability
   ├─ MinIO: Can handle 100s Gbps
   ├─ AWS S3: Unlimited
   ├─ Ceph RGW: Limited by cluster size
   └─ Brazz doesn't limit backend

3. Policy Engine Scalability
   ├─ Must handle policy eval requests
   ├─ Cache policies in memory
   ├─ Typical: 1000s eval/sec per instance
   └─ Can scale horizontally
```

### 11.2 Resource Limits

```yaml
ironbucket:
  s3proxy:
    # Connection limits
    max_concurrent_requests: 1000
    max_idle_timeout: 60s
    
    # Payload limits
    max_request_size: 5GB
    max_object_size: 5TB              # MinIO default
    
    # Buffer limits
    buffer_size: 64 * 1024 * 1024     # 64MB
    
    # Timeout limits
    request_timeout: 30 * 60s          # 30 minutes
    policy_eval_timeout: 5s
    s3_operation_timeout: 30 * 60s
    
    # Rate limiting (per tenant)
    rate_limit:
      requests_per_minute: 10000
      bytes_per_second: "10 Gbps"
```

---

## 12. Testing Requirements

Every proxy implementation must pass:

1. ✅ GET object returns correct content
2. ✅ PUT object saves to backend
3. ✅ DELETE removes object
4. ✅ ListBucket returns objects
5. ✅ Authentication required (no JWT = 401)
6. ✅ Policy enforcement (policy denied = 403)
7. ✅ Large files streamed without buffering
8. ✅ Backpressure handled correctly
9. ✅ Audit logs generated for all operations
10. ✅ Tenant isolation enforced
11. ✅ Headers added/removed correctly
12. ✅ Error responses formatted correctly
13. ✅ S3 errors passed through cleanly
14. ✅ Performance < 500ms (excluding backend)
15. ✅ Handles concurrent requests

---

## 13. Backward Compatibility

Brazz-Nossel MUST be a drop-in replacement for S3:

```bash
# Before (direct S3)
aws s3 cp file.txt s3://bucket/file.txt

# After (through Brazz)
aws s3 --endpoint-url http://brazz:8080 \
       cp file.txt s3://bucket/file.txt

# No code changes required!
```

---

## 14. Future Enhancements

- [ ] Multipart upload support
- [ ] Object tagging support
- [ ] Bucket lifecycle policies
- [ ] Object encryption (SSE-S3, SSE-C)
- [ ] Request signing (AWS Signature v4)
- [ ] Pre-signed URLs with policy enforcement
- [ ] CORS support
- [ ] Bandwidth throttling per tenant
- [ ] Request deduplication (idempotency)
- [ ] WebSocket streaming API
