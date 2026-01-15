# 🏗️ IronBucket Architecture Overview

**Target Audience**: Architects, Contributors, Operators  
**Read Time**: 30 minutes  
**Status**: ✅ Complete  
**Last Updated**: January 15, 2026

---

## Executive Summary

IronBucket is a **policy-driven, object storage security system** that sits between applications and S3-compatible storage backends. It enforces fine-grained access controls and audit logging through a declarative policy system.

**Core Value**: Transform raw S3 access into audited, policy-controlled storage with enterprise security features.

---

## 🎯 System Vision

```
User/Application
        ↓
    [API Gateway]
        ↓
    [IronBucket]
   /          \
  ↓            ↓
[Policy        [S3
 Engine]       Proxy]
  ↓            ↓
  └────┬───────┘
       ↓
   S3 Storage
   (AWS, MinIO, etc.)
```

**Key Principle**: Every request → Policy evaluation → Decision → Action → Audit log

---

## 🏢 Layered Architecture

### Layer 1: API Entry Point
**Components**: Zuul API Gateway (Sentinel-Gear)

```
┌─────────────────────────────────┐
│    Zuul API Gateway             │
│   (Sentinel-Gear)               │
│                                 │
│  • Route management             │
│  • Authentication delegation    │
│  • Request/response logging     │
│  • Rate limiting                │
└────────────────┬────────────────┘
                 ↓
         (Routes to services)
```

**Responsibility**:
- Route incoming HTTP requests to appropriate services
- Early authentication validation
- Request/response logging
- Rate limiting and circuit breakers

**Key Config**: [docker-compose-steel-hammer.yml](steel-hammer/docker-compose-steel-hammer.yml)

---

### Layer 2: Security & Access Control
**Components**: Keycloak, Identity Model, Policy Engine

#### 2A. Identity Management
**Keycloak Role**:
- OAuth2/OIDC token issuance
- User/group/role management
- Multi-realm support
- Token validation

**Identity Model** (`docs/identity-model.md`):
```
┌─────────────────────────────────┐
│     Identity Token              │
│  ┌─────────────────────────┐    │
│  │ Subject (user/service)  │    │
│  │ Scopes (permissions)    │    │
│  │ Groups (memberships)    │    │
│  │ Custom Claims           │    │
│  └─────────────────────────┘    │
└─────────────────────────────────┘
```

#### 2B. Policy Engine
**Brazz-Nossel Service**: Core policy evaluation

```
Request + Identity
        ↓
   [Policy Engine]
   /   /   \   \
  ↓   ↓     ↓   ↓
[Load] [Parse] [Evaluate] [Return Decision]
```

**Supported Policies**:
- **CRUD Operations**: Create, Read, Update, Delete
- **Resource Matching**: Exact path, wildcard, regex
- **Conditions**: Time-based, IP-based, rate-limit-based
- **Effects**: Allow, Deny, Audit-only
- **Actions**: Block, Monitor, Require-MFA, Require-Approval

---

### Layer 3: Storage & Compliance
**Components**: S3 Proxy (Buzzle-Vane), Audit Log (Claimspindel), Storage Gateway

#### 3A. S3 Proxy
**Buzzle-Vane Service**: Proxies S3 operations

```
Policy ✓ Decision
        ↓
   [S3 Proxy]
   /   /   \   \
  ↓   ↓     ↓   ↓
[HEAD] [GET] [PUT] [DELETE]
        ↓
   [MinIO/AWS S3]
```

**Operations Supported**:
- GetObject - Download files
- PutObject - Upload files
- DeleteObject - Remove files
- ListObjects - Enumerate buckets
- HeadObject - Check object existence
- All operations with full request/response transformation

#### 3B. Audit & Compliance
**Claimspindel Service**: Centralized audit logging

```
Every Operation
        ↓
   [Audit Logger]
   /   \   /   \
  ↓     ↓ ↓     ↓
[User] [Action] [Resource] [Result]
        ↓
   [Postgres]
   (Immutable audit trail)
```

**Logged Information**:
- WHO: User/service principal
- WHAT: Operation (GET, PUT, DELETE, etc.)
- WHERE: Resource path
- WHEN: Timestamp (UTC)
- WHY: Policy rule evaluated
- RESULT: Allow/Deny/Modified

---

### Layer 4: Data Persistence
**Components**: PostgreSQL, MinIO/S3

#### Database Schema

```
┌─────────────────────────┐
│  PostgreSQL             │
├─────────────────────────┤
│ • Audit Log Table       │
│ • Policy Store          │
│ • Identity Cache        │
│ • Configuration         │
└─────────────────────────┘
```

#### Object Storage
```
┌─────────────────────────┐
│ S3-Compatible Storage   │
├─────────────────────────┤
│ • MinIO (local dev)     │
│ • AWS S3 (production)   │
│ • Multi-bucket support  │
└─────────────────────────┘
```

