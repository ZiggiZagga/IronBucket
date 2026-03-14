#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="$ROOT_DIR/test-results/phase1-4-proof/$TIMESTAMP"
EVIDENCE_DIR="$OUT_DIR/evidence"
REPORT_FILE="$OUT_DIR/PHASE1_2_3_4_PROOF_REPORT.md"

PHASE4_MINIO_CONTAINER="minio"
PHASE4_MINIO_PORT="${PHASE4_MINIO_PORT:-19000}"
PHASE4_DOCKER_NETWORK="${PHASE4_DOCKER_NETWORK:-phase4-proof-net}"
KEEP_STACK="${KEEP_STACK:-true}"

mkdir -p "$EVIDENCE_DIR"

log() {
  printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"
}

bool_text() {
  if [[ "$1" == "true" ]]; then
    echo "true"
  else
    echo "false"
  fi
}

find_latest_phase13_report() {
  local report
  report="$(find "$ROOT_DIR/test-results/phase1-3-proof" -mindepth 2 -maxdepth 2 -name 'PHASE1_2_3_PROOF_REPORT.md' 2>/dev/null | sort | tail -1 || true)"
  echo "$report"
}

extract_phase_flag() {
  local report_file="$1"
  local label="$2"

  if [[ -z "$report_file" || ! -f "$report_file" ]]; then
    echo "false"
    return
  fi

  if grep -F "| $label | true |" "$report_file" >/dev/null 2>&1; then
    echo "true"
  else
    echo "false"
  fi
}

cleanup_phase4_container() {
  docker rm -f "$PHASE4_MINIO_CONTAINER" >/dev/null 2>&1 || true
}

ensure_phase4_network() {
  if ! docker network inspect "$PHASE4_DOCKER_NETWORK" >/dev/null 2>&1; then
    docker network create "$PHASE4_DOCKER_NETWORK" >/dev/null
  fi
}

wait_minio_ready() {
  local max_attempts="${1:-40}"

  for ((attempt=1; attempt<=max_attempts; attempt++)); do
    if curl -kfsS "https://127.0.0.1:${PHASE4_MINIO_PORT}/minio/health/live" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  return 1
}

log "Running Phase 1-3 proof gate"
set +e
KEEP_STACK="$KEEP_STACK" bash "$ROOT_DIR/scripts/e2e/prove-phase1-3-complete.sh" \
  | tee "$EVIDENCE_DIR/phase1-3-proof.log"
PHASE13_EXIT=${PIPESTATUS[0]}
set -e

PHASE13_REPORT="$(find_latest_phase13_report)"
if [[ -n "$PHASE13_REPORT" && -f "$PHASE13_REPORT" ]]; then
  cp "$PHASE13_REPORT" "$EVIDENCE_DIR/PHASE1_2_3_PROOF_REPORT.md"
fi

PHASE1_OK="$(extract_phase_flag "$PHASE13_REPORT" "Phase 1 infrastructure verification")"
PHASE2_OK="$(extract_phase_flag "$PHASE13_REPORT" "Phase 2 Alice auth + upload/get")"
PHASE3_OK="$(extract_phase_flag "$PHASE13_REPORT" "Phase 3 Bob auth + upload/get")"

PHASE4_MINIO_READY=false
PHASE4_CRUD_OK=false

log "Running Phase 4 MinIO CRUD integration gate"
cleanup_phase4_container
ensure_phase4_network
docker run -d \
  --name "$PHASE4_MINIO_CONTAINER" \
  --network "$PHASE4_DOCKER_NETWORK" \
  -p "${PHASE4_MINIO_PORT}:9000" \
  -v "$ROOT_DIR/certs:/certs:ro" \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio:latest /bin/sh -c "mkdir -p /root/.minio/certs && if [ -f /certs/services/infrastructure/minio/tls.crt ] && [ -f /certs/services/infrastructure/minio/tls.key ]; then cp /certs/services/infrastructure/minio/tls.crt /root/.minio/certs/public.crt && cp /certs/services/infrastructure/minio/tls.key /root/.minio/certs/private.key; else openssl req -x509 -nodes -newkey rsa:2048 -keyout /root/.minio/certs/private.key -out /root/.minio/certs/public.crt -days 365 -subj '/CN=minio'; fi && exec minio server /data" \
  > "$EVIDENCE_DIR/phase4-minio-container-id.txt"

