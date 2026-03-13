#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="$ROOT_DIR/steel-hammer"
COMPOSE_FILE="$STACK_DIR/docker-compose-lgtm.yml"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="$ROOT_DIR/test-results/phase2-observability/$TIMESTAMP"
EVIDENCE_DIR="$OUT_DIR/evidence"
REPORT_FILE="$OUT_DIR/PHASE2_OBSERVABILITY_PROOF_REPORT.md"
KEEP_STACK="${KEEP_STACK:-false}"
INFRA_KEYCLOAK_UP_SUM_THRESHOLD="${INFRA_KEYCLOAK_UP_SUM_THRESHOLD:-1.0}"
INFRA_MINIO_UP_SUM_THRESHOLD="${INFRA_MINIO_UP_SUM_THRESHOLD:-1.0}"
INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD="${INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD:-1.0}"

mkdir -p "$EVIDENCE_DIR"

log() {
  printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"
}

run_internal_curl() {
  local url="$1"
  local output_file="$2"
  docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS "$url" > "$output_file"
}

run_container_local_curl() {
  local container_name="$1"
  local url="$2"
  local output_file="$3"
  docker run --rm --network "container:$container_name" curlimages/curl:8.12.1 -sS "$url" > "$output_file"
}

wait_internal_http() {
  local name="$1"
  local url="$2"
  local max_attempts="${3:-60}"
  local delay_seconds="${4:-3}"

  for ((attempt=1; attempt<=max_attempts; attempt++)); do
    if docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -f "$url" > /dev/null 2>&1; then
      log "READY: $name"
      return 0
    fi
    sleep "$delay_seconds"
  done

  log "NOT READY: $name"
  return 1
}

wait_container_local_http() {
  local name="$1"
  local container_name="$2"
  local url="$3"
  local max_attempts="${4:-60}"
  local delay_seconds="${5:-3}"

  for ((attempt=1; attempt<=max_attempts; attempt++)); do
    if docker run --rm --network "container:$container_name" curlimages/curl:8.12.1 -sS -f "$url" > /dev/null 2>&1; then
      log "READY: $name"
      return 0
    fi
    sleep "$delay_seconds"
  done

  log "NOT READY: $name"
  return 1
}

json_len() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys
p=sys.argv[1]
try:
    with open(p, 'r', encoding='utf-8') as f:
        data=json.load(f)
    result=data.get('data',{}).get('result',[])
    print(len(result) if isinstance(result, list) else 0)
except Exception:
    print(0)
PY
}

log "Starting LGTM + services stack"
(
  cd "$STACK_DIR"
  docker compose -f "$COMPOSE_FILE" up -d --build
) > "$EVIDENCE_DIR/compose-up.log" 2>&1

(
  cd "$STACK_DIR"
  docker compose -f "$COMPOSE_FILE" ps
) > "$EVIDENCE_DIR/compose-ps-initial.txt"

NETWORK_NAME="$(docker inspect steel-hammer-loki --format '{{range $k, $v := .NetworkSettings.Networks}}{{println $k}}{{end}}' | head -n1 | tr -d '\r')"
if [[ -z "$NETWORK_NAME" ]]; then
  echo "Failed to discover compose network name" >&2
  exit 1
fi
log "Discovered network: $NETWORK_NAME"

STACK_OK=true
wait_internal_http "Loki" "http://steel-hammer-loki:3100/ready" || STACK_OK=false
wait_internal_http "Tempo" "http://steel-hammer-tempo:3200/ready" || STACK_OK=false
wait_internal_http "Mimir" "http://steel-hammer-mimir:9009/prometheus/api/v1/status/buildinfo" || STACK_OK=false
wait_internal_http "Keycloak" "http://steel-hammer-keycloak:7081/realms/dev/.well-known/openid-configuration" 90 3 || STACK_OK=false
INFRA_ENDPOINTS_READY=true
wait_internal_http "Keycloak metrics" "http://steel-hammer-keycloak:7081/metrics" 90 3 || INFRA_ENDPOINTS_READY=false
wait_internal_http "MinIO metrics" "http://steel-hammer-minio:9000/minio/v2/metrics/cluster" || INFRA_ENDPOINTS_READY=false
wait_internal_http "Postgres exporter metrics" "http://steel-hammer-postgres-exporter:9187/metrics" || INFRA_ENDPOINTS_READY=false
wait_internal_http "Buzzle-Vane" "http://steel-hammer-buzzle-vane:8083/actuator/health" || STACK_OK=false
wait_internal_http "Claimspindel" "http://steel-hammer-claimspindel:8081/actuator/health" || STACK_OK=false
wait_internal_http "Brazz-Nossel" "http://steel-hammer-brazz-nossel:8082/actuator/health" || STACK_OK=false
wait_container_local_http "Sentinel-Gear mgmt" "steel-hammer-sentinel-gear" "http://localhost:8081/actuator/health-check" || STACK_OK=false