---

## 🔄 Request Flow - Complete Walkthrough

### Request Lifecycle (Step-by-Step)

```
1. CLIENT INITIATES REQUEST
   Client → GET /bucket/file.txt
             (with Bearer token)

2. API GATEWAY (Sentinel-Gear)
   ↓
   Zuul routing → /bucket/* → Buzzle-Vane service
   Rate limiting check

3. S3 PROXY RECEIVES REQUEST (Buzzle-Vane)
   ↓
   Extract:
   • User from Bearer token
   • Resource: bucket/file.txt
   • Operation: GET (read)

4. POLICY EVALUATION (Brazz-Nossel)
   ↓
   Load policies for user
   Match against resource path
   Evaluate conditions:
     - User in allowed group? ✓
     - Time within allowed window? ✓
     - IP whitelist check? ✓
   → ALLOW / DENY decision

5. DECISION ROUTING
   ↓
   IF ALLOWED:
     → Continue to Storage
   IF DENIED:
     → Return 403 Forbidden
   IF AUDIT_ONLY:
     → Log but continue

6. STORAGE OPERATION (MinIO/S3)
   ↓
   S3 Proxy executes:
   GET /bucket/file.txt
   ← Returns object + metadata

7. AUDIT LOGGING (Claimspindel)
   ↓
   Async log to PostgreSQL:
   {
     timestamp: 2026-01-15T10:30:00Z,
     user: alice@example.com,
     action: GET,
     resource: bucket/file.txt,
     result: SUCCESS,
     policy: read-public-objects,
     latency: 125ms
   }

8. RESPONSE TO CLIENT
   ↓
   Client ← 200 OK + file contents
```

---

## 🔐 Security Model

### Trust Boundaries

```
┌────────────────────────────────────────────────────┐
│         EXTERNAL (Untrusted)                       │
│    Client Applications & Users                     │
└──────────────────┬─────────────────────────────────┘
                   │ TLS + Bearer Token
                   ↓
┌────────────────────────────────────────────────────┐
│     GATEWAY LAYER (Partial Trust)                  │
│     Zuul validates tokens, rate limiting          │
└──────────────────┬─────────────────────────────────┘
                   │ Service mesh (mTLS)
                   ↓
┌────────────────────────────────────────────────────┐
│    CORE SERVICES (Internal Trusted)                │
│  • Brazz-Nossel (Policy Engine)                    │
│  • Buzzle-Vane (S3 Proxy)                          │
│  • Claimspindel (Audit)                            │
└──────────────────┬─────────────────────────────────┘
                   │ Encrypted connections
                   ↓
┌────────────────────────────────────────────────────┐
│    STORAGE LAYER (Protected)                       │
│  • PostgreSQL (credentials in env)                 │
│  • MinIO/S3 (separate access keys)                 │
└────────────────────────────────────────────────────┘
```

### Authentication & Authorization

| Component | Method | Token Type | Validation |
|-----------|--------|-----------|------------|
| API Gateway | OAuth2 | JWT (Keycloak) | Signature + expiry |
| S3 Proxy | Bearer token | JWT | From Keycloak |
| Policy Engine | Identity object | Structured | Cache-based |
| Database | Credentials | PostgreSQL auth | Connection string |
| Storage | AWS/MinIO keys | Access keys | Separate from app |

---

## 📊 Service Dependencies

### Service Graph

```
Sentinel-Gear (API Gateway)
    ↓
    ├→ Brazz-Nossel (Policy Engine)
    │   ↓
    │   └→ Keycloak (Identity)
    │
    ├→ Buzzle-Vane (S3 Proxy)
    │   ↓
    │   ├→ MinIO/S3 Storage
    │   └→ Keycloak (Identity)
    │
    └→ Claimspindel (Audit Logger)
        ↓
        └→ PostgreSQL (Audit Store)

Supporting Services:
    ├→ Keycloak (Identity Provider)
    │   ↓
    │   └→ PostgreSQL
    │
    └→ Observability Stack (Optional)
        ├→ Prometheus (Metrics)
        ├→ Grafana (Dashboards)
        ├→ Loki (Logs)
        └→ Tempo (Traces)
```

### Startup Dependencies

**Correct startup order:**

1. **PostgreSQL** (foundation - needed by all)
2. **MinIO** (storage backend)
3. **Keycloak** (identity provider)
4. **Brazz-Nossel** (policy engine)
5. **Buzzle-Vane** (S3 proxy - depends on policy engine)
6. **Claimspindel** (audit logger)
7. **Sentinel-Gear** (API gateway - routes to all others)

See: [STARTUP-ORDER.md](steel-hammer/STARTUP-ORDER.md)

---

