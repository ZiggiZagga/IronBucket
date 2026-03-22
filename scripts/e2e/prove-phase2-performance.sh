#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="$ROOT_DIR/steel-hammer"
COMPOSE_FILE="$STACK_DIR/docker-compose-lgtm.yml"

source "$ROOT_DIR/scripts/.env.defaults"
source "$ROOT_DIR/scripts/lib/common.sh"
register_error_trap
ensure_cert_artifacts

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TEST_RESULTS_DIR="${TEST_RESULTS_DIR:-$ROOT_DIR/test-results}"

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
  echo "[prove-phase2-performance] Primary test-results directory not writable: $REQUESTED_TEST_RESULTS_DIR" >&2
  echo "[prove-phase2-performance] Using fallback test-results directory: $TEST_RESULTS_DIR" >&2
fi

OUT_DIR="$TEST_RESULTS_DIR/phase2-performance/$TIMESTAMP"
EVIDENCE_DIR="$OUT_DIR/evidence"
REPORT_FILE="$OUT_DIR/PHASE2_PERFORMANCE_REPORT.md"
HISTORY_CSV="$TEST_RESULTS_DIR/phase2-performance/performance-history.csv"

KEEP_STACK="${KEEP_STACK:-false}"
PERF_REUSE_STACK="${PERF_REUSE_STACK:-false}"
PERF_TARGET_URL="${PERF_TARGET_URL:-https://steel-hammer-graphite-forge:8084/actuator/health}"
PERF_REQUESTS="${PERF_REQUESTS:-120}"
PERF_CONCURRENCY="${PERF_CONCURRENCY:-12}"
PERF_SERVICE_REQUESTS="${PERF_SERVICE_REQUESTS:-60}"
PERF_SERVICE_CONCURRENCY="${PERF_SERVICE_CONCURRENCY:-6}"
PERF_P95_MS_THRESHOLD="${PERF_P95_MS_THRESHOLD:-350}"
PERF_P99_MS_THRESHOLD="${PERF_P99_MS_THRESHOLD:-650}"
PERF_RPS_THRESHOLD="${PERF_RPS_THRESHOLD:-20}"
PERF_ERROR_RATE_THRESHOLD="${PERF_ERROR_RATE_THRESHOLD:-1.0}"

mkdir -p "$EVIDENCE_DIR"

log() {
  printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"
}

