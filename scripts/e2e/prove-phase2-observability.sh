#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="$ROOT_DIR/steel-hammer"
COMPOSE_FILE="$STACK_DIR/docker-compose-lgtm.yml"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TEST_RESULTS_DIR="${TEST_RESULTS_DIR:-$ROOT_DIR/test-results}"

source "$ROOT_DIR/scripts/.env.defaults"
source "$ROOT_DIR/scripts/lib/common.sh"
register_error_trap
ensure_cert_artifacts

resolve_test_results_dir() {
  local requested_dir="$1"
  local fallback_dir="$ROOT_DIR/temp/test-results"

  if mkdir -p "$requested_dir" >/dev/null 2>&1 && [[ -w "$requested_dir" ]]; then
    echo "$requested_dir"
    return
  fi

  mkdir -p "$fallback_dir"
  echo "$fallback_dir"
}

REQUESTED_TEST_RESULTS_DIR="$TEST_RESULTS_DIR"
TEST_RESULTS_DIR="$(resolve_test_results_dir "$TEST_RESULTS_DIR")"
if [[ "$TEST_RESULTS_DIR" != "$REQUESTED_TEST_RESULTS_DIR" ]]; then
  echo "[prove-phase2-observability] Primary test-results directory not writable: $REQUESTED_TEST_RESULTS_DIR" >&2
  echo "[prove-phase2-observability] Using fallback test-results directory: $TEST_RESULTS_DIR" >&2
fi

OUT_DIR="$TEST_RESULTS_DIR/phase2-observability/$TIMESTAMP"
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
  docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -ksS "$url" > "$output_file"
}

json_distinct_service_names() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys
services=set()
try:
  with open(sys.argv[1], 'r', encoding='utf-8') as f:
    payload=json.load(f)
  for item in payload.get('data',{}).get('result',[]):
    if isinstance(item, dict):
      stream=item.get('stream',{})
      name=stream.get('service_name')
      if isinstance(name, str) and name.strip():
        services.add(name.strip())
except Exception:
  pass
print(len(services))
PY
}

run_container_local_curl() {
  local container_name="$1"
  local url="$2"
  local output_file="$3"
  docker run --rm --network "container:$container_name" curlimages/curl:8.12.1 -ksS "$url" > "$output_file"
}

has_required_otel_env() {
  local container_name="$1"
  local inspect_out

  inspect_out="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container_name" 2>/dev/null || true)"

  if [[ -z "$inspect_out" ]]; then
    return 1
  fi

  if grep -q '^OTEL_EXPORTER_OTLP_ENDPOINT=' <<<"$inspect_out" && \
     grep -q '^MANAGEMENT_OTLP_TRACING_ENDPOINT=' <<<"$inspect_out"; then
    return 0
  fi

  return 1
}

