# Release Notes v1.2.13 (Draft)

Date: 2026-03-15 (prepared on 2026-03-14)

## Scope

Diff basis for this draft release:
- from tag `v1.2.12`
- to branch head `develop` (`75dbd06`)

Key focus of this increment:
- TLS/Vault runtime hardening in LGTM
- deterministic certificate bootstrap for test scripts
- startup reliability fixes in WebFlux security setup

## Highlights

- Added automatic certificate artifact generation to core test entry scripts when certs are missing.
- Hardened internal TLS certificates to include compose host aliases (notably MinIO and Postgres hostnames used in runtime networking).
- Enabled Vault TLS service in LGTM compose and integrated Vault health into infrastructure checks.
- Added Vault metrics scrape path into OTEL collector pipelines for LGTM metrics visibility.
- Fixed startup failures in Brazz-Nossel and Claimspindel by registering explicit ReactiveJwtDecoder beans.

## Functional Changes

### 1) Certificate Bootstrap Hardening

Updated central helper and test entrypoints to regenerate cert artifacts if absent:
- scripts/lib/common.sh
- scripts/run-all-tests-complete.sh
- scripts/e2e/e2e-alice-bob-test.sh
- scripts/e2e/e2e-test-standalone.sh
- scripts/e2e/test-containerized.sh
- scripts/e2e/run-containerized-tests.sh

Behavior:
- Checks required TLS assets in certs/
- Executes certs/generate-certificates.sh when artifacts are missing
- Fails fast if generation is incomplete

### 2) TLS SAN Compatibility Improvements

Updated certificate generation to support additional DNS SAN aliases:
- certs/generate-certificates.sh

Notable runtime aliases now covered:
- steel-hammer-minio
- steel-hammer-postgres

### 3) Vault TLS in LGTM Stack

Vault service now present in LGTM compose with TLS configuration and health checks:
- steel-hammer/docker-compose-lgtm.yml
- steel-hammer/vault/dev-server.hcl

Infrastructure validation now includes Vault TLS health:
- scripts/run-all-tests-complete.sh

### 4) Vault Observability in LGTM

Added Vault Prometheus scrape job to OTEL collector configs:
- steel-hammer/otel-collector-config.yml
- steel-hammer/otel-collector-config-default.yml

Result:
- Vault metrics are available in the LGTM metrics pipeline.

### 5) WebFlux Security Startup Reliability

Added explicit JWT decoder bean registration in:
- services/Brazz-Nossel/src/main/java/com/ironbucket/brazznossel/SecurityConfig.java
- services/Claimspindel/src/main/java/com/ironbucket/claimspindel/config/SecurityConfig.java

Result:
- Removes runtime startup failure caused by missing ReactiveJwtDecoder bean in those services.

## Validation Evidence

Latest complete orchestrator run after cert artifact deletion:
- Command: bash scripts/run-all-tests-complete.sh
- Result: 194 total, 193 passed, 2 failed
- Passed phases: Infrastructure, Alice-Bob E2E, core observability endpoints
- Failing suites:
  - tools/Storage-Conductor build failure
  - Observability_Phase2_Proof failure

Additional runtime checks confirmed:
- Vault HTTPS health endpoint reachable
- Vault Prometheus metrics endpoint active
- Vault job visible in LGTM metrics ingestion path

## Known Issues / Follow-ups

Before final v1.2.13 tag and merge to main:
1. Resolve Storage-Conductor Maven/build failure in complete orchestrator path.
2. Stabilize Observability_Phase2_Proof assertions and rerun complete suite to green baseline.
3. Re-run release preflight and required gate checks on develop.

## Suggested Tag Plan

Recommended once follow-ups are green:
- Tag: v1.2.13
- Branch: develop (then merge to main next day)
