#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export KEEP_STACK="${KEEP_STACK:-false}"
export INFRA_KEYCLOAK_UP_SUM_THRESHOLD="${INFRA_KEYCLOAK_UP_SUM_THRESHOLD:-0.0}"
export INFRA_MINIO_UP_SUM_THRESHOLD="${INFRA_MINIO_UP_SUM_THRESHOLD:-1.0}"
export INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD="${INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD:-1.0}"
export PERF_P95_MS_THRESHOLD="${PERF_P95_MS_THRESHOLD:-350}"
export PERF_P99_MS_THRESHOLD="${PERF_P99_MS_THRESHOLD:-650}"
export PERF_RPS_THRESHOLD="${PERF_RPS_THRESHOLD:-20}"
export PERF_ERROR_RATE_THRESHOLD="${PERF_ERROR_RATE_THRESHOLD:-1.0}"

echo "[observability-gate] running phase2 observability proof"
echo "[observability-gate] thresholds: keycloak=${INFRA_KEYCLOAK_UP_SUM_THRESHOLD}, minio=${INFRA_MINIO_UP_SUM_THRESHOLD}, postgres_exporter=${INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD}"
echo "[observability-gate] performance thresholds: p95=${PERF_P95_MS_THRESHOLD}ms, p99=${PERF_P99_MS_THRESHOLD}ms, rps=${PERF_RPS_THRESHOLD}, error_rate=${PERF_ERROR_RATE_THRESHOLD}%"

KEEP_STACK=true bash "$ROOT_DIR/scripts/e2e/prove-phase2-observability.sh"
PERF_REUSE_STACK=true KEEP_STACK=false bash "$ROOT_DIR/scripts/e2e/prove-phase2-performance.sh"

echo "[observability-gate] passed"