wait_internal_http() {
  local name="$1"
  local url="$2"
  local max_attempts="${3:-90}"
  local delay_seconds="${4:-2}"

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

run_load_samples() {
  local mode="$1"
  local target="$2"
  local requests="$3"
  local concurrency="$4"
  local out_file="$5"

  if [[ "$mode" == "container" ]]; then
    local container_name="$6"
    docker run --rm --network "container:${container_name}" \
      -e TARGET="$target" \
      -e REQUESTS="$requests" \
      -e CONCURRENCY="$concurrency" \
      curlimages/curl:8.12.1 sh -lc '
        seq "$REQUESTS" | xargs -I{} -P "$CONCURRENCY" sh -c "curl -ks -o /dev/null -w \"%{http_code} %{time_total}\\n\" \"$TARGET\" || true"
      ' > "$out_file"
  else
    docker run --rm --network "$NETWORK_NAME" \
      -e TARGET="$target" \
      -e REQUESTS="$requests" \
      -e CONCURRENCY="$concurrency" \
      curlimages/curl:8.12.1 sh -lc '
        seq "$REQUESTS" | xargs -I{} -P "$CONCURRENCY" sh -c "curl -ks -o /dev/null -w \"%{http_code} %{time_total}\\n\" \"$TARGET\" || true"
      ' > "$out_file"
  fi
}

compute_summary() {
  local samples_file="$1"
  local start_ns="$2"
  local end_ns="$3"
  local out_file="$4"

  python3 - "$samples_file" "$start_ns" "$end_ns" "$out_file" <<'PY'
import math
import statistics
import sys

samples_path, start_ns, end_ns, out_path = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
latencies_ms = []
success = 0
total = 0

with open(samples_path, 'r', encoding='utf-8') as f:
    for line in f:
        parts = line.strip().split()
        if len(parts) != 2:
            continue
        code, sec = parts
        total += 1
        try:
            latency_ms = float(sec) * 1000.0
        except Exception:
            continue
        latencies_ms.append(latency_ms)
        if code.startswith('2'):
            success += 1

duration_s = max((end_ns - start_ns) / 1_000_000_000.0, 0.001)
rps = success / duration_s
success_rate = (success / total * 100.0) if total else 0.0

if latencies_ms:
    latencies_ms.sort()
    p50 = statistics.median(latencies_ms)
    idx95 = min(len(latencies_ms) - 1, math.ceil(len(latencies_ms) * 0.95) - 1)
    idx99 = min(len(latencies_ms) - 1, math.ceil(len(latencies_ms) * 0.99) - 1)
    p95 = latencies_ms[idx95]
    p99 = latencies_ms[idx99]
    avg = sum(latencies_ms) / len(latencies_ms)
else:
    p50 = p95 = p99 = avg = 0.0

error_rate = ((total - success) / total * 100.0) if total else 100.0

with open(out_path, 'w', encoding='utf-8') as out:
  out.write(f"total={total}\n")
  out.write(f"success={success}\n")
  out.write(f"success_rate={success_rate:.2f}\n")
  out.write(f"error_rate={error_rate:.2f}\n")
  out.write(f"duration_s={duration_s:.3f}\n")
  out.write(f"rps={rps:.2f}\n")
  out.write(f"latency_avg_ms={avg:.2f}\n")
  out.write(f"latency_p50_ms={p50:.2f}\n")
  out.write(f"latency_p95_ms={p95:.2f}\n")
  out.write(f"latency_p99_ms={p99:.2f}\n")
PY
}

if [[ "$PERF_REUSE_STACK" != "true" ]]; then
  log "Starting LGTM + services stack for performance proof"
  (
    cd "$STACK_DIR"
    docker compose -f "$COMPOSE_FILE" up -d --build
  ) > "$EVIDENCE_DIR/compose-up.log" 2>&1
else
  log "Reusing existing LGTM + services stack for performance proof"
fi

NETWORK_NAME="$(docker inspect steel-hammer-loki --format '{{range $k, $v := .NetworkSettings.Networks}}{{println $k}}{{end}}' 2>/dev/null | head -n1 | tr -d '\r')"
if [[ -z "$NETWORK_NAME" ]]; then
  echo "Failed to discover compose network name (is stack running?)" >&2
  exit 1
fi
log "Discovered network: $NETWORK_NAME"

STACK_OK=true
wait_internal_http "Graphite-Forge" "https://steel-hammer-graphite-forge:8084/actuator/health" || STACK_OK=false
wait_internal_http "Tempo" "https://steel-hammer-tempo:3200/ready" || STACK_OK=false
wait_internal_http "Loki" "https://steel-hammer-loki:3100/ready" || STACK_OK=false
wait_internal_http "Mimir" "https://steel-hammer-mimir:9009/prometheus/api/v1/status/buildinfo" || STACK_OK=false

if [[ "$STACK_OK" != "true" ]]; then
  echo "Stack not ready for performance proof" >&2
  exit 1
fi

log "Running latency/throughput load: requests=$PERF_REQUESTS concurrency=$PERF_CONCURRENCY target=$PERF_TARGET_URL"
START_NS="$(date +%s%N)"
run_load_samples "internal" "$PERF_TARGET_URL" "$PERF_REQUESTS" "$PERF_CONCURRENCY" "$EVIDENCE_DIR/latency-samples.txt"
END_NS="$(date +%s%N)"
compute_summary "$EVIDENCE_DIR/latency-samples.txt" "$START_NS" "$END_NS" "$EVIDENCE_DIR/performance-summary.txt"

source "$EVIDENCE_DIR/performance-summary.txt"

LATENCY_OK=false
if python3 - <<PY
import sys
sys.exit(0 if float("${latency_p95_ms:-0}") <= float("${PERF_P95_MS_THRESHOLD}") else 1)
PY
then
  LATENCY_OK=true
fi

LATENCY_P99_OK=false
if python3 - <<PY
import sys
sys.exit(0 if float("${latency_p99_ms:-0}") <= float("${PERF_P99_MS_THRESHOLD}") else 1)
PY
then
  LATENCY_P99_OK=true
fi

THROUGHPUT_OK=false
if python3 - <<PY
import sys
sys.exit(0 if float("${rps:-0}") >= float("${PERF_RPS_THRESHOLD}") else 1)
PY
then
  THROUGHPUT_OK=true
fi

SUCCESS_RATE_OK=false
if python3 - <<PY
import sys
sys.exit(0 if float("${success_rate:-0}") >= 99.0 else 1)
PY
then
  SUCCESS_RATE_OK=true
fi

ERROR_RATE_OK=false
if python3 - <<PY
import sys
sys.exit(0 if float("${error_rate:-100}") <= float("${PERF_ERROR_RATE_THRESHOLD}") else 1)
PY
then
  ERROR_RATE_OK=true
fi

mkdir -p "$EVIDENCE_DIR/service-level"
SERVICE_STATS_ROWS=""
SERVICE_LEVEL_OK=true

for service in graphite-forge buzzle-vane claimspindel brazz-nossel minio keycloak sentinel-gear-mgmt; do
  mode="internal"
  target=""
  container_name=""

  case "$service" in
    graphite-forge)
      target="https://steel-hammer-graphite-forge:8084/actuator/health"
      ;;
    buzzle-vane)
      target="https://steel-hammer-buzzle-vane:8083/actuator/health"
      ;;
    claimspindel)
      target="https://steel-hammer-claimspindel:8081/actuator/health"
      ;;
    brazz-nossel)
      target="https://steel-hammer-brazz-nossel:8082/actuator/health"
      ;;
    minio)
      target="https://steel-hammer-minio:9000/minio/health/live"
      ;;
    keycloak)
      target="https://steel-hammer-keycloak:7081/realms/dev/.well-known/openid-configuration"
      ;;
    sentinel-gear-mgmt)
      mode="container"
      container_name="steel-hammer-sentinel-gear"
      target="https://localhost:8081/actuator/health-check"
      ;;
  esac

  sample_file="$EVIDENCE_DIR/service-level/${service}-latency-samples.txt"
  summary_file="$EVIDENCE_DIR/service-level/${service}-summary.txt"

  service_start_ns="$(date +%s%N)"
  if [[ "$mode" == "container" ]]; then
    run_load_samples "$mode" "$target" "$PERF_SERVICE_REQUESTS" "$PERF_SERVICE_CONCURRENCY" "$sample_file" "$container_name"
  else
    run_load_samples "$mode" "$target" "$PERF_SERVICE_REQUESTS" "$PERF_SERVICE_CONCURRENCY" "$sample_file"
  fi
  service_end_ns="$(date +%s%N)"

  compute_summary "$sample_file" "$service_start_ns" "$service_end_ns" "$summary_file"

  service_total="$(awk -F= '/^total=/{print $2}' "$summary_file")"
  service_success_rate="$(awk -F= '/^success_rate=/{print $2}' "$summary_file")"
  service_error_rate="$(awk -F= '/^error_rate=/{print $2}' "$summary_file")"
  service_rps="$(awk -F= '/^rps=/{print $2}' "$summary_file")"
  service_p95="$(awk -F= '/^latency_p95_ms=/{print $2}' "$summary_file")"
  service_p99="$(awk -F= '/^latency_p99_ms=/{print $2}' "$summary_file")"

  service_ok="true"
  if ! python3 - <<PY
