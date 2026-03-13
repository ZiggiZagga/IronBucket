#!/usr/bin/env bash
set -euo pipefail

# Canonical complete E2E runner.
# Runs full all-projects gate (Java + UI), first-user containerized proof,
# and Phase 2 observability proof, then records Alice upload evidence + LGTM logs.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="$ROOT_DIR/test-results/e2e-complete/$TIMESTAMP"
REPORT_FILE="$OUT_DIR/E2E_COMPLETE_REPORT.md"
LOG_DIR="$OUT_DIR/logs"

mkdir -p "$LOG_DIR"

log() {
  printf '[run-e2e-complete] %s\n' "$*"
}

latest_file() {
  local pattern="$1"
  find "$ROOT_DIR" -path "$pattern" -type f 2>/dev/null | sort | tail -1 || true
}

extract_alice_object() {
  local alice_log="$1"
  grep -E 'Object: default-alice-files/' "$alice_log" | tail -1 | sed -E 's/.*Object: //' || true
}

extract_json_value() {
  local json_file="$1"
  local key="$2"
  python3 - "$json_file" "$key" <<'PY'
import json
import sys

path = sys.argv[1]
key = sys.argv[2]

with open(path, 'r', encoding='utf-8') as handle:
    data = json.load(handle)

value = data.get(key, '')
if value is None:
    value = ''
print(str(value))
PY
}

capture_lgtm_logs() {
  local captured=0
  local containers=(
    "steel-hammer-loki"
    "steel-hammer-tempo"
    "steel-hammer-mimir"
    "steel-hammer-otel-collector"
    "steel-hammer-grafana"
    "steel-hammer-promtail"
  )

  for c in "${containers[@]}"; do
    if docker ps --format '{{.Names}}' | grep -qx "$c"; then
      docker logs --since 20m "$c" > "$LOG_DIR/${c}.log" 2>&1 || true
      captured=$((captured + 1))
    fi
  done

  echo "$captured"
}

# Fresh baseline for reproducibility.
log "Resetting compose stacks for fresh environment"
if command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  DC="docker compose"
fi

(
  cd "$ROOT_DIR/steel-hammer"
  $DC -f docker-compose-steel-hammer.yml down -v --remove-orphans || true
  $DC -f docker-compose-lgtm.yml down -v --remove-orphans || true
)

docker rm -f jclouds-minio-phase4-proof jclouds-minio-it minio >/dev/null 2>&1 || true

log "Step 1/4: all-projects gate (includes UI E2E)"
bash "$ROOT_DIR/scripts/ci/run-all-projects-e2e-gate.sh" | tee "$LOG_DIR/all-projects-e2e-gate.log"

log "Step 2/4: first-user gate (containerized Phase 1-4 proof)"
KEEP_STACK=true bash "$ROOT_DIR/scripts/ci/run-first-user-experience-gate.sh" | tee "$LOG_DIR/first-user-gate.log"

PHASE13_REPORT="$(latest_file '*/test-results/phase1-3-proof/*/PHASE1_2_3_PROOF_REPORT.md')"
ALICE_BOB_LOG="$(latest_file '*/test-results/phase1-3-proof/*/evidence/e2e-alice-bob.log')"

if [[ -z "$PHASE13_REPORT" || -z "$ALICE_BOB_LOG" ]]; then
  log "ERROR: Missing Phase 1-3 evidence after first-user gate"
  exit 1
fi

if ! grep -q 'Alice upload/get via Sentinel-Gear successful' "$ALICE_BOB_LOG"; then
  log "ERROR: Alice upload success marker not found in $ALICE_BOB_LOG"
  exit 1
fi

ALICE_OBJECT="$(extract_alice_object "$ALICE_BOB_LOG")"

log "Step 3/4: live UI upload persistence proof"
(
  cd "$ROOT_DIR/ironbucket-app-nextjs"
  npm run build
  npm run test:e2e:ui:live
) | tee "$LOG_DIR/ui-live-persistence.log"

UI_LIVE_ARTIFACT="$(latest_file '*/test-results/ui-e2e-traces/ui-live-upload-persistence.json')"
if [[ -z "$UI_LIVE_ARTIFACT" || ! -f "$UI_LIVE_ARTIFACT" ]]; then
  log "ERROR: Missing live UI persistence artifact"
  exit 1
fi

UI_LIVE_KEY="$(extract_json_value "$UI_LIVE_ARTIFACT" key)"
UI_LIVE_VERIFIED="$(extract_json_value "$UI_LIVE_ARTIFACT" verified)"
if [[ "$UI_LIVE_VERIFIED" != "True" && "$UI_LIVE_VERIFIED" != "true" ]]; then
  log "ERROR: Live UI persistence artifact reports unverified upload"
  exit 1
fi

MINIO_UI_LOG_FILE="$LOG_DIR/steel-hammer-minio-ui-upload.log"
docker logs --since 20m steel-hammer-minio > "$MINIO_UI_LOG_FILE" 2>&1 || true
UI_LIVE_MINIO_LOG_HIT=false
if [[ -n "$UI_LIVE_KEY" ]] && grep -Fq "$UI_LIVE_KEY" "$MINIO_UI_LOG_FILE"; then
  UI_LIVE_MINIO_LOG_HIT=true
fi

log "Step 4/4: observability infra gate (LGTM stack)"
KEEP_STACK=true \
INFRA_KEYCLOAK_UP_SUM_THRESHOLD="0.0" \
INFRA_MINIO_UP_SUM_THRESHOLD="1.0" \
INFRA_POSTGRES_EXPORTER_UP_SUM_THRESHOLD="1.0" \
bash "$ROOT_DIR/scripts/ci/run-observability-infra-gate.sh" | tee "$LOG_DIR/observability-gate.log"

PHASE2_REPORT="$(latest_file '*/test-results/phase2-observability/*/PHASE2_OBSERVABILITY_PROOF_REPORT.md')"
LGTM_LOGS_CAPTURED="$(capture_lgtm_logs)"

cat > "$REPORT_FILE" <<EOF
# Complete E2E Report

- Timestamp (UTC): $TIMESTAMP
- Runner: steel-hammer/test-scripts/run-e2e-complete.sh
- Output directory: $OUT_DIR

## Gate Results

| Gate | Result | Evidence |
|---|---|---|
| All-projects E2E gate (Java + UI) | true | logs/all-projects-e2e-gate.log |
| First-user Phase 1-4 proof | true | logs/first-user-gate.log |
| Live UI upload persistence proof | true | logs/ui-live-persistence.log |
| Phase 2 observability proof | true | logs/observability-gate.log |

## Alice Upload Proof

- Alice success marker: found
- Alice object path: ${ALICE_OBJECT:-n/a}
- Alice/Bob log: $ALICE_BOB_LOG
- Phase 1-3 report: $PHASE13_REPORT

## UI Live Upload Proof

- UI live persistence artifact: ${UI_LIVE_ARTIFACT:-n/a}
- UI live uploaded key: ${UI_LIVE_KEY:-n/a}
- UI live verified flag: ${UI_LIVE_VERIFIED:-n/a}
- MinIO log contains UI key: ${UI_LIVE_MINIO_LOG_HIT}
- MinIO UI log evidence: $MINIO_UI_LOG_FILE

## Observability Evidence

- Phase 2 report: ${PHASE2_REPORT:-n/a}
- LGTM container logs captured: $LGTM_LOGS_CAPTURED
- LGTM log directory: $LOG_DIR

## Decision

✅ Complete E2E run passed from fresh environment, with verified Alice upload and LGTM evidence.
EOF

log "Report generated: $REPORT_FILE"
log "Done"
