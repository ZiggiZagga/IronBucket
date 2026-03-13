#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "[performance-gate] running phase2 performance proof"
echo "[performance-gate] thresholds: p95_ms=${PERF_P95_MS_THRESHOLD:-350}, rps=${PERF_RPS_THRESHOLD:-20}, success_rate>=99%"

bash "$ROOT_DIR/scripts/e2e/prove-phase2-performance.sh"

echo "[performance-gate] passed"