log "Collecting service Prometheus endpoints"
run_internal_curl "http://steel-hammer-buzzle-vane:8083/actuator/prometheus" "$EVIDENCE_DIR/buzzle-prometheus.txt" || true
run_internal_curl "http://steel-hammer-claimspindel:8081/actuator/prometheus" "$EVIDENCE_DIR/claimspindel-prometheus.txt" || true
run_internal_curl "http://steel-hammer-brazz-nossel:8082/actuator/prometheus" "$EVIDENCE_DIR/brazz-prometheus.txt" || true
run_container_local_curl "steel-hammer-sentinel-gear" "http://localhost:8081/actuator/prometheus" "$EVIDENCE_DIR/sentinel-prometheus.txt" || true
run_internal_curl "http://steel-hammer-keycloak:7081/metrics" "$EVIDENCE_DIR/keycloak-metrics.txt" || true
run_internal_curl "http://steel-hammer-minio:9000/minio/v2/metrics/cluster" "$EVIDENCE_DIR/minio-metrics.txt" || true
run_internal_curl "http://steel-hammer-postgres-exporter:9187/metrics" "$EVIDENCE_DIR/postgres-exporter-metrics.txt" || true

log "Generating synthetic OTLP trace"
OTLP_TRACE_JSON="$EVIDENCE_DIR/synthetic-trace.json"
python3 - "$OTLP_TRACE_JSON" <<'PY'
import json, secrets, time, sys
out = sys.argv[1]
start = int(time.time_ns())
end = start + 20_000_000
payload = {
  "resourceSpans": [{
    "resource": {
      "attributes": [
        {"key": "service.name", "value": {"stringValue": "phase2-proof-script"}},
        {"key": "deployment.environment", "value": {"stringValue": "local"}}
      ]
    },
    "scopeSpans": [{
      "scope": {"name": "phase2-proof", "version": "1.0.0"},
      "spans": [{
        "traceId": secrets.token_hex(16),
        "spanId": secrets.token_hex(8),
        "name": "phase2-observability-proof-span",
        "kind": 2,
        "startTimeUnixNano": str(start),
        "endTimeUnixNano": str(end)
      }]
    }]
  }]
}
with open(out, 'w', encoding='utf-8') as f:
  json.dump(payload, f)
PY

OTLP_POST_RAW="$EVIDENCE_DIR/otlp-trace-post-raw.txt"
docker run --rm --network "$NETWORK_NAME" -v "$EVIDENCE_DIR:/evidence:rw" curlimages/curl:8.12.1 \
  -sS -w "\n%{http_code}" \
  -X POST "http://steel-hammer-otel-collector:4318/v1/traces" \
  -H "Content-Type: application/json" \
  --data @/evidence/synthetic-trace.json > "$OTLP_POST_RAW"

tail -n1 "$OTLP_POST_RAW" > "$EVIDENCE_DIR/otlp-trace-post-status.txt"
sed '$d' "$OTLP_POST_RAW" > "$EVIDENCE_DIR/otlp-trace-post-response.txt"

sleep 8