if wait_minio_ready 40; then
  PHASE4_MINIO_READY=true
  log "Phase 4 MinIO container is ready"
else
  log "Phase 4 MinIO container failed readiness"
  docker logs "$PHASE4_MINIO_CONTAINER" > "$EVIDENCE_DIR/phase4-minio.log" 2>&1 || true
fi

if [[ "$PHASE4_MINIO_READY" == "true" ]]; then
  set +e
  (
    export IRONBUCKET_MINIO_ENDPOINT="https://${PHASE4_MINIO_CONTAINER}:9000"
    export IRONBUCKET_MINIO_ACCESS_KEY=minioadmin
    export IRONBUCKET_MINIO_SECRET_KEY=minioadmin
    export IRONBUCKET_MINIO_REGION=us-east-1
    export MAVEN_CONTAINER_NETWORK="$PHASE4_DOCKER_NETWORK"
    export MAVEN_DOCKER_ENV_VARS="IRONBUCKET_MINIO_ENDPOINT,IRONBUCKET_MINIO_ACCESS_KEY,IRONBUCKET_MINIO_SECRET_KEY,IRONBUCKET_MINIO_REGION"
    bash "$ROOT_DIR/scripts/ci/run-maven-in-container.sh" "services/jclouds-adapter-core" -B -V verify -Pminio-it
  ) | tee "$EVIDENCE_DIR/phase4-minio-it.log"
  PHASE4_IT_EXIT=${PIPESTATUS[0]}
  set -e

  if [[ "$PHASE4_IT_EXIT" -eq 0 ]]; then
    PHASE4_CRUD_OK=true
  fi
fi

docker logs "$PHASE4_MINIO_CONTAINER" > "$EVIDENCE_DIR/phase4-minio.log" 2>&1 || true
cleanup_phase4_container

PHASES_PASSED=0
[[ "$PHASE1_OK" == "true" ]] && PHASES_PASSED=$((PHASES_PASSED + 1))
[[ "$PHASE2_OK" == "true" ]] && PHASES_PASSED=$((PHASES_PASSED + 1))
[[ "$PHASE3_OK" == "true" ]] && PHASES_PASSED=$((PHASES_PASSED + 1))
[[ "$PHASE4_CRUD_OK" == "true" ]] && PHASES_PASSED=$((PHASES_PASSED + 1))

PHASE_COVERAGE_PERCENT=$((PHASES_PASSED * 100 / 4))

cat > "$REPORT_FILE" <<EOF
# Phase 1-4 E2E Coverage Proof Report

- Timestamp (UTC): $TIMESTAMP
- Runner: scripts/e2e/prove-phase1-4-complete.sh
- Evidence directory: $EVIDENCE_DIR

## Results by Phase

| Phase | Check | Result |
|---|---|---|
| Phase 1 | Infrastructure verification | $(bool_text "$PHASE1_OK") |
| Phase 2 | Alice auth + object upload/get | $(bool_text "$PHASE2_OK") |
| Phase 3 | Bob auth + object upload/get | $(bool_text "$PHASE3_OK") |
| Phase 4 | jclouds MinIO CRUD integration (mvn verify -Pminio-it) | $(bool_text "$PHASE4_CRUD_OK") |

## Supporting Gate Signals

| Signal | Result |
|---|---|
| Phase 1-3 proof script exit code | $PHASE13_EXIT |
| Phase 4 MinIO readiness | $(bool_text "$PHASE4_MINIO_READY") |

## Coverage

- Passed phases: $PHASES_PASSED / 4
- E2E phase coverage: ${PHASE_COVERAGE_PERCENT}%

## Gate Decision

EOF

if [[ "$PHASE_COVERAGE_PERCENT" -eq 100 ]]; then
  echo "✅ Phase 1-4 E2E coverage is 100% and verified." >> "$REPORT_FILE"
else
  echo "❌ Phase 1-4 E2E coverage is ${PHASE_COVERAGE_PERCENT}% (target: 100%)." >> "$REPORT_FILE"
fi

log "Report generated: $REPORT_FILE"

if [[ "$PHASE_COVERAGE_PERCENT" -ne 100 ]]; then
  exit 1
fi
