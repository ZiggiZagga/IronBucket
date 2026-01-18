# IronBucket Roadmap: Journey to Graphite Forge

**Last Updated:** January 18, 2026  
**Current Phase:** Phase 3 - Operator CLI & Production Hardening  
**Overall Status:** 🟢 **Production-Ready** (with roadmap for enterprise features)

---

## Executive Vision

IronBucket is evolving from a zero-trust S3 proxy into **Graphite Forge**—an enterprise-grade identity-aware storage platform that unifies multi-tenant, policy-driven access control across heterogeneous backends (S3, GCS, Azure Blob, local filesystem).

**North Star Metrics:**
- 100% policy compliance enforcement (deny-overrides-allow semantics)
- Sub-100ms latency for access decisions (cached policies)
- 99.99% availability for metadata operations
- 100% audit trail completeness (zero access without record)
- Zero-trust architecture validation on every request

---

## Phase Timeline

### ✅ Phase 1: Foundation (Complete - Jan 2026)
**Goal:** Establish zero-trust S3 proxy with JWT auth and policy-based routing

**Deliverables:**
- ✅ Sentinel-Gear (OIDC JWT validator, claim normalization)
- ✅ Claimspindel (policy router, deny-overrides-allow semantics)
- ✅ Brazz-Nossel (S3 proxy, request transformation)
- ✅ Buzzle-Vane (Eureka service discovery)
- ✅ PostgreSQL audit logging (transactional integrity)
- ✅ 231+ unit tests (100% passing)
- ✅ Docker Compose local deployment
- ✅ SLSA Build Level 3 CI/CD pipeline
- ✅ Keycloak OIDC integration (multi-tenant identity)
- ✅ Comprehensive architecture documentation

**Status:** 🟢 Production-Ready

---

### ✅ Phase 2: Testing & Hardening (Complete - Jan 2026)
**Goal:** Governance, integrity, resilience validation + operator tooling

**Deliverables:**
- ✅ GovernanceIntegrityResilienceTest (50+ scenarios)
  - Policy bypass prevention
  - Metadata drift detection
  - Multipart/streaming safety
  - Migration/cutover validation
  - API semantic parity
  - Operational HA fragility
  - Security/observability completeness
- ✅ Graphite Admin Shell (Spring Shell 3.2.4 + Spring Boot 4.0.1)
  - 6 operational commands (reconcile, backfill, orphan-cleanup, inspect, script-runner, adapter-lister)
  - RBAC with force acknowledgement gates
  - OpenTelemetry tracing integration
  - Structured audit logging
  - Tab completion for catalog items
  - 15/15 tests passing
- ✅ E2E test integration (Alice & Bob multi-tenant scenario)
- ✅ OpenTelemetry tracing infrastructure (OTLP exporter)
- ✅ Java 25 upgrade across all services
- ✅ Production-ready deployment manifests (k8s, Docker Compose)
- ✅ Network isolation policies (Kubernetes NetworkPolicies)
- ✅ Observability stack (Prometheus, Loki, Tempo, Grafana)

**Status:** 🟢 Production-Ready

---

### 🔵 Phase 3: Enterprise Adapter Ecosystem (In Progress - Q1 2026)
**Goal:** Multi-backend support (GCS, Azure Blob, local FS) with unified policy enforcement

**Deliverables (Target: Feb 2026):**

#### 3.1 GCS Backend Adapter
- Google Cloud Storage request translation layer
- Service account authentication
- Bucket/object permissions mapping to Claimspindel policies
- Unit tests (30+ scenarios)
- Integration tests with public GCS
- Documentation: GCS-to-Policy mapping table

#### 3.2 Azure Blob Backend Adapter
- Azure Blob Storage request translation layer
- Managed identity / SAS token support
- Container/blob permissions mapping
- Unit tests (30+ scenarios)
- Integration tests with public Azure Blob
- Documentation: Azure-to-Policy mapping table

#### 3.3 Local Filesystem Backend Adapter
- POSIX-compatible filesystem backend
- File permission (umask, ACLs) mapping to policies
- Symbolic link handling (security boundary)
- Quota enforcement per tenant
- Unit tests (25+ scenarios)
- Documentation: FS-to-Policy mapping table

#### 3.4 Adapter Registry & Discovery
- Service mesh integration (Istio/Linkerd)
- Dynamic backend selection per bucket
- Fallback/failover routing
- Unit tests (20+ scenarios)
- Integration tests with multi-adapter deployment
- Observability: adapter selection metrics

#### 3.5 Policy Enforcement Framework
- Unified policy evaluation across all adapters
- Adapter-specific capability checks (e.g., versioning not supported on local FS)
- Deny list vs. allow list semantics validation per adapter
- Audit logging for adapter-specific operations
- Performance: policy cache invalidation strategy

**Success Criteria:**
- All three adapters pass integration tests
- Single policy language covers all backends
- Latency increase <5ms for adapter selection
- 100% audit trail coverage across adapters

---

### 🔵 Phase 4: Advanced Governance & Compliance (Q2 2026)
**Goal:** Policy versioning, auditability, compliance automation

