# IronBucket Documentation

**Last Updated:** March 12, 2026  
**Status:** Core platform 100% operational | Phase 3 roadmap contracts 100% complete (current Sentinel roadmap suite)

---

## Quick Start

- **New Users:** Start with [GETTING_STARTED.md](GETTING_STARTED.md)
- **Current Status:** See [../test-results/reports/LATEST-REPORT-EXPLAINED.md](../test-results/reports/LATEST-REPORT-EXPLAINED.md)
- **Roadmap:** See [../ROADMAP.md](../ROADMAP.md)
- **Run Tests:** `bash scripts/run-all-tests-complete.sh` from project root

---

## Core Documentation

### Architecture & Design
- [ARCHITECTURE.md](ARCHITECTURE.md) - System design, components, data flow
- [identity-model.md](identity-model.md) - JWT claims, tenant isolation
- [policy-schema.md](policy-schema.md) - Policy engine rules and semantics

### Operations
- [DEPLOYMENT.md](DEPLOYMENT.md) - Docker Compose and Kubernetes deployment
- [E2E-OBSERVABILITY-GUIDE.md](E2E-OBSERVABILITY-GUIDE.md) - Loki, Tempo, Grafana, Mimir setup
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Common issues and solutions

### Development
- [API.md](API.md) - REST API endpoints
- [s3-proxy-contract.md](s3-proxy-contract.md) - S3 API compatibility layer
- [PHASE4-JCLOUDS-CAPABILITY-MATRIX.md](PHASE4-JCLOUDS-CAPABILITY-MATRIX.md) - Initial provider capability baseline for Phase 4
- [testing/TESTING-QUICK-START.md](testing/TESTING-QUICK-START.md) - Running tests

### Security
- [security/THREAT-MODEL.md](security/THREAT-MODEL.md) - Security analysis
- [security/COMPLIANCE-MATRIX.md](security/COMPLIANCE-MATRIX.md) - Compliance requirements
- [k8s-network-policies.yaml](k8s-network-policies.yaml) - Network isolation rules

---

## Test Results

**Latest Reports:**
- [test-results/reports/LATEST-REPORT-EXPLAINED.md](../test-results/reports/LATEST-REPORT-EXPLAINED.md) - **Start here**
- [test-results/reports/LATEST-REPORT.md](../test-results/reports/LATEST-REPORT.md) - Raw test output

**Test Coverage:**
- Core Platform: 100% (7/7 tests passing)
- Infrastructure: 89% (16/18 tests passing)
- Observability: 100% (Loki, Tempo, Grafana, Mimir operational)
- Maven Tests: 79% (103/131 - 28 are TDD roadmap tests for Phase 3)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      External Access                         │
│                  (Only Sentinel-Gear:8080)                   │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
                    Sentinel-Gear
                  (API Gateway + JWT)
                           ↓
              ┌────────────┴────────────┐
              ↓                         ↓
        Claimspindel              Brazz-Nossel
      (Policy Engine)              (S3 Proxy)
              ↓                         ↓
              └────────────┬────────────┘
                           ↓
                      MinIO (S3)
```

**Observability:** All services → OTEL Collector → Loki/Tempo/Mimir → Grafana

---

## Directory Structure

```
docs/
├── README.md (this file)
├── ARCHITECTURE.md
├── DEPLOYMENT.md
├── E2E-OBSERVABILITY-GUIDE.md
├── GETTING_STARTED.md
├── TROUBLESHOOTING.md
├── API.md
├── identity-model.md
├── policy-schema.md
├── s3-proxy-contract.md
├── k8s-manifests-production.yaml
├── k8s-network-policies.yaml
├── security/
│   ├── THREAT-MODEL.md
│   ├── COMPLIANCE-MATRIX.md
│   └── VAULT-INTEGRATION.md
└── testing/
    └── TESTING-QUICK-START.md
```

---

## Marathon Mindset

IronBucket follows the **Marathon Mindset**: Complete feature implementation, not partial delivery.

**How we work:**
1. Write TDD tests that define complete requirements (see `Sentinel-Gear/src/test/java/com/ironbucket/roadmap/`)
2. Tests intentionally fail until feature is fully implemented
3. No partial features shipped to production
4. Phase 3 roadmap requirements are enforced by `scripts/ci/run-sentinel-roadmap-gate.sh`

**Example:** S3 API completeness test requires 80% coverage before passing - we won't ship 50% and call it done.

---

## Contributing

1. Review [ARCHITECTURE.md](ARCHITECTURE.md)
2. Check Phase 3 requirements: `Sentinel-Gear/src/test/java/com/ironbucket/roadmap/`
3. Set up dev environment: `cd steel-hammer && docker-compose -f docker-compose-lgtm.yml up`
4. Run tests: `bash scripts/run-all-tests-complete.sh`
5. Submit PR with tests

---

**Questions?** See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) or view test reports in `../test-results/reports/`
