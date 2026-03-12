# Phase 2 Observability Proof Plan

## Objective
Provide executable evidence that Phase 2 observability is operational end-to-end using the existing LGTM + OpenTelemetry stack:
- Logs in Loki
- Traces in Tempo
- Metrics pipeline through OTEL Collector (and queryability in Mimir)
- Service-level Prometheus endpoints

## Scope
- Environment: local Docker Compose (`steel-hammer/docker-compose-lgtm.yml`)
- Services under test: `sentinel-gear`, `claimspindel`, `brazz-nossel`, `buzzle-vane`, plus observability components (`loki`, `tempo`, `mimir`, `grafana`, `otel-collector`)
- Evidence output location: `test-results/phase2-observability/<timestamp>/`

## Success Criteria
1. **Stack readiness**
   - LGTM + OTEL containers are up and reachable on internal network.
2. **Service observability endpoints**
   - `/actuator/prometheus` is reachable for core services.
3. **Trace ingestion proof**
   - Synthetic OTLP span is successfully posted to OTEL Collector.
   - Tempo metrics indicate spans received.
4. **Log aggregation proof**
   - Loki query returns log streams for at least one IronBucket service container.
5. **Metrics proof**
   - OTEL Collector metrics show accepted spans/metrics.
   - Mimir query endpoint responds successfully (or returns a documented, reproducible limitation).
6. **Human-readable evidence**
   - Markdown report with pass/fail checks and references to raw evidence files.

## Execution Steps
1. Start stack with build.
2. Discover compose network.
3. Wait for component health endpoints.
4. Generate telemetry traffic:
   - Hit service health/prometheus endpoints.
   - Send synthetic OTLP trace payload.
5. Collect evidence files from Loki/Tempo/Mimir/OTEL and service endpoints.
6. Evaluate criteria and generate final proof report.

## Deliverables
- Plan: `docs/PHASE2-OBSERVABILITY-PROOF-PLAN.md`
- Executable proof script: `scripts/e2e/prove-phase2-observability.sh`
- Execution report: `test-results/phase2-observability/<timestamp>/PHASE2_OBSERVABILITY_PROOF_REPORT.md`
- Raw evidence files under `.../evidence/`