import sys
sys.exit(0 if (
  float("${service_success_rate:-0}") >= 99.0 and
  float("${service_error_rate:-100}") <= float("${PERF_ERROR_RATE_THRESHOLD}") and
  float("${service_rps:-0}") >= float("${PERF_RPS_THRESHOLD}") and
  float("${service_p95:-0}") <= float("${PERF_P95_MS_THRESHOLD}") and
  float("${service_p99:-0}") <= float("${PERF_P99_MS_THRESHOLD}")
) else 1)
PY
  then
    service_ok="false"
    SERVICE_LEVEL_OK=false
  fi

  SERVICE_STATS_ROWS+="| ${service} | ${service_total} | ${service_success_rate} | ${service_error_rate} | ${service_rps} | ${service_p95} | ${service_p99} | ${service_ok} |"$'\n'
done

mkdir -p "$(dirname "$HISTORY_CSV")"
if [[ ! -f "$HISTORY_CSV" ]]; then
  echo "timestamp,target,requests,concurrency,success_rate,error_rate,rps,latency_avg_ms,latency_p50_ms,latency_p95_ms,latency_p99_ms,latency_ok,latency_p99_ok,throughput_ok,success_rate_ok,error_rate_ok" > "$HISTORY_CSV"
fi
echo "$TIMESTAMP,$PERF_TARGET_URL,$PERF_REQUESTS,$PERF_CONCURRENCY,$success_rate,$error_rate,$rps,$latency_avg_ms,$latency_p50_ms,$latency_p95_ms,$latency_p99_ms,$LATENCY_OK,$LATENCY_P99_OK,$THROUGHPUT_OK,$SUCCESS_RATE_OK,$ERROR_RATE_OK" >> "$HISTORY_CSV"