## 🎯 Design Patterns & Principles

### 1. **Policy-First Access Control**
Every request must pass policy evaluation before reaching storage.

```
Request → [Policy Gate] → ALLOW/DENY → Storage
```

### 2. **Immutable Audit Trail**
All operations logged to PostgreSQL with no update/delete capability.

```
Log Entry
├─ Insert: ✅ Yes
├─ Update: ❌ No
└─ Delete: ❌ No
```

### 3. **Microservice Separation**
Each service has single responsibility:

| Service | Responsibility |
|---------|---|
| Brazz-Nossel | Evaluate policies only |
| Buzzle-Vane | Execute S3 operations only |
| Claimspindel | Log operations only |
| Sentinel-Gear | Route requests only |

### 4. **Event-Driven Audit**
Audit logging is asynchronous to avoid impacting request latency.

```
Request → Storage → Response ↓ (in parallel)
                    ↓
                Audit Logger
```

### 5. **Identity Caching**
Keycloak tokens cached to reduce authentication latency.

```
Request 1 → Keycloak → Cache ← Request 2 (instant)
```

---

## 📈 Scalability Architecture

### Horizontal Scaling

**Stateless Services** (can scale freely):
- Sentinel-Gear (API Gateway)
- Brazz-Nossel (Policy Engine)
- Buzzle-Vane (S3 Proxy)

```
  Load Balancer
  ↙   ↓   ↘
[Pod1] [Pod2] [Pod3]  (replicas of stateless services)
```

**Stateful Services** (scale carefully):
- PostgreSQL (primary + read replicas)
- Keycloak (shared state via DB)
- MinIO (distributed minio cluster)

### Database Scaling

```
PostgreSQL Primary (writes)
    ↙   ↘
[Read Replica 1] [Read Replica 2]
(read-only, used for audit queries)
```

### Load Distribution

- **Requests**: Round-robin across service replicas
- **Database**: Write to primary, read from replicas
- **Storage**: MinIO distributed mode or AWS S3 (unlimited)

---

## 🔍 Observability Architecture

### Metrics Collection

```
Services (Prometheus exporters)
    ↓
[Prometheus] (scrapes every 30s)
    ↓
[Grafana] (visualizes)
```

**Metrics by service:**
- API Gateway: Request rate, latency, error rate
- Policy Engine: Evaluation time, cache hit ratio
- S3 Proxy: S3 operation latency, bandwidth
- Audit Logger: Log throughput, database latency

### Log Aggregation

```
Services (stdout/stderr)
    ↓
[Loki] (log aggregation)
    ↓
[Grafana] (query & visualize)
```

### Distributed Tracing

```
Services (emit spans)
    ↓
[Tempo] (trace collection)
    ↓
[Grafana] (trace visualization)
```

**Trace flow:**
```
Request #123
├─ Span: API Gateway (2ms)
├─ Span: Policy Engine (15ms)
│  ├─ Span: Cache lookup (1ms)
│  └─ Span: Policy evaluation (14ms)
├─ Span: S3 Proxy (50ms)
│  └─ Span: S3 operation (48ms)
└─ Span: Audit logging (3ms)
   Total: 70ms
```

---

## 🚀 Technology Stack

### Core Application

| Layer | Technology | Why |
|-------|-----------|-----|
| Language | Java 21 | Type-safe, JVM ecosystem, performance |
| Framework | Spring Boot | Microservices, minimal config |
| API | REST/HTTP | Standard, widely supported |
| Service mesh | Kubernetes (future) | Service discovery, mTLS, observability |

### Data Persistence

| Component | Technology | Why |
|-----------|-----------|-----|
| Transactional DB | PostgreSQL 15 | ACID, JSON, scalable |
| Object Storage | S3-compatible (MinIO) | Standard protocol, portable |
| Identity | Keycloak | OAuth2/OIDC, multi-realm |

### Observability

| Component | Technology | Why |
|-----------|-----------|-----|
| Metrics | Prometheus | Standard, time-series optimized |
| Visualization | Grafana | Powerful dashboards |
| Logs | Loki | Efficient log storage |
| Traces | Tempo | Cost-effective tracing |

### Infrastructure

| Component | Technology | Why |
|-----------|-----------|-----|
| Containerization | Docker | Standard container runtime |
| Orchestration | Docker Compose (dev), Kubernetes (prod) | Production-grade |
| CI/CD | GitHub Actions | Native to GitHub, free tier |

---

## 🔄 Data Flow Examples

### Example 1: User Uploads File

