#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="$ROOT_DIR/steel-hammer"
COMPOSE_FILE="$STACK_DIR/docker-compose-lgtm.yml"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="$ROOT_DIR/test-results/phase2-performance/$TIMESTAMP"
EVIDENCE_DIR="$OUT_DIR/evidence"
REPORT_FILE="$OUT_DIR/PHASE2_PERFORMANCE_REPORT.md"
HISTORY_CSV="$ROOT_DIR/test-results/phase2-performance/performance-history.csv"

KEEP_STACK="${KEEP_STACK:-false}"
PERF_REUSE_STACK="${PERF_REUSE_STACK:-false}"
PERF_TARGET_URL="${PERF_TARGET_URL:-http://steel-hammer-graphite-forge:8084/actuator/health}"
PERF_REQUESTS="${PERF_REQUESTS:-120}"
PERF_CONCURRENCY="${PERF_CONCURRENCY:-12}"
PERF_P95_MS_THRESHOLD="${PERF_P95_MS_THRESHOLD:-350}"
PERF_RPS_THRESHOLD="${PERF_RPS_THRESHOLD:-20}"

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
    if docker run --rm --network "$NETWORK_NAME" curlimages/curl:8.12.1 -sS -f "$url" > /dev/null 2>&1; then
      log "READY: $name"
      return 0
    fi
    sleep "$delay_seconds"
  done

  log "NOT READY: $name"
  return 1
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
wait_internal_http "Graphite-Forge" "http://steel-hammer-graphite-forge:8084/actuator/health" || STACK_OK=false
wait_internal_http "Tempo" "http://steel-hammer-tempo:3200/ready" || STACK_OK=false
wait_internal_http "Loki" "http://steel-hammer-loki:3100/ready" || STACK_OK=false
wait_internal_http "Mimir" "http://steel-hammer-mimir:9009/prometheus/api/v1/status/buildinfo" || STACK_OK=false

if [[ "$STACK_OK" != "true" ]]; then
  echo "Stack not ready for performance proof" >&2
  exit 1
fi

log "Running latency/throughput load: requests=$PERF_REQUESTS concurrency=$PERF_CONCURRENCY target=$PERF_TARGET_URL"
START_NS="$(date +%s%N)"
docker run --rm --network "$NETWORK_NAME" \
  -e TARGET="$PERF_TARGET_URL" \
  -e REQUESTS="$PERF_REQUESTS" \
  -e CONCURRENCY="$PERF_CONCURRENCY" \
  curlimages/curl:8.12.1 sh -lc '
    seq "$REQUESTS" | xargs -I{} -P "$CONCURRENCY" sh -c "curl -s -o /dev/null -w \"%{http_code} %{time_total}\\n\" \"$TARGET\""
  ' > "$EVIDENCE_DIR/latency-samples.txt"
END_NS="$(date +%s%N)"

python3 - "$EVIDENCE_DIR/latency-samples.txt" "$START_NS" "$END_NS" "$EVIDENCE_DIR/performance-summary.txt" <<'PY'
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
    p95 = latencies_ms[idx95]
    avg = sum(latencies_ms) / len(latencies_ms)
else:
    p50 = p95 = avg = 0.0

with open(out_path, 'w', encoding='utf-8') as out:
    out.write(f"total={total}\n")
    out.write(f"success={success}\n")
    out.write(f"success_rate={success_rate:.2f}\n")
    out.write(f"duration_s={duration_s:.3f}\n")
    out.write(f"rps={rps:.2f}\n")
    out.write(f"latency_avg_ms={avg:.2f}\n")
    out.write(f"latency_p50_ms={p50:.2f}\n")
    out.write(f"latency_p95_ms={p95:.2f}\n")
PY

source "$EVIDENCE_DIR/performance-summary.txt"

LATENCY_OK=false
if python3 - <<PY
import sys
sys.exit(0 if float("${latency_p95_ms:-0}") <= float("${PERF_P95_MS_THRESHOLD}") else 1)
PY
then
  LATENCY_OK=true
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

mkdir -p "$(dirname "$HISTORY_CSV")"
if [[ ! -f "$HISTORY_CSV" ]]; then
  echo "timestamp,target,requests,concurrency,success_rate,rps,latency_avg_ms,latency_p50_ms,latency_p95_ms,latency_ok,throughput_ok,success_rate_ok" > "$HISTORY_CSV"
fi
echo "$TIMESTAMP,$PERF_TARGET_URL,$PERF_REQUESTS,$PERF_CONCURRENCY,$success_rate,$rps,$latency_avg_ms,$latency_p50_ms,$latency_p95_ms,$LATENCY_OK,$THROUGHPUT_OK,$SUCCESS_RATE_OK" >> "$HISTORY_CSV"

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
| Throughput (requests/sec) | $THROUGHPUT_OK | >= $PERF_RPS_THRESHOLD |
| P95 latency (ms) | $LATENCY_OK | <= $PERF_P95_MS_THRESHOLD |

## Measured Stats

- total requests: $total
- successful requests: $success
- success rate (%): $success_rate
- duration (s): $duration_s
- throughput (req/s): $rps
- latency avg (ms): $latency_avg_ms
- latency p50 (ms): $latency_p50_ms
- latency p95 (ms): $latency_p95_ms

## Continuous Tracking

- History CSV: test-results/phase2-performance/performance-history.csv

## Conclusion

EOF

OVERALL_OK=false
if [[ "$STACK_OK" == "true" && "$SUCCESS_RATE_OK" == "true" && "$LATENCY_OK" == "true" && "$THROUGHPUT_OK" == "true" ]]; then
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
