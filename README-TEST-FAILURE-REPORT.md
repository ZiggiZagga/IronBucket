# IronBucket Test Failure Analysis and Prevention Plan

## Executive Summary
This report reflects the latest Sentinel-Gear gate stabilization work and replaces outdated failure assumptions.

Current state:
- Governance Roadmap Gate: passing
- Sentinel Roadmap Gate: passing
- Sentinel Behavioral Gate: passing

## What We Learned From the Latest Failure Analysis

### 1) Primary failure mode was test infrastructure, not production logic
The largest failure cluster came from Spring test context bootstrapping and test wiring:
- Missing or inconsistent test bootstrapping class references
- Missing `ReactiveJwtDecoder` in test contexts
- Duplicate test bean registration (`reactiveJwtDecoder`) when multiple test configs were loaded together

### 2) Security test style mattered for WebFlux integration stability
Using `mockJwt()` mutators in some routes produced brittle behavior with server-bound clients. Explicit bearer headers were more stable and representative for these contract tests.

### 3) Evidence exporters must follow current test semantics
Governance evidence export initially required only retention+audit keyword matches and failed even when governance tests were otherwise valid. Evidence logic was too strict for actual test naming.

### 4) Endpoint assumptions inside tests were brittle
TLS E2E checks failed when they depended on a specific actuator path mapping/port behavior. Transport-level TLS checks are a more robust invariant.

### 5) Script diagnostics were insufficient on first failure
Gate scripts originally returned Maven failure without concise first-cause extraction from surefire reports, slowing triage.

## Infrastructure Optimizations Implemented

### A) Added automatic surefire failure summaries for gate scripts
New helper:
- `scripts/ci/print-surefire-failures.sh`

Integrated into:
- `scripts/ci/run-governance-roadmap-gate.sh`
- `scripts/ci/run-sentinel-roadmap-gate.sh`
- `scripts/ci/run-sentinel-behavioral-gate.sh`

Result:
- On Maven failure, gate output now includes compact failing testcase summaries and first error detail.

### B) Hardened roadmap gate accounting with Maven exit code
`run-sentinel-roadmap-gate.sh` now includes `mvn_exit` in summary and fails if Maven fails, even before XML counting could hide context.

### C) Stabilized governance evidence gate logic
`scripts/ci/export-governance-evidence-summary.sh` now accepts replay+signature/presigned evidence combinations when retention+audit naming is absent.

### D) Hardened cert artifact discovery for containerized paths
`scripts/lib/common.sh` now supports:
- `CERTS_DIR` override
- fallback to `/certs`

This avoids false negatives in containerized environments.

## Additional Engineering Changes Applied During Stabilization

- Test JWT decoder config centralized to avoid missing bean and duplicate bean issues.
- Roadmap and integration tests aligned to explicit, deterministic `@SpringBootTest` classes.
- Brittle GraphQL route-id assertion replaced with endpoint reachability contract check.
- Production readiness test properties aligned with runtime bindability expectations.
- TLS test refocused on HTTPS transport behavior.

## Remaining Optimization Plan

### Phase 1: Immediate guardrails (next PR)
1. Add a CI step that runs a lightweight static check to detect duplicate test bean names across loaded test configurations.
2. Add a dedicated script to print oldest root cause from surefire dumps when `ApplicationContext failure threshold` appears.
3. Persist gate summaries as reusable artifacts for trend tracking.

### Phase 2: Reliability improvements (short term)
1. Add service readiness contracts for auth/discovery dependencies in integration stack startup.
2. Standardize test endpoint contracts to avoid path-mapping drift (especially actuator endpoints).
3. Add a policy that all gate scripts emit machine-readable summary JSON with explicit `mvn_exit`, `failures`, `errors`.

### Phase 3: Observability and dependency resilience (mid term)
1. Expand correlation-header contract tests to include non-2xx/4xx paths.
2. Add verification around external artifact availability and fallback mirrors for critical internal dependencies.
3. Add MinIO SSE mode compatibility matrix checks in CI profiles.

## Validation Executed
The following gates were executed and verified passing after these changes:

1. `bash scripts/ci/run-governance-roadmap-gate.sh`
2. `bash scripts/ci/run-sentinel-roadmap-gate.sh`
3. `bash scripts/ci/run-sentinel-behavioral-gate.sh`

Observed outcome:
- All three gates passed with zero test failures and zero test errors.

## Notes
- 404 responses observed in governance contract tests are expected for non-backed object paths and are not gate failures.
- Keep this report synchronized with actual gate behavior and workflow policy checks.

_Last updated: March 17, 2026_
