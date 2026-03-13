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
- test-results/phase2-performance/20260313T212324Z/PHASE2_PERFORMANCE_REPORT.md

| Timestamp (UTC) | Target | Requests | Concurrency | Success Rate (%) | Throughput (req/s) | Avg Latency (ms) | P50 (ms) | P95 (ms) | Result |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| 20260313T212324Z | Graphite-Forge /actuator/health | 120 | 12 | 100.00 | 106.37 | 35.39 | 33.27 | 57.77 | PASS |

## Continuous Tracking Rules

1. Run the performance gate after major observability/runtime changes.
2. Append each new run to the CSV at test-results/phase2-performance/performance-history.csv.
3. Update this document with the newest baseline row and note threshold changes.
4. If p95 latency regresses by >25% versus previous run, open a hardening task in ROADMAP.md.
5. Keep throughput and latency measured against the same target endpoint unless intentionally changed.

## Next Enhancements

- Add authenticated endpoint performance profile (JWT-protected path).
- Add multi-endpoint profile (gateway + GraphQL + policy path) with per-endpoint stats.
- Add Grafana snapshot links for p95/p99 and request rate trend lines.