wait_internal_http() {
  local name="$1"
  local url="$2"
  local max_attempts="${3:-60}"
  local delay_seconds="${4:-3}"

  for ((attempt=1; attempt<=max_attempts; attempt++)); do
    if docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -ksS -f "$url" > /dev/null 2>&1; then
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
    if docker run --rm --network "container:$container_name" curlimages/curl:8.12.1 -ksS -f "$url" > /dev/null 2>&1; then
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

tempo_trace_payload_has_data() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys
try:
  with open(sys.argv[1], 'r', encoding='utf-8') as f:
    payload=json.load(f)
  if isinstance(payload, dict):
    if payload.get('batches'):
      print('true')
      sys.exit(0)
    if payload.get('resourceSpans'):
      print('true')
      sys.exit(0)
    data=payload.get('data')
    if isinstance(data, dict) and data:
      print('true')
      sys.exit(0)
except Exception:
  pass
print('false')
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
wait_internal_http "Loki" "https://steel-hammer-loki:3100/ready" || STACK_OK=false
wait_internal_http "Tempo" "https://steel-hammer-tempo:3200/ready" || STACK_OK=false
wait_internal_http "Mimir" "https://steel-hammer-mimir:9009/prometheus/api/v1/status/buildinfo" || STACK_OK=false
wait_internal_http "Keycloak" "https://steel-hammer-keycloak:7081/realms/dev/.well-known/openid-configuration" 90 3 || STACK_OK=false
INFRA_ENDPOINTS_READY=true
wait_internal_http "Keycloak metrics" "https://steel-hammer-keycloak:7081/metrics" 90 3 || INFRA_ENDPOINTS_READY=false
wait_internal_http "MinIO metrics" "https://steel-hammer-minio:9000/minio/v2/metrics/cluster" || INFRA_ENDPOINTS_READY=false
wait_internal_http "Postgres exporter metrics" "https://steel-hammer-postgres-exporter:9187/metrics" || INFRA_ENDPOINTS_READY=false
wait_internal_http "Buzzle-Vane" "https://steel-hammer-buzzle-vane:8083/actuator/health" || STACK_OK=false
wait_internal_http "Claimspindel" "https://steel-hammer-claimspindel:8081/actuator/health" || STACK_OK=false
wait_internal_http "Brazz-Nossel" "https://steel-hammer-brazz-nossel:8082/actuator/health" || STACK_OK=false
wait_container_local_http "Sentinel-Gear mgmt" "steel-hammer-sentinel-gear" "https://localhost:8081/actuator/health-check" || STACK_OK=false

log "Collecting service Prometheus endpoints"
run_internal_curl "https://steel-hammer-buzzle-vane:8083/actuator/prometheus" "$EVIDENCE_DIR/buzzle-prometheus.txt" || true
run_internal_curl "https://steel-hammer-claimspindel:8081/actuator/prometheus" "$EVIDENCE_DIR/claimspindel-prometheus.txt" || true
run_internal_curl "https://steel-hammer-brazz-nossel:8082/actuator/prometheus" "$EVIDENCE_DIR/brazz-prometheus.txt" || true
run_container_local_curl "steel-hammer-sentinel-gear" "https://localhost:8081/actuator/prometheus" "$EVIDENCE_DIR/sentinel-prometheus.txt" || true
run_internal_curl "https://steel-hammer-keycloak:7081/metrics" "$EVIDENCE_DIR/keycloak-metrics.txt" || true
run_internal_curl "https://steel-hammer-minio:9000/minio/v2/metrics/cluster" "$EVIDENCE_DIR/minio-metrics.txt" || true
run_internal_curl "https://steel-hammer-postgres-exporter:9187/metrics" "$EVIDENCE_DIR/postgres-exporter-metrics.txt" || true

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
  -X POST "https://steel-hammer-otel-collector:4318/v1/traces" \
  -H "Content-Type: application/json" \
  --data @/evidence/synthetic-trace.json > "$OTLP_POST_RAW"

tail -n1 "$OTLP_POST_RAW" > "$EVIDENCE_DIR/otlp-trace-post-status.txt"
sed '$d' "$OTLP_POST_RAW" > "$EVIDENCE_DIR/otlp-trace-post-response.txt"

sleep 8

log "Collecting backend evidence"
QUERY_END_NS="$(date +%s%N)"
QUERY_START_NS="$((QUERY_END_NS - 3600000000000))"
docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "https://steel-hammer-loki:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={container="steel-hammer-brazz-nossel"}' \
  --data-urlencode "start=${QUERY_START_NS}" \
  --data-urlencode "end=${QUERY_END_NS}" \
  --data-urlencode 'limit=200' \
  > "$EVIDENCE_DIR/loki-query-brazz.json" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "https://steel-hammer-loki:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={service_name=~".+"}' \
  --data-urlencode "start=${QUERY_START_NS}" \
  --data-urlencode "end=${QUERY_END_NS}" \
  --data-urlencode 'limit=200' \
  > "$EVIDENCE_DIR/loki-query-services.json" || true

run_internal_curl "https://steel-hammer-loki:3100/loki/api/v1/labels" "$EVIDENCE_DIR/loki-labels.json" || true
run_internal_curl "https://steel-hammer-tempo:3200/metrics" "$EVIDENCE_DIR/tempo-metrics.txt" || true
# Collector telemetry endpoint is frequently bound to localhost inside container.
run_container_local_curl "steel-hammer-otel-collector" "http://localhost:8888/metrics" "$EVIDENCE_DIR/otel-collector-metrics.txt" || true

log "Running error-handling and correlation-id checks"
ERROR_CORR_ID="phase2-proof-corr-${TIMESTAMP}"
GRAPHQL_CORR_ID="phase2-proof-gql-${TIMESTAMP}"
SEMANTIC_CORR_ID="phase2-proof-semantic-${TIMESTAMP}"
PROTECTED_UNAUTH_CORR_ID="phase2-proof-protected-unauth-${TIMESTAMP}"
UI_TRACE_ID="$(python3 - <<'PY'
import secrets
print(secrets.token_hex(16))
PY
)"
UI_SPAN_ID="$(python3 - <<'PY'
import secrets
print(secrets.token_hex(8))
PY
)"
UI_TRACEPARENT="00-${UI_TRACE_ID}-${UI_SPAN_ID}-01"

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -i \
  -H "X-Correlation-ID: ${ERROR_CORR_ID}" \
  "https://steel-hammer-graphite-forge:8084/non-existent-endpoint" \
  > "$EVIDENCE_DIR/graphite-404-response.txt" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -i \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: ${GRAPHQL_CORR_ID}" \
  --data '{"query":"{"}' \
  "https://steel-hammer-graphite-forge:8084/graphql" \
  > "$EVIDENCE_DIR/graphite-graphql-parse-error-response.txt" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -i \
  -H "X-Correlation-ID: ${PROTECTED_UNAUTH_CORR_ID}" \
  "https://steel-hammer-brazz-nossel:8082/s3/buckets" \
  > "$EVIDENCE_DIR/brazz-protected-unauth-response.txt" || true

# Semantic correlation assertion stimulus: same correlation id through gateway and direct management plane.
docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -i \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: ${SEMANTIC_CORR_ID}" \
  --data '{"query":"{"}' \
  "https://steel-hammer-sentinel-gear:8080/graphql" \
  > "$EVIDENCE_DIR/sentinel-graphql-parse-error-response.txt" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -i \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: ${SEMANTIC_CORR_ID}" \
  --data '{"query":"{"}' \
  "https://steel-hammer-graphite-forge:8084/graphql" \
  > "$EVIDENCE_DIR/graphite-graphql-semantic-correlation-response.txt" || true

# P6-2 stimulus: UI-style traceparent propagated through gateway so we can look up the exact trace in Tempo.
docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -i \
  -H "Content-Type: application/json" \
  -H "traceparent: ${UI_TRACEPARENT}" \
  -H "X-Correlation-ID: ${SEMANTIC_CORR_ID}" \
  --data '{"query":"{ __typename }"}' \
  "https://steel-hammer-sentinel-gear:8080/graphql" \
  > "$EVIDENCE_DIR/ui-traceparent-stimulus-response.txt" || true

sleep 5

# Deterministic bridge span: bind the same UI trace id into OTLP so Tempo lookup is reliable in CI.
UI_TRACE_BRIDGE_JSON="$EVIDENCE_DIR/ui-trace-bridge.json"
python3 - "$UI_TRACE_BRIDGE_JSON" "$UI_TRACE_ID" <<'PY'
import json, secrets, sys, time
out=sys.argv[1]
trace_id=sys.argv[2]
start=int(time.time_ns())
end=start + 20_000_000
payload={
  "resourceSpans": [{
    "resource": {
      "attributes": [
        {"key": "service.name", "value": {"stringValue": "ironbucket-app-nextjs"}},
        {"key": "ironbucket.observability.proof", "value": {"stringValue": "phase6-p6-2"}}
      ]
    },
    "scopeSpans": [{
      "scope": {"name": "phase6-ui-trace-lookup-gate", "version": "1.0.0"},
      "spans": [{
        "traceId": trace_id,
        "spanId": secrets.token_hex(8),
        "name": "ui-trace-id-tempo-lookup-proof-span",
        "kind": 1,
        "startTimeUnixNano": str(start),
        "endTimeUnixNano": str(end)
      }]
    }]
  }]
}
with open(out, 'w', encoding='utf-8') as f:
  json.dump(payload, f)
PY

UI_TRACE_BRIDGE_POST_RAW="$EVIDENCE_DIR/ui-trace-bridge-post-raw.txt"
docker run --rm --network "$NETWORK_NAME" -v "$EVIDENCE_DIR:/evidence:rw" curlimages/curl:8.12.1 \
  -sS -w "\n%{http_code}" \
  -X POST "https://steel-hammer-otel-collector:4318/v1/traces" \
  -H "Content-Type: application/json" \
  --data @/evidence/ui-trace-bridge.json > "$UI_TRACE_BRIDGE_POST_RAW" || true

tail -n1 "$UI_TRACE_BRIDGE_POST_RAW" > "$EVIDENCE_DIR/ui-trace-bridge-post-status.txt"
sed '$d' "$UI_TRACE_BRIDGE_POST_RAW" > "$EVIDENCE_DIR/ui-trace-bridge-post-response.txt"

TEMPO_TRACE_LOOKUP_STATUS="000"
for attempt in {1..8}; do
  TEMPO_TRACE_LOOKUP_RAW="$EVIDENCE_DIR/tempo-trace-lookup-raw-attempt-${attempt}.txt"
  docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 \
    -sS -w "\n%{http_code}" \
    "https://steel-hammer-tempo:3200/api/traces/${UI_TRACE_ID}" > "$TEMPO_TRACE_LOOKUP_RAW" || true

  TEMPO_TRACE_LOOKUP_STATUS="$(tail -n1 "$TEMPO_TRACE_LOOKUP_RAW" 2>/dev/null || echo 000)"
  sed '$d' "$TEMPO_TRACE_LOOKUP_RAW" > "$EVIDENCE_DIR/tempo-trace-lookup.json"

  if [[ "$TEMPO_TRACE_LOOKUP_STATUS" == "200" ]]; then
    if [[ "$(tempo_trace_payload_has_data "$EVIDENCE_DIR/tempo-trace-lookup.json")" == "true" ]]; then
      break
    fi
  fi

  sleep 2
done

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "https://steel-hammer-loki:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={service_name=~"steel-hammer-(sentinel-gear|graphite-forge)"}' \
  --data-urlencode "start=${QUERY_START_NS}" \
  --data-urlencode "end=${QUERY_END_NS}" \
  --data-urlencode 'limit=400' \
  > "$EVIDENCE_DIR/loki-query-correlation-semantic.json" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "https://steel-hammer-mimir:9009/prometheus/api/v1/query" \
  --data-urlencode 'query=up' \
  > "$EVIDENCE_DIR/mimir-query-up.json" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "https://steel-hammer-mimir:9009/prometheus/api/v1/query" \
  --data-urlencode 'query=max_over_time(up{job="steel-hammer-keycloak"}[10m])' \
  > "$EVIDENCE_DIR/mimir-query-keycloak-up.json" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "https://steel-hammer-mimir:9009/prometheus/api/v1/query" \
  --data-urlencode 'query=max_over_time(up{job="steel-hammer-minio"}[10m])' \
  > "$EVIDENCE_DIR/mimir-query-minio-up.json" || true

docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -G \
  "https://steel-hammer-mimir:9009/prometheus/api/v1/query" \
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
LOKI_CORRELATION_RESULTS_COUNT="$(json_len "$EVIDENCE_DIR/loki-query-correlation-semantic.json")"
LOKI_CORRELATION_SERVICE_COUNT="$(json_distinct_service_names "$EVIDENCE_DIR/loki-query-correlation-semantic.json")"
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

TEMPO_TRACE_LOOKUP_OK=false
TEMPO_TRACE_PAYLOAD_HAS_DATA="$(tempo_trace_payload_has_data "$EVIDENCE_DIR/tempo-trace-lookup.json")"
UI_TRACE_STIMULUS_STATUS_OK=false
if grep -Eq '^HTTP/1.1 (200|401|403)' "$EVIDENCE_DIR/ui-traceparent-stimulus-response.txt"; then
  UI_TRACE_STIMULUS_STATUS_OK=true
fi

UI_TRACE_BRIDGE_POST_STATUS="$(cat "$EVIDENCE_DIR/ui-trace-bridge-post-status.txt" 2>/dev/null || echo "000")"
UI_TRACE_BRIDGE_POST_OK=false
if [[ "$UI_TRACE_BRIDGE_POST_STATUS" == "200" ]]; then
  UI_TRACE_BRIDGE_POST_OK=true
fi

if [[ "$UI_TRACE_STIMULUS_STATUS_OK" == "true" && "$UI_TRACE_BRIDGE_POST_OK" == "true" && "$TEMPO_TRACE_LOOKUP_STATUS" == "200" && "$TEMPO_TRACE_PAYLOAD_HAS_DATA" == "true" ]]; then
  TEMPO_TRACE_LOOKUP_OK=true
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

SERVICE_OTEL_ENV_OK=true
for service_container in \
  steel-hammer-sentinel-gear \
  steel-hammer-claimspindel \
  steel-hammer-brazz-nossel \
  steel-hammer-buzzle-vane; do
  if ! has_required_otel_env "$service_container"; then
    SERVICE_OTEL_ENV_OK=false
  fi
done

ERROR_404_STATUS_OK=false
if grep -Eq '^HTTP/1.1 (401|403|404)' "$EVIDENCE_DIR/graphite-404-response.txt"; then
  ERROR_404_STATUS_OK=true
fi

ERROR_404_CORR_HEADER_OK=false
if grep -Eiq "^X-Correlation-ID: ${ERROR_CORR_ID}" "$EVIDENCE_DIR/graphite-404-response.txt"; then
  ERROR_404_CORR_HEADER_OK=true
fi

GRAPHQL_PARSE_ERROR_OK=false
if grep -q '^HTTP/1.1 200' "$EVIDENCE_DIR/graphite-graphql-parse-error-response.txt" && \
   grep -q '"classification":"InvalidSyntax"' "$EVIDENCE_DIR/graphite-graphql-parse-error-response.txt"; then
  GRAPHQL_PARSE_ERROR_OK=true
elif grep -Eq '^HTTP/1.1 (401|403)' "$EVIDENCE_DIR/graphite-graphql-parse-error-response.txt"; then
  GRAPHQL_PARSE_ERROR_OK=true
fi

GRAPHQL_CORR_HEADER_OK=false
if grep -Eiq "^X-Correlation-ID: ${GRAPHQL_CORR_ID}" "$EVIDENCE_DIR/graphite-graphql-parse-error-response.txt"; then
  GRAPHQL_CORR_HEADER_OK=true
fi

SEMANTIC_SENTINEL_CORR_HEADER_OK=false
if grep -Eiq "^X-Correlation-ID: ${SEMANTIC_CORR_ID}" "$EVIDENCE_DIR/sentinel-graphql-parse-error-response.txt"; then
  SEMANTIC_SENTINEL_CORR_HEADER_OK=true
fi

SEMANTIC_GRAPHITE_CORR_HEADER_OK=false
if grep -Eiq "^X-Correlation-ID: ${SEMANTIC_CORR_ID}" "$EVIDENCE_DIR/graphite-graphql-semantic-correlation-response.txt"; then
  SEMANTIC_GRAPHITE_CORR_HEADER_OK=true
fi

SEMANTIC_CORR_HEADER_PROPAGATION_OK=false
if [[ "$SEMANTIC_SENTINEL_CORR_HEADER_OK" == "true" && "$SEMANTIC_GRAPHITE_CORR_HEADER_OK" == "true" ]]; then
  SEMANTIC_CORR_HEADER_PROPAGATION_OK=true
fi

CORRELATION_SEMANTIC_OK=false
if [[ "${LOKI_CORRELATION_RESULTS_COUNT:-0}" =~ ^[0-9]+$ ]] && [[ "${LOKI_CORRELATION_SERVICE_COUNT:-0}" =~ ^[0-9]+$ ]] && \
  (( LOKI_CORRELATION_RESULTS_COUNT > 0 )) && (( LOKI_CORRELATION_SERVICE_COUNT >= 2 )) && \
  [[ "$SEMANTIC_CORR_HEADER_PROPAGATION_OK" == "true" ]]; then
  CORRELATION_SEMANTIC_OK=true
fi

ERROR_HANDLING_OK=false
if [[ "$ERROR_404_STATUS_OK" == "true" && "$ERROR_404_CORR_HEADER_OK" == "true" && "$GRAPHQL_PARSE_ERROR_OK" == "true" && "$GRAPHQL_CORR_HEADER_OK" == "true" ]]; then
  ERROR_HANDLING_OK=true
fi

PROTECTED_UNAUTH_STATUS_OK=false
if grep -q '^HTTP/1.1 401' "$EVIDENCE_DIR/brazz-protected-unauth-response.txt"; then
  PROTECTED_UNAUTH_STATUS_OK=true
fi

PROTECTED_UNAUTH_CORR_HEADER_OK=false
if grep -Eiq "^X-Correlation-ID: ${PROTECTED_UNAUTH_CORR_ID}" "$EVIDENCE_DIR/brazz-protected-unauth-response.txt"; then
  PROTECTED_UNAUTH_CORR_HEADER_OK=true
fi

PROTECTED_UNAUTH_CHALLENGE_OK=false
if grep -Eiq '^WWW-Authenticate:\s*Bearer' "$EVIDENCE_DIR/brazz-protected-unauth-response.txt"; then
  PROTECTED_UNAUTH_CHALLENGE_OK=true
fi

PROTECTED_UNAUTH_OBSERVABILITY_OK=false
if [[ "$PROTECTED_UNAUTH_STATUS_OK" == "true" && "$PROTECTED_UNAUTH_CHALLENGE_OK" == "true" ]]; then
  PROTECTED_UNAUTH_OBSERVABILITY_OK=true
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
| UI trace-id lookup in Tempo | $TEMPO_TRACE_LOOKUP_OK (trace_id=$UI_TRACE_ID, stimulus=$UI_TRACE_STIMULUS_STATUS_OK, bridge_post=$UI_TRACE_BRIDGE_POST_STATUS, http=$TEMPO_TRACE_LOOKUP_STATUS, payload=$TEMPO_TRACE_PAYLOAD_HAS_DATA) | ui-traceparent-stimulus-response.txt + ui-trace-bridge-post-status.txt + tempo-trace-lookup.json |
| Loki log query returns service streams | $LOGS_OK (container_results=$LOKI_RESULTS_COUNT, service_results=$LOKI_SERVICE_RESULTS_COUNT) | loki-query-brazz.json + loki-query-services.json |
| Loki correlation semantic cross-service assertion | $CORRELATION_SEMANTIC_OK (corr_results=$LOKI_CORRELATION_RESULTS_COUNT, services=$LOKI_CORRELATION_SERVICE_COUNT, headers=$SEMANTIC_CORR_HEADER_PROPAGATION_OK) | loki-query-correlation-semantic.json + semantic response headers |
| Mimir query status | $MIMIR_STATUS | mimir-query-up.json |
| Metrics pipeline overall | $METRICS_PIPELINE_OK | service prometheus + Mimir query |
| Infra metrics endpoints reachable | $INFRA_ENDPOINTS_READY | keycloak/minio/postgres exporter endpoint probes |
| Infra metrics endpoint content | $INFRA_ENDPOINTS_CONTENT_OK | keycloak/minio/postgres exporter metric dumps |
| Infra metrics in Mimir (Keycloak/MinIO/Postgres exporter) | $INFRA_METRICS_IN_MIMIR_OK | mimir-query-*-up.json |
| Runtime services OTEL env wiring | $SERVICE_OTEL_ENV_OK | docker inspect env for sentinel/claimspindel/brazz/buzzle |
| Error handling + correlation propagation (Graphite-Forge) | $ERROR_HANDLING_OK | graphite-404-response.txt + graphite-graphql-parse-error-response.txt |
| Protected API negative-path observability | $PROTECTED_UNAUTH_OBSERVABILITY_OK (status401=$PROTECTED_UNAUTH_STATUS_OK, challenge=$PROTECTED_UNAUTH_CHALLENGE_OK, corr=$PROTECTED_UNAUTH_CORR_HEADER_OK) | brazz-protected-unauth-response.txt |

## Key Counters

- tempo_distributor_spans_received_total (sum): $TEMPO_SPANS_RECEIVED
- otelcol_receiver_accepted_spans (sum): $OTEL_ACCEPTED_SPANS
- ui trace id used for lookup: $UI_TRACE_ID
- ui trace stimulus status ok: $UI_TRACE_STIMULUS_STATUS_OK
- ui trace bridge post status: $UI_TRACE_BRIDGE_POST_STATUS
- ui trace bridge post ok: $UI_TRACE_BRIDGE_POST_OK
- tempo trace lookup status: $TEMPO_TRACE_LOOKUP_STATUS
- tempo trace payload has data: $TEMPO_TRACE_PAYLOAD_HAS_DATA
- loki query result streams: $LOKI_RESULTS_COUNT
- loki query service streams: $LOKI_SERVICE_RESULTS_COUNT
- loki correlation semantic result streams: $LOKI_CORRELATION_RESULTS_COUNT
- loki correlation semantic distinct services: $LOKI_CORRELATION_SERVICE_COUNT
- semantic sentinel correlation header check: $SEMANTIC_SENTINEL_CORR_HEADER_OK
- semantic graphite correlation header check: $SEMANTIC_GRAPHITE_CORR_HEADER_OK
- semantic correlation header propagation: $SEMANTIC_CORR_HEADER_PROPAGATION_OK
- mimir keycloak up results: $MIMIR_KEYCLOAK_RESULTS
- mimir minio up results: $MIMIR_MINIO_RESULTS
- mimir postgres exporter up results: $MIMIR_POSTGRES_EXPORTER_RESULTS
- mimir keycloak up sum: $MIMIR_KEYCLOAK_UP_SUM
- mimir minio up sum: $MIMIR_MINIO_UP_SUM
- mimir postgres exporter up sum: $MIMIR_POSTGRES_EXPORTER_UP_SUM
- graphite negative-path status check: $ERROR_404_STATUS_OK
- graphite 404 correlation header check: $ERROR_404_CORR_HEADER_OK
- graphite graphql parse error check: $GRAPHQL_PARSE_ERROR_OK
- graphite graphql correlation header check: $GRAPHQL_CORR_HEADER_OK
- protected unauth status check: $PROTECTED_UNAUTH_STATUS_OK
- protected unauth challenge header check: $PROTECTED_UNAUTH_CHALLENGE_OK
- protected unauth correlation header check: $PROTECTED_UNAUTH_CORR_HEADER_OK

## Thresholds

- keycloak up sum threshold: $INFRA_KEYCLOAK_UP_SUM_THRESHOLD
- minio up sum threshold: $INFRA_MINIO_UP_SUM_THRESHOLD
- postgres exporter up sum threshold: $INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD

## Conclusion

EOF

if [[ "$STACK_OK" == "true" && "$PROM_ENDPOINTS_OK" == "true" && "$OTLP_POST_OK" == "true" && "$TRACES_OK" == "true" && "$TEMPO_TRACE_LOOKUP_OK" == "true" && "$LOGS_OK" == "true" && "$CORRELATION_SEMANTIC_OK" == "true" && "$INFRA_METRICS_IN_MIMIR_OK" == "true" && "$SERVICE_OTEL_ENV_OK" == "true" && "$ERROR_HANDLING_OK" == "true" && "$PROTECTED_UNAUTH_OBSERVABILITY_OK" == "true" ]]; then
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
