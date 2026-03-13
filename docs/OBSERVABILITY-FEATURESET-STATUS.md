# Observability Featureset Status

Last Updated: 2026-03-13
Scope: LGTM + OpenTelemetry runtime in IronBucket compose stack, aligned with ROADMAP.md Phase 2/Phase E hardening.

## Executive Summary

IronBucket observability is verified green in the current Phase 2 proof run.

- Logs: operational (Loki + Promtail ingesting service streams)
- Metrics: operational (Mimir query path and infra scrape checks green)
- Traces: operational (OTLP ingest accepted and Tempo/Collector counters > 0)
- Error handling + correlation propagation: operational (Graphite-Forge error contracts and response correlation headers verified)

Primary evidence source:
- test-results/phase2-observability/20260313T211751Z/PHASE2_OBSERVABILITY_PROOF_REPORT.md

## Verified Completed

- Stack readiness checks pass for Loki, Tempo, Mimir, Keycloak, MinIO, Postgres exporter, and core services.
- Service Prometheus endpoints are reachable and contain valid metric payloads.
- Synthetic OTLP trace POST succeeds with HTTP 200.
- Trace ingestion is confirmed via Tempo distributor and OTEL collector accepted-span counters.
- Loki log query returns service streams (`service_name` query path).
- Mimir query status is `success`, with infra up-metrics evidence files present.
- Runtime OTEL env wiring is verified for Sentinel-Gear, Claimspindel, Brazz-Nossel, and Buzzle-Vane.
- Error handling checks pass in Graphite-Forge:
  - 404 response contract and JSON error payload.
  - GraphQL parse-error contract (`InvalidSyntax`).
  - `X-Correlation-ID` response propagation in both cases.

## Verified Not Completed

- Cross-service correlation semantic assertions in Loki are not yet a blocking gate condition (stream presence is verified, semantic join is not).
- Authenticated negative-path error handling checks for JWT-protected APIs are not yet included in Phase 2 proof.
- UI trace-id to Tempo trace lookup is not yet mandatory in the gate.

## Test Adjustments Applied

- Updated scripts/e2e/prove-phase2-observability.sh to include new blocking checks:
  - Graphite-Forge 404 error + correlation response header validation.
  - Graphite-Forge GraphQL parse-error + correlation response header validation.
- These checks are now included in the gate verdict (`OVERALL_OK`).

## Future Implementation Cases (Queued)

1. Add authenticated error-path scenario for protected APIs (token-backed negative tests).
2. Add Loki correlation semantic checks (`correlationId`/`traceId` presence and queryability) as blocking assertions.
3. Add trace-id roundtrip assertion from UI E2E artifact to Tempo query.
4. Add SLO-focused observability assertions (p95/p99 latency and error-rate thresholds) in advanced gates.

## References

- docs/E2E-OBSERVABILITY-GUIDE.md
- ROADMAP.md
- README.md
- scripts/ci/run-observability-infra-gate.sh
- scripts/e2e/prove-phase2-observability.sh
