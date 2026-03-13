# Observability Featureset Status

Last Updated: 2026-03-13
Scope: LGTM + OpenTelemetry runtime in IronBucket compose stack, aligned with ROADMAP.md Phase 2/Phase E hardening.

## Executive Summary

IronBucket observability is partially green in live runtime:

- Logs: operational (Loki + Promtail ingesting application streams)
- Metrics: operational (Mimir query path returns `up=1` for core services)
- Traces: degraded (Tempo restart loop; trace query path unavailable)
- UI E2E proof artifacts: operational (Playwright screenshot artifacts generated; screenshot proof uploaded to MinIO and downloaded/displayed)

Roadmap alignment:

- Phase 2 goal "complete observability" is functionally met for logs and metrics.
- Phase E gate hardening remains valid, but tracing is currently a runtime blocker for full green status.

## Verified Runtime Evidence (2026-03-13)

### Stack/Container Status

- Up: `steel-hammer-loki`, `steel-hammer-mimir`, `steel-hammer-grafana`, `steel-hammer-otel-collector`, `steel-hammer-promtail`
- Up (core services): `steel-hammer-sentinel-gear`, `steel-hammer-graphite-forge`, `steel-hammer-claimspindel`, `steel-hammer-brazz-nossel`, `steel-hammer-minio`, `steel-hammer-keycloak`
- Degraded: `steel-hammer-tempo` (restart loop)

### Logs (Loki)

Status: PASS

Evidence highlights:

- Query path returns Graphite-Forge streams for GraphQL operations.
- E2E screenshot-proof flow is visible in logs:
  - `graphql uploadObject invoked bucket=default-alice-proofs key=alice-ui-e2e-proof-...png.b64`
  - `graphql listObjects invoked bucket=default-alice-proofs query=alice-ui-e2e-proof-...png.b64`
  - `graphql downloadObject invoked bucket=default-alice-proofs key=alice-ui-e2e-proof-...png.b64`

### Metrics (Mimir)

Status: PASS

Evidence highlights:

- `GET /prometheus/api/v1/query?query=up` returns successful vectors.
- Core service scrape targets observed with `value=1` in runtime checks.

### Traces (Tempo)

Status: FAIL (runtime)

Evidence highlights:

- Container state: `status=restarting`, repeated exits with code 1.
- Repeating error in Tempo logs:
  - `failed to init module services: ... distributor: failed to create distributor: the Kafka topic has not been configured`

Impact:

- End-to-end trace retrieval by trace ID is not currently possible through Tempo.
- Logs + metrics remain available and are sufficient for partial observability proofs, but not for full tracing proof.

## Feature Matrix

| Capability | Target (Roadmap) | Current | Status | Notes |
|---|---|---|---|---|
| Service log aggregation (Loki) | Required | Implemented and verified | PASS | Streams available by `service_name` |
| Metrics pipeline/query (Mimir) | Required | Implemented and verified | PASS | `up` query returns core targets |
| Distributed traces (Tempo) | Required | Runtime broken | FAIL | Kafka topic config mismatch in Tempo distributor |
| OTEL collector ingestion | Required | Running | PASS* | Collector running; trace backend unavailable due Tempo |
| UI E2E observability artifacts | Required hardening evidence | Implemented and verified | PASS | JSON + screenshot proof artifacts generated |
| Screenshot proof persisted in MinIO | Not explicit in roadmap, supports evidence hardening | Implemented and verified | PASS | Proof object present in `default-alice-proofs` |

## Roadmap Alignment and Adjustments

### In alignment

- Phase 2 objective for logs/metrics evidence is satisfied in live environment.
- Phase E "gate hardening" direction is supported by deterministic UI evidence artifacts.
- First-user experience proof path now includes UI screenshot evidence persisted in object storage.

### Required adjustment

- Treat tracing as a blocking runtime gap until Tempo configuration is corrected.
- Keep observability gate semantics split:
  - Logs + metrics: blocking checks
  - Traces: currently warning-path with explicit incident note

## Recommended Corrective Actions

1. Fix Tempo config in `steel-hammer/tempo-config.yaml` to remove or correctly configure Kafka-dependent distributor settings for local single-binary mode.
2. Add a deterministic readiness check for Tempo that validates startup without restart loops.
3. Add a trace-by-id smoke test in E2E scripts after Tempo fix:
   - Extract trace ID from UI artifact
   - Query Tempo API for the same trace ID
   - Assert spans from sentinel/graphite/claimspindel/brazz path
4. Promote screenshot proof and trace-id artifact publication as standard release evidence.

## Current Evidence Artifacts

- `test-results/ui-e2e-traces/ui-live-upload-persistence.json`
- `test-results/ui-e2e-traces/ui-s3-methods-e2e.json`
- `test-results/ui-e2e-traces/ui-s3-methods-proof.png`

Note: `ui-s3-methods-e2e.json` currently stores complete checks and trace id from the last passing run. It should be used as the canonical runtime proof artifact for UI S3-method coverage.