**Deliverables:**

#### 4.1 Policy Versioning & Rollback
- Version control for policy definitions (git-backed)
- Promotion pipeline: dev → staging → prod
- Automatic rollback on policy violation detection
- Policy diff viewer (UI + CLI)
- Unit tests (20+ scenarios)

#### 4.2 Compliance Frameworks
- PCI-DSS compliance validation rules
- HIPAA audit requirement automation
- SOC 2 Type II evidence collection
- Kubernetes Pod Security Standards validation
- Unit tests (25+ scenarios)

#### 4.3 Data Residency & Geo-Fencing
- Policy constraint: bucket location must be in {region-list}
- Prevent cross-region data movement
- Audit trail for geo-fence violations
- Per-tenant residency preferences
- Unit tests (15+ scenarios)

#### 4.4 Encryption & Key Management
- Transparent encryption-at-rest (per adapter)
- Bring-Your-Own-Key (BYOK) support
- Key rotation policies
- Integration with HashiCorp Vault
- Unit tests (20+ scenarios)

**Success Criteria:**
- Enterprise audit trails 100% complete
- Compliance checks run pre-deployment
- Zero data residency violations in test suite

---

### 🔵 Phase 5: Observability & Performance (Q2-Q3 2026)
**Goal:** Production monitoring, profiling, optimization

**Deliverables:**

#### 5.1 Metrics & Dashboards
- Prometheus metrics for all microservices
- Custom dashboards (Grafana):
  - Policy evaluation latency (p50, p95, p99)
  - Adapter throughput (requests/sec per backend)
  - Cache hit rates (policy, credential, object metadata)
  - Multi-tenant resource usage
- SLO/SLA monitoring (99.9% uptime, <100ms decision latency)

#### 5.2 Distributed Tracing
- OpenTelemetry spans for entire request lifecycle
- Trace sampling strategy (adaptive)
- Correlation IDs across services
- Integration with Tempo (traces storage)

#### 5.3 Logging Aggregation
- Structured JSON logs to Loki
- Log queries for compliance audits
- Alert rules (e.g., failed policy evaluation)
- Retention policy per log type

#### 5.4 Performance Optimization
- Policy cache tuning (TTL, eviction strategy)
- Connection pooling for all backends
- Circuit breaker configuration
- Load testing suite (concurrent tenants, large payloads)

**Success Criteria:**
- Request latency <100ms (p99) end-to-end
- Cache hit rate >85% for policies
- Zero resource leaks (memory, connections)

---

### 🔵 Phase 6: Advanced Features (Q3-Q4 2026)
**Goal:** Differential access control, real-time analytics, predictive enforcement

**Deliverables:**

#### 6.1 Time-Based Access Control
- Policy rules: valid only between HH:MM and HH:MM
- Daylight-saving time handling
- Geo-location based time zones
- Unit tests (20+ scenarios)

#### 6.2 Risk-Based Access Control (RBAC → Adaptive)
- Real-time risk scoring (velocity, location, device)
- Step-up authentication for high-risk requests
- Anomaly detection (ML-based, offline training)
- Audit logs for risk decisions

#### 6.3 Data Access Analytics
- Query patterns per tenant
- Frequently accessed objects (heatmap)
- Dormant data detection (unused for >90 days)
- Cost attribution (compute + storage per tenant)
- Dashboard: analytics portal (read-only)

#### 6.4 Multi-Signature Workflows
- Require N out of M approvals for destructive ops
- Approval workflow integration (Slack, Teams, email)
- Audit trail for approval chain
- Timeout handling (auto-deny after 24h)

**Success Criteria:**
- Risk scoring adds <10ms latency
- ML anomaly detection integrated
- 100% multi-sig workflow coverage for admin ops

---

## Horizontal Initiatives (Continuous)

### Security Hardening
- [ ] Secrets rotation (credentials, JWT signing keys)
- [ ] Penetration testing (quarterly)
- [ ] Threat modeling updates (per release)
- [ ] OWASP Top 10 validation
- [ ] Dependency scanning (Dependabot, Snyk)

### Documentation
- [ ] API OpenAPI/Swagger specs (auto-generated)
- [ ] Policy language grammar (EBNF)
- [ ] Operator runbooks (troubleshooting)
- [ ] Architecture decision records (ADRs)
- [ ] Case studies (customer deployments)

### Developer Experience
- [ ] CLI improvements (shell completion, aliases)
- [ ] SDKs in Python, Go, Node.js
- [ ] Terraform/Helm providers
- [ ] IDE plugins (VS Code, IntelliJ)
- [ ] Example applications (file-sharing, data lake)

### Community & Adoption
- [ ] Open-source release (GitHub public)
- [ ] Docker Hub official images
- [ ] Helm chart for Kubernetes
- [ ] Community Slack/Discord
- [ ] Conference talks & blog posts

---

## Success Metrics by Phase