```
Client: PUT /bucket/reports/2024-data.xlsx (Bearer token)
         ↓
    Sentinel-Gear
      (routing)
         ↓
    Buzzle-Vane
      (S3 proxy)
         ↓
    Extract user from token
    Resource: bucket/reports/2024-data.xlsx
    Operation: PUT
         ↓
    Brazz-Nossel (Policy Engine)
         ↓
    Load policies for user
    "alice can write to reports/ in office hours"
    Check: Is it 9-17? In building network? →  YES
         ↓
    ALLOW → Proceed to storage
         ↓
    MinIO receives PUT
    Stores object + metadata
         ↓
    Claimspindel (async)
    Log: {
      timestamp: 2026-01-15T14:30:00Z,
      user: alice@company.com,
      action: PutObject,
      resource: bucket/reports/2024-data.xlsx,
      size: 2.5MB,
      policy_matched: reports-write,
      result: SUCCESS
    }
         ↓
    Response: 200 OK + object ETag
```

### Example 2: Unauthorized Access Attempt

```
Client: GET /bucket/confidential/salaries.csv (wrong user token)
         ↓
    Sentinel-Gear (validates token is valid)
         ↓
    Buzzle-Vane
         ↓
    User: bob@company.com (from token)
    Resource: bucket/confidential/salaries.csv
    Operation: GET
         ↓
    Brazz-Nossel
         ↓
    Load policies for bob
    "bob can read from public/"
    Check: Is resource in public/? → NO
         ↓
    DENY
         ↓
    Claimspindel (async)
    Log: {
      timestamp: 2026-01-15T14:30:15Z,
      user: bob@company.com,
      action: GetObject,
      resource: bucket/confidential/salaries.csv,
      policy_matched: none,
      result: DENIED,
      reason: resource-not-in-allowed-paths
    }
         ↓
    Response: 403 Forbidden
```

---

## 🔧 Configuration & Tuning

### Service Configuration

**Brazz-Nossel** (Policy Engine):
- `policy.cache.ttl`: How long to cache policy evaluations (default: 5min)
- `policy.evaluation.timeout`: Max time for evaluation (default: 1s)

**Buzzle-Vane** (S3 Proxy):
- `s3.connection.timeout`: Connection timeout (default: 30s)
- `s3.socket.timeout`: Socket timeout (default: 60s)
- `s3.max.retries`: Retry attempts (default: 3)

**Claimspindel** (Audit):
- `audit.batch.size`: How many logs to batch (default: 100)
- `audit.batch.timeout`: Max time before flushing (default: 5s)

See: [docs/s3-proxy-contract.md](docs/s3-proxy-contract.md) for full configuration reference.

---

## 📋 Component Responsibilities Summary

| Component | Primary Duty | Secondary Duty |
|-----------|---|---|
| Sentinel-Gear | Route to services | Rate limiting |
| Brazz-Nossel | Evaluate policies | Identity caching |
| Buzzle-Vane | Execute S3 ops | Transform requests |
| Claimspindel | Log operations | Audit reporting |
| Keycloak | Issue tokens | User management |
| PostgreSQL | Store audit logs | Store policies |
| MinIO/S3 | Store objects | Handle multipart uploads |

---

## 🎓 Learning Path

1. **Start Here**: This document (you are here!)
2. **Understand Policies**: [policy-schema.md](docs/policy-schema.md)
3. **See the Contract**: [identity-flow.md](docs/identity-flow.md)
4. **Run It Locally**: [START.md](START.md)
5. **Deploy It**: [DEPLOYMENT-GUIDE.md](docs/DEPLOYMENT-GUIDE.md)
6. **Test It**: [test-suite-phase2.md](docs/test-suite-phase2.md)
7. **Monitor It**: [steel-hammer/LGTM-SETUP-GUIDE.md](steel-hammer/LGTM-SETUP-GUIDE.md)

---

## 🔗 Related Documents

| Document | Purpose |
|----------|---------|
| [policy-schema.md](docs/policy-schema.md) | How to write policies |
| [identity-model.md](docs/identity-model.md) | Identity token structure |
| [s3-proxy-contract.md](docs/s3-proxy-contract.md) | S3 proxy API specification |
| [identity-flow.md](docs/identity-flow.md) | Authentication sequence |
| [DEPLOYMENT-GUIDE.md](docs/DEPLOYMENT-GUIDE.md) | Deployment instructions |
| [COMMUNITY-ROADMAP.md](COMMUNITY-ROADMAP.md) | Future architecture plans |

---

## ✅ Architecture Review Checklist

- [x] Core layers identified and documented
- [x] Request flow walkthrough complete
- [x] Security model defined
- [x] Service dependencies mapped
- [x] Design patterns documented
- [x] Scalability strategy outlined
- [x] Technology stack justified
- [x] Observability architecture defined
- [x] Data flow examples provided
- [x] Configuration reference linked

---

**Architecture Version**: 1.0  
**Last Reviewed**: January 15, 2026  
**Next Review**: April 15, 2026

Status: ✅ COMPLETE & VERIFIED
