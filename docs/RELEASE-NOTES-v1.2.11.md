# Release Notes v1.2.11

Date: 2026-03-13

## Highlights

- Enabled Java virtual threads in core Spring Boot services to improve runtime concurrency posture:
  - `services/Graphite-Forge`
  - `services/Buzzle-Vane`
  - `services/Claimspindel`
  - `services/Brazz-Nossel`
  - `services/Sentinel-Gear`
- Extended Phase 2 performance proof with service-level measurement and threshold evaluation.
- Updated continuous performance tracking with the latest validated baseline and per-service snapshot.
- Verified full orchestrator success after fixes: 187/187 tests passing.
- Applied reactive migration where compatible:
  - `tools/Vault-Smith` moved to `spring-boot-starter-webflux`
  - `services/Buzzle-Vane` intentionally remains on `spring-boot-starter-web` for Eureka compatibility

## Validation Evidence

- Observability proof: `test-results/phase2-observability/20260313T215355Z/PHASE2_OBSERVABILITY_PROOF_REPORT.md`
- Observability proof (latest): `test-results/phase2-observability/20260313T222356Z/PHASE2_OBSERVABILITY_PROOF_REPORT.md`
- Performance proof (latest): `test-results/phase2-performance/20260313T222700Z/PHASE2_PERFORMANCE_REPORT.md`
- Full orchestrator report: `test-results/reports/LATEST-REPORT.md`
- Gate execution: `bash scripts/ci/run-observability-infra-gate.sh`

## Baseline Summary (Latest)

- Success rate: 100.00%
- Throughput: 131.88 req/s
- P95 latency: 37.24 ms
- Service-level thresholds: PASS for all measured services

## Change Impact (x -> y)

Compared with the previous run (20260313T215610Z):

- Throughput: 105.64 -> 131.88 req/s (+24.84%)
- P95 latency: 59.08 -> 37.24 ms (-36.97%)
- Reliability: unchanged at 100.00% success rate

Conclusion: this release candidate improves throughput and latency with stable reliability and keeps all thresholds comfortably green.

## Notes

- `spring-boot-starter-web` usage currently remains in `services/Buzzle-Vane` and `tools/Vault-Smith` and is tracked for follow-up reactive migration work.