cat > "$REPORT_FILE" <<EOF
# Phase 2 Performance Proof Report

- Timestamp (UTC): $TIMESTAMP
- Target URL: $PERF_TARGET_URL
- Requests: $PERF_REQUESTS
- Concurrency: $PERF_CONCURRENCY
- Evidence directory: $EVIDENCE_DIR

## Results

| Check | Result | Threshold |
|---|---|---|
| Stack readiness | $STACK_OK | true |
| Success rate >= 99% | $SUCCESS_RATE_OK | 99.0% |
| Error rate (%) | $ERROR_RATE_OK | <= $PERF_ERROR_RATE_THRESHOLD |
| Throughput (requests/sec) | $THROUGHPUT_OK | >= $PERF_RPS_THRESHOLD |
| P95 latency (ms) | $LATENCY_OK | <= $PERF_P95_MS_THRESHOLD |
| P99 latency (ms) | $LATENCY_P99_OK | <= $PERF_P99_MS_THRESHOLD |
| Service-level stats checks | $SERVICE_LEVEL_OK | all services meet p95/p99/rps/error thresholds |

## Measured Stats

- total requests: $total
- successful requests: $success
- success rate (%): $success_rate
- error rate (%): $error_rate
- duration (s): $duration_s
- throughput (req/s): $rps
- latency avg (ms): $latency_avg_ms
- latency p50 (ms): $latency_p50_ms
- latency p95 (ms): $latency_p95_ms
- latency p99 (ms): $latency_p99_ms

## Service-Level Stats

| Service | Requests | Success Rate (%) | Error Rate (%) | Throughput (req/s) | P95 Latency (ms) | P99 Latency (ms) | Threshold Result |
|---|---:|---:|---:|---:|---:|---:|---|
$SERVICE_STATS_ROWS

## Continuous Tracking

- History CSV: test-results/phase2-performance/performance-history.csv

## Conclusion

EOF

OVERALL_OK=false
if [[ "$STACK_OK" == "true" && "$SUCCESS_RATE_OK" == "true" && "$ERROR_RATE_OK" == "true" && "$LATENCY_OK" == "true" && "$LATENCY_P99_OK" == "true" && "$THROUGHPUT_OK" == "true" && "$SERVICE_LEVEL_OK" == "true" ]]; then
  echo "✅ Phase 2 performance proof passed." >> "$REPORT_FILE"
  OVERALL_OK=true
else
  echo "❌ Phase 2 performance proof failed. Check thresholds and evidence." >> "$REPORT_FILE"
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
