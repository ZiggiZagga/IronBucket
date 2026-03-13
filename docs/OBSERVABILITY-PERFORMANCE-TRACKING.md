# Observability Performance Tracking

Last Updated: 2026-03-13
Scope: Continuous tracking of latency and throughput baselines for the LGTM-backed runtime verification path.

## Purpose

This document tracks measured performance baselines from the Phase 2 performance proof and keeps threshold evolution visible over time.

Primary test:
- scripts/e2e/prove-phase2-performance.sh

Gate integration:
- scripts/ci/run-observability-infra-gate.sh
- scripts/ci/run-observability-performance-gate.sh

History source:
- test-results/phase2-performance/performance-history.csv

## Current Thresholds

- Success rate: >= 99.0%
- Throughput (req/s): >= 20
- P95 latency (ms): <= 350

## Latest Verified Baseline

Source report:
- test-results/phase2-performance/20260313T222700Z/PHASE2_PERFORMANCE_REPORT.md

| Timestamp (UTC) | Target | Requests | Concurrency | Success Rate (%) | Throughput (req/s) | Avg Latency (ms) | P50 (ms) | P95 (ms) | Result |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| 20260313T222700Z | Graphite-Forge /actuator/health | 120 | 12 | 100.00 | 131.88 | 20.47 | 19.30 | 37.24 | PASS |

Service-level snapshot (same report):

| Service | Requests | Success Rate (%) | Throughput (req/s) | P95 Latency (ms) | Result |
|---|---:|---:|---:|---:|---|
| graphite-forge | 60 | 100.00 | 86.26 | 31.11 | PASS |
| buzzle-vane | 60 | 100.00 | 75.44 | 27.17 | PASS |
| claimspindel | 60 | 100.00 | 78.10 | 29.43 | PASS |
| brazz-nossel | 60 | 100.00 | 74.01 | 44.19 | PASS |
| minio | 60 | 100.00 | 120.78 | 6.71 | PASS |
| keycloak | 60 | 100.00 | 90.32 | 18.82 | PASS |
| sentinel-gear-mgmt | 60 | 100.00 | 92.57 | 35.23 | PASS |

## Change Impact Conclusion (x -> y)

Comparison window:
- Previous run: 20260313T215610Z
- Current run: 20260313T222700Z

Measured change:
- Throughput: 105.64 -> 131.88 req/s (+26.24, +24.84%)
- P95 latency: 59.08 -> 37.24 ms (-21.84, -36.97%)
- Average latency: 35.18 -> 20.47 ms (-14.71, -41.81%)

Conclusion:
- The recent changes increased throughput while maintaining full success rate (100%).
- Tail and average latency improved significantly and remain far inside the current gate threshold (P95 <= 350 ms).
- Net effect for this release candidate: better throughput and lower latency with no reliability regression.

## Continuous Tracking Rules

1. Run the performance gate after major observability/runtime changes.
2. Append each new run to the CSV at test-results/phase2-performance/performance-history.csv.
3. Update this document with the newest baseline row and note threshold changes.
4. If p95 latency regresses by >25% versus previous run, open a hardening task in ROADMAP.md.
5. Keep throughput and latency measured against the same target endpoint unless intentionally changed.

## Next Enhancements

- Add authenticated endpoint performance profile (JWT-protected path).
- Add multi-endpoint profile (gateway + GraphQL + policy path) with per-endpoint and per-service trend stats.
- Add Grafana snapshot links for p95/p99 and request rate trend lines.
