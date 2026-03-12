# Roadmap Execution Plan (2026-03-12)

## Scope

This plan is derived from the verified roadmap profile run in `services/Sentinel-Gear`:
- 105 tests run
- 30 failures
- readiness score below 80% gate

The plan focuses on converting roadmap assertions into implementation increments without weakening test intent.

---

## Verified Gap Clusters

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

## Immediate Next Actions (Executed in this update)

- Consolidated roadmap docs to single source: `ROADMAP.md`.
- Added compatibility entry: `docs/PRODUCTION-READINESS-ROADMAP.md`.
- Updated roadmap references in top-level docs.
- Updated roadmap README status/commands/links to current verified state.
- Fixed duplicate security execution in `scripts/comprehensive-test-reporter.sh`.

---

## Tracking Recommendation

For each phase, open one epic plus 3-6 narrowly scoped tasks with explicit failing-test references, then close tasks only when both:
1. relevant roadmap tests pass, and
2. docs reflect the new state in `ROADMAP.md`.