| Phase | Timeline | Key Metric | Target | Status |
|-------|----------|-----------|--------|--------|
| 1 | Jan 2026 | Tests Passing | 100% | ✅ 231/231 |
| 1 | Jan 2026 | Latency (p99) | <200ms | ✅ ~150ms |
| 2 | Jan 2026 | Governance Scenarios | 50+ | ✅ 50 tests |
| 2 | Jan 2026 | Admin Shell Commands | 6 | ✅ All implemented |
| 3 | Feb 2026 | Supported Backends | 3 | 🔵 In progress |
| 3 | Feb 2026 | Adapter Tests | 100+ | 🔵 In progress |
| 4 | Q2 2026 | Policy Versions | Tracked | 🔵 Planned |
| 5 | Q2-Q3 2026 | Cache Hit Rate | >85% | 🔵 Planned |
| 6 | Q3-Q4 2026 | Risk Score Latency | <10ms | 🔵 Planned |

---

## Dependencies & Blockers

### External
- [ ] GCS service account credentials (for Phase 3.2 integration tests)
- [ ] Azure Blob SAS token (for Phase 3.3 integration tests)
- [ ] Vault cluster (for Phase 4.4 BYOK)
- [ ] ML training dataset (historical access patterns for Phase 6.2)

### Internal
- [x] Java 25 upgrade (completed)
- [x] OTEL infrastructure (completed)
- [x] Admin Shell CLI (completed)
- [ ] Multi-tenant cache isolation (Phase 3.5)
- [ ] Policy cache invalidation strategy (Phase 3.5)

---

## Architecture Evolution

### Phase 1-2: Monolithic Proxy
```
Client → Brazz-Nossel → Sentinel-Gear → Claimspindel → MinIO
         (S3 Proxy)    (JWT Validator)  (Policy Engine)  (Storage)
```

### Phase 3: Multi-Backend Routing
```
                 ┌─── GCS Adapter
Client → Brazz-Nossel ┼─── Azure Blob Adapter
              ↓       └─── Local FS Adapter
         Claimspindel (unified policy evaluation)
```

### Phase 4-6: Full Enterprise Platform (Graphite Forge)
```
┌──────────────────────────────────────────────────────┐
│              Graphite Forge UI/CLI                    │
│  (Policy Management, Compliance, Analytics, Approvals)│
└──────────────────────────────────────────────────────┘
                          ↓
     ┌────────────────────┼────────────────────┐
     │                    │                    │
┌────────────┐   ┌─────────────────┐  ┌─────────────────┐
│ Time-Based │   │ Risk-Based      │  │ Data Analytics  │
│ Access     │   │ Access Control  │  │ Engine          │
└────┬───────┘   └────────┬────────┘  └────────┬────────┘
     │                    │                    │
     └────────────────────┼────────────────────┘
                          ↓
               ┌──────────────────────┐
               │ Policy & Compliance  │
               │ Engine (Claimspindel)│
               └──────────┬───────────┘
                          ↓
         ┌────────────────┼───────────────┐
         ↓                ↓               ↓
      S3/MinIO          GCS          Azure Blob
```

---

## Getting Started

### For Contributors
1. Review [ARCHITECTURE.md](docs/ARCHITECTURE.md) for system design
2. Check [Phase 3 Adapter Spec](docs/implementation/adapter-interface.md) (to be created)
3. Set up local development: `cd steel-hammer && docker-compose up`
4. Pick a task from Phase 3 above and submit a PR

### For Operators
1. Deploy Phase 2 components: [DEPLOYMENT.md](docs/DEPLOYMENT.md)
2. Configure policies: [policy-schema.md](docs/policy-schema.md)
3. Monitor via LGTM stack: [Grafana dashboards](docs/observability/)
4. Run admin shell: `java -jar graphite-admin-shell.jar`

### For Security Teams
1. Review network policies: [k8s-network-policies.yaml](docs/k8s-network-policies.yaml)
2. Validate audit logs: PostgreSQL tables (Sentinel-Gear, Claimspindel, Brazz-Nossel)
3. Check compliance: [PRODUCTION-READINESS-ROADMAP.md](docs/PRODUCTION-READINESS-ROADMAP.md)
4. Schedule penetration test (quarterly)

---

## References

- [Architecture Overview](docs/ARCHITECTURE.md)
- [Production-Ready Status](docs/PRODUCTION-READY-STATUS.md)
- [Testing System](docs/TEST-REPORTING-SYSTEM.md)
- [Policy Schema](docs/policy-schema.md)
- [Identity Model](docs/identity-model.md)
- [CI/CD Pipeline](docs/CI-CD-PIPELINE.md)
- [Deployment Guide](docs/DEPLOYMENT.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)

---

## Version History

| Date | Phase | Key Milestone | Status |
|------|-------|--------------|--------|
| Jan 2026 | 1-2 | Foundation + Testing | ✅ Complete |
| Feb 2026 | 3 | Multi-Backend Support | 🔵 In Progress |
| Q2 2026 | 4 | Enterprise Governance | Planned |
| Q3 2026 | 5 | Observability at Scale | Planned |
| Q4 2026 | 6 | Advanced Features | Planned |

---

**Last Updated:** January 18, 2026  
**Maintained By:** IronBucket Development Team  
**Questions?** See [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) or open an issue on GitHub.