log "Collecting backend evidence"
QUERY_END_NS="$(date +%s%N)"
QUERY_START_NS="$((QUERY_END_NS - 3600000000000))"
docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "http://steel-hammer-loki:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={container="steel-hammer-brazz-nossel"}' \
  --data-urlencode "start=${QUERY_START_NS}" \
  --data-urlencode "end=${QUERY_END_NS}" \
  --data-urlencode 'limit=200' \
  > "$EVIDENCE_DIR/loki-query-brazz.json" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "http://steel-hammer-loki:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={service_name=~".+"}' \
  --data-urlencode "start=${QUERY_START_NS}" \
  --data-urlencode "end=${QUERY_END_NS}" \
  --data-urlencode 'limit=200' \
  > "$EVIDENCE_DIR/loki-query-services.json" || true

run_internal_curl "http://steel-hammer-loki:3100/loki/api/v1/labels" "$EVIDENCE_DIR/loki-labels.json" || true
run_internal_curl "http://steel-hammer-tempo:3200/metrics" "$EVIDENCE_DIR/tempo-metrics.txt" || true
# Collector telemetry endpoint is frequently bound to localhost inside container.
run_container_local_curl "steel-hammer-otel-collector" "http://localhost:8888/metrics" "$EVIDENCE_DIR/otel-collector-metrics.txt" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "http://steel-hammer-mimir:9009/prometheus/api/v1/query" \
  --data-urlencode 'query=up' \
  > "$EVIDENCE_DIR/mimir-query-up.json" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "http://steel-hammer-mimir:9009/prometheus/api/v1/query" \
  --data-urlencode 'query=max_over_time(up{job="steel-hammer-keycloak"}[10m])' \
  > "$EVIDENCE_DIR/mimir-query-keycloak-up.json" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "http://steel-hammer-mimir:9009/prometheus/api/v1/query" \
  --data-urlencode 'query=max_over_time(up{job="steel-hammer-minio"}[10m])' \
  > "$EVIDENCE_DIR/mimir-query-minio-up.json" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "http://steel-hammer-mimir:9009/prometheus/api/v1/query" \
  --data-urlencode 'query=max_over_time(up{job="steel-hammer-postgres-exporter"}[10m])' \
  > "$EVIDENCE_DIR/mimir-query-postgres-exporter-up.json" || true

(
  cd "$STACK_DIR"
  docker compose -f "$COMPOSE_FILE" ps
) > "$EVIDENCE_DIR/compose-ps-final.txt"

PROM_ENDPOINTS_OK=true
for file in \
  "$EVIDENCE_DIR/buzzle-prometheus.txt" \
  "$EVIDENCE_DIR/claimspindel-prometheus.txt" \
  "$EVIDENCE_DIR/brazz-prometheus.txt" \
  "$EVIDENCE_DIR/sentinel-prometheus.txt"; do
  if [[ ! -s "$file" ]] || ! grep -q "# HELP\|# TYPE" "$file"; then
    PROM_ENDPOINTS_OK=false
  fi
done

INFRA_ENDPOINTS_CONTENT_OK=true
for file in \
  "$EVIDENCE_DIR/keycloak-metrics.txt" \
  "$EVIDENCE_DIR/minio-metrics.txt" \
  "$EVIDENCE_DIR/postgres-exporter-metrics.txt"; do
  if [[ ! -s "$file" ]] || ! grep -q "# HELP\|# TYPE" "$file"; then
    INFRA_ENDPOINTS_CONTENT_OK=false
  fi
done

OTLP_POST_STATUS="$(cat "$EVIDENCE_DIR/otlp-trace-post-status.txt" 2>/dev/null || echo "000")"
OTLP_POST_OK=false
if [[ "$OTLP_POST_STATUS" == "200" ]]; then
  OTLP_POST_OK=true
fi

TEMPO_SPANS_RECEIVED="$(awk '/tempo_distributor_spans_received_total/{sum+=$NF} END{print (sum==""?0:sum)}' "$EVIDENCE_DIR/tempo-metrics.txt" 2>/dev/null || echo 0)"
OTEL_ACCEPTED_SPANS="$(awk '/otelcol_receiver_accepted_spans(\{| )/{sum+=$NF} END{print (sum==""?0:sum)}' "$EVIDENCE_DIR/otel-collector-metrics.txt" 2>/dev/null || echo 0)"
LOKI_RESULTS_COUNT="$(json_len "$EVIDENCE_DIR/loki-query-brazz.json")"
LOKI_SERVICE_RESULTS_COUNT="$(json_len "$EVIDENCE_DIR/loki-query-services.json")"
MIMIR_STATUS="$(python3 - "$EVIDENCE_DIR/mimir-query-up.json" <<'PY'
import json,sys
try:
    with open(sys.argv[1], 'r', encoding='utf-8') as f:
        print(json.load(f).get('status','unknown'))
