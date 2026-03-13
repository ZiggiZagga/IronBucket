# IronBucket Release Notes v1.2.8

Release date: 2026-03-13

## Executive Summary

This release focuses on reliability and clarity:

- Stabilizes a recurring e2e pipeline failure in the observability gate.
- Aligns top-level documentation with files and commands that actually exist.
- Adds plain-language "pain points" examples in the main README so non-specialists can quickly understand IronBucket's value.

## Highlights

### 1. CI/CD Reliability: e2e Observability Gate Stabilized

The e2e workflow previously failed intermittently in the Phase 2 observability proof with curl exit code 23.

Root cause:
- A curl call inside a container wrote response output directly to a mounted evidence path using `-o`, which could fail during write operations in CI.

Fix:
- Updated `scripts/e2e/prove-phase2-observability.sh` to capture curl output via host-side redirection.
- Split raw output into:
  - `otlp-trace-post-status.txt` (HTTP status)
  - `otlp-trace-post-response.txt` (response body)

Outcome:
- Local reproduction of the previously failing gate now passes end-to-end.

### 2. Documentation Accuracy Improvements

The main README was updated to remove broken or stale references and point to active docs.

Updated references include:
- Production readiness roadmap docs
- Testing quick-start docs
- E2E quickstart and observability guide
- Correct contributing guide location under `docs/`

### 3. Main README: Human-Friendly Problem Statements

Added a new section describing real-world pain IronBucket addresses, such as:
- Multi-tenant data isolation concerns
- Repeated security logic reimplementation across services
- Compliance and audit traceability gaps
- Need for secure on-prem S3-compatible storage without lock-in

## Detailed Changes

### Changed

- `README.md`
  - Added plain-language "What Pain IronBucket Solves" section.
  - Corrected outdated documentation links.
  - Added release notes reference for this version.

- `scripts/e2e/prove-phase2-observability.sh`
  - Reworked OTLP curl response/status capture to avoid container write instability.

### Added

- `docs/RELEASE-NOTES-v1.2.8.md`

## Verification Performed

The following checks were run after the fixes:

- `bash scripts/ci/validate-shell-scripts.sh` -> pass
- `bash scripts/ci/verify-e2e-doc-sync.sh` -> pass
- `KEEP_STACK=false INFRA_KEYCLOAK_UP_SUM_THRESHOLD=0.0 INFRA_MINIO_UP_SUM_THRESHOLD=1.0 INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD=1.0 bash scripts/ci/run-observability-infra-gate.sh` -> pass

## Operational Notes

- A Docker Compose warning remains about obsolete `version` key usage in `steel-hammer/docker-compose-lgtm.yml`. It does not block execution but should be cleaned up in a later maintenance pass.

## Upgrade / Adoption Impact

- No API contract changes introduced.
- No migration actions required for existing runtime deployments from this patch release.
- CI users should observe improved stability for `e2e-complete-suite` Phase 2 observability execution.

## Known Constraints / Remaining Work

- Production hardening roadmap items remain active (network policy enforcement, secrets governance, release governance checks).
- This release is focused on reliability and documentation correctness, not new runtime features.
