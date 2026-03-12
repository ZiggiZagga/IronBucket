#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export KEEP_STACK="${KEEP_STACK:-false}"
export INFRA_KEYCLOAK_UP_SUM_THRESHOLD="${INFRA_KEYCLOAK_UP_SUM_THRESHOLD:-0.0}"
export INFRA_MINIO_UP_SUM_THRESHOLD="${INFRA_MINIO_UP_SUM_THRESHOLD:-1.0}"
export INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD="${INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD:-1.0}"

echo "[observability-gate] running phase2 observability proof"
echo "[observability-gate] thresholds: keycloak=${INFRA_KEYCLOAK_UP_SUM_THRESHOLD}, minio=${INFRA_MINIO_UP_SUM_THRESHOLD}, postgres_exporter=${INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD}"

bash "$ROOT_DIR/scripts/e2e/prove-phase2-observability.sh"

echo "[observability-gate] passed"