except Exception:
    print('unknown')
PY
)"

mimir_query_results_len() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys
try:
    with open(sys.argv[1], 'r', encoding='utf-8') as f:
        payload=json.load(f)
    results=payload.get('data',{}).get('result',[])
    print(len(results) if isinstance(results,list) else 0)
except Exception:
    print(0)
PY
}

MIMIR_KEYCLOAK_RESULTS="$(mimir_query_results_len "$EVIDENCE_DIR/mimir-query-keycloak-up.json")"
MIMIR_MINIO_RESULTS="$(mimir_query_results_len "$EVIDENCE_DIR/mimir-query-minio-up.json")"
MIMIR_POSTGRES_EXPORTER_RESULTS="$(mimir_query_results_len "$EVIDENCE_DIR/mimir-query-postgres-exporter-up.json")"

mimir_query_up_sum() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys
total=0.0
try:
  with open(sys.argv[1], 'r', encoding='utf-8') as f:
    payload=json.load(f)
  for item in payload.get('data',{}).get('result',[]):
    value=item.get('value',[])
    if isinstance(value,list) and len(value) > 1:
      try:
        total += float(value[1])
      except Exception:
        pass
except Exception:
  pass
print(total)
PY
}

MIMIR_KEYCLOAK_UP_SUM="$(mimir_query_up_sum "$EVIDENCE_DIR/mimir-query-keycloak-up.json")"
MIMIR_MINIO_UP_SUM="$(mimir_query_up_sum "$EVIDENCE_DIR/mimir-query-minio-up.json")"
MIMIR_POSTGRES_EXPORTER_UP_SUM="$(mimir_query_up_sum "$EVIDENCE_DIR/mimir-query-postgres-exporter-up.json")"

INFRA_METRICS_IN_MIMIR_OK=false
if [[ "$MIMIR_KEYCLOAK_RESULTS" =~ ^[0-9]+$ ]] && [[ "$MIMIR_MINIO_RESULTS" =~ ^[0-9]+$ ]] && [[ "$MIMIR_POSTGRES_EXPORTER_RESULTS" =~ ^[0-9]+$ ]] && \
  (( MIMIR_KEYCLOAK_RESULTS > 0 )) && (( MIMIR_MINIO_RESULTS > 0 )) && (( MIMIR_POSTGRES_EXPORTER_RESULTS > 0 )) && \
   python3 - <<PY
import sys
sys.exit(0 if (
   float("${MIMIR_KEYCLOAK_UP_SUM:-0}") >= float("${INFRA_KEYCLOAK_UP_SUM_THRESHOLD}") and
   float("${MIMIR_MINIO_UP_SUM:-0}") >= float("${INFRA_MINIO_UP_SUM_THRESHOLD}") and
   float("${MIMIR_POSTGRES_EXPORTER_UP_SUM:-0}") >= float("${INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD}")
) else 1)
PY
then
  INFRA_METRICS_IN_MIMIR_OK=true
fi

TRACES_OK=false
if python3 - <<PY
import sys
tempo=float("${TEMPO_SPANS_RECEIVED:-0}")
otel=float("${OTEL_ACCEPTED_SPANS:-0}")
sys.exit(0 if (tempo > 0 or otel > 0) else 1)
PY
then
  TRACES_OK=true
fi

LOGS_OK=false
if [[ "${LOKI_RESULTS_COUNT:-0}" =~ ^[0-9]+$ ]] && (( LOKI_RESULTS_COUNT > 0 )); then
  LOGS_OK=true
elif [[ "${LOKI_SERVICE_RESULTS_COUNT:-0}" =~ ^[0-9]+$ ]] && (( LOKI_SERVICE_RESULTS_COUNT > 0 )); then
  LOGS_OK=true
