#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="$ROOT_DIR/steel-hammer"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="$ROOT_DIR/test-results/lgtm-diagnostics/$TIMESTAMP"
mkdir -p "$OUT_DIR"

NET_NAME="$(docker network ls --format '{{.Name}}' | grep 'steel-hammer_steel-hammer-network' | head -n1 || true)"

if [[ -z "$NET_NAME" ]]; then
  echo "ERROR: docker network steel-hammer_steel-hammer-network not found. Start compose stack first." >&2
  exit 1
fi

echo "[diag] output: $OUT_DIR"

docker ps --format 'table {{.Names}}\t{{.Status}}' > "$OUT_DIR/docker-ps.txt"

docker logs --tail 200 steel-hammer-otel-collector > "$OUT_DIR/otel-collector.log" 2>&1 || true
docker logs --tail 200 steel-hammer-grafana > "$OUT_DIR/grafana.log" 2>&1 || true

retry_check() {
  local name="$1"
  local url="$2"
  local insecure="${3:-false}"
  local attempts="${4:-8}"
  local code="000"

  for i in $(seq 1 "$attempts"); do
    if [[ "$insecure" == "true" ]]; then
      code="$(docker run --rm --network "$NET_NAME" curlimages/curl:8.7.1 -ksS -o /dev/null -w '%{http_code}' "$url" || true)"
    else
      code="$(docker run --rm --network "$NET_NAME" curlimages/curl:8.7.1 -sS -o /dev/null -w '%{http_code}' "$url" || true)"
    fi
    echo "$name attempt=$i http=$code" >> "$OUT_DIR/readiness.txt"
    if [[ "$code" == "200" ]]; then
      return 0
    fi
    sleep 2
  done

  return 1
}

touch "$OUT_DIR/readiness.txt"
retry_check "loki" "http://steel-hammer-loki:3100/ready" false || true
retry_check "tempo" "http://steel-hammer-tempo:3200/ready" false || true
retry_check "mimir" "http://steel-hammer-mimir:9009/ready" false || true
retry_check "keycloak" "https://steel-hammer-keycloak:7081/health/ready" true || true
retry_check "minio" "https://steel-hammer-minio:9000/minio/health/live" true || true

docker run --rm --network "$NET_NAME" curlimages/curl:8.7.1 -sS \
  'http://steel-hammer-mimir:9009/prometheus/api/v1/query?query=up' > "$OUT_DIR/mimir-up.json" || true

docker run --rm --network "$NET_NAME" curlimages/curl:8.7.1 -sS \
  'http://steel-hammer-loki:3100/loki/api/v1/label/service_name/values' > "$OUT_DIR/loki-service-names.json" || true

echo "[diag] done"
echo "[diag] key files:"
echo "  - $OUT_DIR/readiness.txt"
echo "  - $OUT_DIR/mimir-up.json"
echo "  - $OUT_DIR/loki-service-names.json"
echo "  - $OUT_DIR/otel-collector.log"
echo "  - $OUT_DIR/grafana.log"
