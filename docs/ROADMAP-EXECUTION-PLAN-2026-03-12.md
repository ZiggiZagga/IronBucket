# Roadmap Execution Plan (2026-03-12)

## Scope

This plan is derived from the verified roadmap profile run in `services/Sentinel-Gear`:
- 105 tests run
- 0 failures
- readiness gates met for the current Sentinel roadmap profile

The plan focuses on converting roadmap assertions into implementation increments without weakening test intent.

---

## Verified Gap Clusters (Historical Baseline)

### P0 (Blockers)
1. TLS configuration not declared in Sentinel-Gear (`server.ssl` expectations fail).
2. Vault dependency/config not present (`spring-cloud-vault-config` expectation fails).
3. GraphQL management plane missing (`Graphite-Forge` schema/resolvers missing).
4. S3 completeness below gate (coverage assertion below 80%).

### P1/P2 (Major)
1. Governance resilience suite lacks required artifacts/tests (chaos, drift, replay, retention, cutover).
2. Priority coverage scoreboard missing required test files.
3. Advanced S3 and cross-backend parity behaviors not fully represented in executable tests.

---

## Execution Update (2026-03-12, latest)

### ✅ Phase A — Stabilize Security Baseline (Completed)
- Vault dependency/config scaffolding in Sentinel-Gear is present.
- TLS configuration scaffolding in Sentinel-Gear is present.
- Related production-readiness tests pass.

### ✅ Phase B — GraphQL Management Plane Skeleton (Completed for roadmap contract)
- GraphQL schema/resolver contract checks are now passing.
- GraphQL roadmap completeness score reached 100% in current suite.

### ✅ Phase C — S3 API Completion Tranche (Completed for roadmap gate)
- S3 roadmap suite is green.
- S3 completeness score reached 90% (target gate 80%).

### ✅ Phase D — Governance & Resilience Harness (Completed)
- Required immediate/high/medium artifacts and files added.
- Tamper/replay controls implemented and tested.
- Priority coverage scoreboard now passes (Immediate 4/4, High 3/3, Medium 5/5).

### 🔵 Phase E — Gate Hardening and Release Policy (Next Active)
- Enforce roadmap profile in CI as required gate for Sentinel-Gear changes.
- Split “scaffold existence” checks from “behavioral/e2e” checks to prevent false confidence.
- Promote presigned security config requirements into deployment manifests and runbooks.

**Progress update (2026-03-12):**
- `build-and-test.yml` now includes a dedicated blocking job: `Sentinel Roadmap Gate`.
- Gate runs `scripts/ci/run-sentinel-roadmap-gate.sh`, executes `mvn test -Proadmap` in Sentinel-Gear,
  and fails if roadmap reports are missing, if failures/errors occur, or if executed roadmap tests are below threshold.
- CI now also includes a separate `Sentinel Behavioral Gate` job using `scripts/ci/run-sentinel-behavioral-gate.sh`
  to run `mvn test -Pintegration` and report behavioral/integration failures independently of roadmap scaffold checks.
- Behavioral gate is now strict and blocking on all configured refs after integration baseline stabilization.

### ✅ Phase F — Full Orchestrator Stability Verification (Completed)
- End-to-end orchestrator run is now green at 157/157 (`scripts/run-all-tests-complete.sh`).
- Alice/Bob scenario is stabilized for containerized execution path used by the orchestrator.
- Transient upstream readiness responses in E2E path are now handled with bounded retries (502/503/504) to reduce flaky false negatives.

---

## Execution Sequence

## Phase A — Stabilize Security Baseline (Week 1)
- Add Vault dependency and minimal externalized secret wiring in Sentinel-Gear.
- Add TLS config scaffolding for Sentinel-Gear (dev profile + docs for cert path).
- Add/align smoke tests proving config presence and startup behavior.

**Exit criteria**
- `ProductionReadinessTest.testVaultIntegrationDependencies` passes.
- `ProductionReadinessTest.testTLSConfiguration` passes.

## Phase B — GraphQL Management Plane Skeleton (Week 1-2)
- Introduce `Graphite-Forge` module skeleton under active source tree.
- Add `schema.graphqls` plus stub resolvers required by tests:
  - `PolicyMutationResolver`
  - `IdentityQueryResolver`
  - `AuditQueryResolver`
- Wire module into build without breaking existing runtime.

**Exit criteria**
- GraphQL schema/resolver existence tests pass.
- Coverage score increases from 0.0% baseline.

## Phase C — S3 API Completion Tranche (Week 2-3)
- Expand `S3Controller`/`S3ProxyService` for missing core methods first (`CreateBucket` etc.).
- Add multipart/versioning/tagging/ACL paths in small increments.
- Validate each increment with roadmap S3 suite.

**Exit criteria**
- Core S3 P0 tests pass.
- S3 completeness score trends toward 80% target.

## Phase D — Governance & Resilience Harness (Week 3-4)
- Create missing immediate-priority tests/files referenced by scoreboard.
- Add initial implementations for tamper/replay detection hooks and drift reconciliation checks.
- Add chaos test scaffolding (adapter crash, metadata drift, bypass attempts).

**Exit criteria**
- Priority scoreboard immediate tests present and executable.
- Governance failures reduced in immediate/high categories.

## Phase E — Gate Hardening and Release Policy (Week 4+)
- Enforce roadmap gate policy in CI as non-blocking report first, then blocking at threshold.
- Publish rollout checklist for transitioning from 75% to 80%+ readiness gate.

---

## Immediate Next Actions (Executed)

- Consolidated roadmap docs to single source: `ROADMAP.md`.
- Added compatibility entry: `docs/PRODUCTION-READINESS-ROADMAP.md`.
- Updated roadmap references in top-level docs.
- Updated roadmap README status/commands/links to current verified state.
- Fixed duplicate security execution in `scripts/comprehensive-test-reporter.sh`.
- Completed governance/resilience artifact rollout and runtime presigned-request enforcement.
- Added externalized presigned security configuration with fail-fast startup checks.

---

## Tracking Recommendation

For each phase, open one epic plus 3-6 narrowly scoped tasks with explicit failing-test references, then close tasks only when both:
1. relevant roadmap tests pass, and
2. docs reflect the new state in `ROADMAP.md`.