fi

METRICS_PIPELINE_OK=false
if [[ "$PROM_ENDPOINTS_OK" == "true" && "$MIMIR_STATUS" == "success" ]]; then
  METRICS_PIPELINE_OK=true
fi

cat > "$REPORT_FILE" <<EOF
# Phase 2 Observability Proof Report

- Timestamp (UTC): $TIMESTAMP
- Stack compose file: steel-hammer/docker-compose-lgtm.yml
- Evidence directory: $EVIDENCE_DIR

## Results

| Check | Result | Evidence |
|---|---|---|
| Stack readiness | $STACK_OK | compose-ps + readiness probes |
| Prometheus endpoints (services) | $PROM_ENDPOINTS_OK | buzzle/claimspindel/brazz/sentinel prometheus dumps |
| Synthetic OTLP trace accepted | $OTLP_POST_OK (HTTP $OTLP_POST_STATUS) | otlp-trace-post-status.txt |
| Trace ingestion (Tempo or OTEL metrics) | $TRACES_OK | tempo-metrics.txt + otel-collector-metrics.txt |
| Loki log query returns service streams | $LOGS_OK (container_results=$LOKI_RESULTS_COUNT, service_results=$LOKI_SERVICE_RESULTS_COUNT) | loki-query-brazz.json + loki-query-services.json |
| Mimir query status | $MIMIR_STATUS | mimir-query-up.json |
| Metrics pipeline overall | $METRICS_PIPELINE_OK | service prometheus + Mimir query |
| Infra metrics endpoints reachable | $INFRA_ENDPOINTS_READY | keycloak/minio/postgres exporter endpoint probes |
| Infra metrics endpoint content | $INFRA_ENDPOINTS_CONTENT_OK | keycloak/minio/postgres exporter metric dumps |
| Infra metrics in Mimir (Keycloak/MinIO/Postgres exporter) | $INFRA_METRICS_IN_MIMIR_OK | mimir-query-*-up.json |

## Key Counters

- tempo_distributor_spans_received_total (sum): $TEMPO_SPANS_RECEIVED
- otelcol_receiver_accepted_spans (sum): $OTEL_ACCEPTED_SPANS
- loki query result streams: $LOKI_RESULTS_COUNT
- loki query service streams: $LOKI_SERVICE_RESULTS_COUNT
- mimir keycloak up results: $MIMIR_KEYCLOAK_RESULTS
- mimir minio up results: $MIMIR_MINIO_RESULTS
- mimir postgres exporter up results: $MIMIR_POSTGRES_EXPORTER_RESULTS
- mimir keycloak up sum: $MIMIR_KEYCLOAK_UP_SUM
- mimir minio up sum: $MIMIR_MINIO_UP_SUM
- mimir postgres exporter up sum: $MIMIR_POSTGRES_EXPORTER_UP_SUM

## Thresholds

- keycloak up sum threshold: $INFRA_KEYCLOAK_UP_SUM_THRESHOLD
- minio up sum threshold: $INFRA_MINIO_UP_SUM_THRESHOLD
- postgres exporter up sum threshold: $INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD

## Conclusion

EOF

if [[ "$STACK_OK" == "true" && "$PROM_ENDPOINTS_OK" == "true" && "$OTLP_POST_OK" == "true" && "$TRACES_OK" == "true" && "$LOGS_OK" == "true" && "$INFRA_METRICS_IN_MIMIR_OK" == "true" ]]; then
  echo "✅ Phase 2 observability is operational and proven with executable evidence." >> "$REPORT_FILE"
  OVERALL_OK=true
else
  echo "❌ Phase 2 observability proof is incomplete. See evidence files for failing checks." >> "$REPORT_FILE"
  OVERALL_OK=false
fi

if [[ "$KEEP_STACK" != "true" ]]; then
  log "Stopping stack"
  (
    cd "$STACK_DIR"
    docker compose -f "$COMPOSE_FILE" down
  ) >> "$EVIDENCE_DIR/compose-down.log" 2>&1 || true
fi

log "Report generated: $REPORT_FILE"

if [[ "$OVERALL_OK" != "true" ]]; then
  exit 1
fi
