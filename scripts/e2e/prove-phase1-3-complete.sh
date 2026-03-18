#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STACK_DIR="$ROOT_DIR/steel-hammer"
COMPOSE_FILE="$STACK_DIR/docker-compose-steel-hammer.yml"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="$ROOT_DIR/test-results/phase1-3-proof/$TIMESTAMP"
EVIDENCE_DIR="$OUT_DIR/evidence"
REPORT_FILE="$OUT_DIR/PHASE1_2_3_PROOF_REPORT.md"
KEEP_STACK="${KEEP_STACK:-true}"

mkdir -p "$EVIDENCE_DIR"

log() {
  printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"
}

ensure_cert_artifacts_preflight() {
  local certs_dir="$ROOT_DIR/certs"
  local generator_script="$certs_dir/generate-certificates.sh"
  local required_files=(
    "ca/ca.crt"
    "ca/ca-truststore.p12"
    "services/infrastructure/keycloak/tls.crt"
    "services/infrastructure/keycloak/tls.key"
    "services/infrastructure/minio/tls.crt"
    "services/infrastructure/minio/tls.key"
    "services/infrastructure/vault/tls.crt"
    "services/infrastructure/vault/tls.key"
  )
  local rel_path

  for rel_path in "${required_files[@]}"; do
    if [[ ! -f "$certs_dir/$rel_path" ]]; then
      log "Missing certificate artifacts detected; generating certificates"
      if [[ ! -f "$generator_script" ]]; then
        echo "ERROR: Certificate generator script not found: $generator_script" >&2
        exit 1
      fi

      (
        cd "$certs_dir"
        bash "./generate-certificates.sh"
      )
      break
    fi
  done

  for rel_path in "${required_files[@]}"; do
    if [[ ! -f "$certs_dir/$rel_path" ]]; then
      echo "ERROR: Missing certificate artifact after generation: $certs_dir/$rel_path" >&2
      exit 1
    fi
  done

  log "Certificate preflight complete"
}

resolve_compose_cmd() {
  if command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
    return
  fi

  if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
    return
  fi

  echo ""
}

COMPOSE_CMD="$(resolve_compose_cmd)"
if [[ -z "$COMPOSE_CMD" ]]; then
  echo "ERROR: Neither 'docker-compose' nor 'docker compose' is available." >&2
  exit 1
fi

dc() {
  (
    cd "$STACK_DIR"
    if [[ "$COMPOSE_CMD" == "docker-compose" ]]; then
      docker-compose -f "$COMPOSE_FILE" "$@"
    else
      docker compose -f "$COMPOSE_FILE" "$@"
    fi
  )
}

wait_container_ready() {
  local container="$1"
  local max_attempts="${2:-90}"
  local delay_seconds="${3:-2}"

  for ((attempt=1; attempt<=max_attempts; attempt++)); do
    local state
    state="$(docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" 2>/dev/null || echo 'missing|missing')"
    local run_state="${state%%|*}"
    local health_state="${state##*|}"

    if [[ "$run_state" == "running" && ("$health_state" == "healthy" || "$health_state" == "none") ]]; then
      log "READY: $container ($health_state)"
      return 0
    fi

    sleep "$delay_seconds"
  done

  log "NOT READY: $container"
  return 1
}

bool_text() {
  if [[ "$1" == "true" ]]; then
    echo "true"
  else
    echo "false"
  fi
}

log "Starting/refreshing steel-hammer stack"
ensure_cert_artifacts_preflight
dc up -d > "$EVIDENCE_DIR/compose-up.log" 2>&1 || true
dc ps > "$EVIDENCE_DIR/compose-ps-initial.txt" 2>&1 || true

STACK_READY=true
wait_container_ready "steel-hammer-keycloak" || STACK_READY=false
wait_container_ready "steel-hammer-postgres" || STACK_READY=false
wait_container_ready "steel-hammer-minio" || STACK_READY=false
wait_container_ready "steel-hammer-buzzle-vane" || STACK_READY=false
wait_container_ready "steel-hammer-claimspindel" || STACK_READY=false
wait_container_ready "steel-hammer-brazz-nossel" || STACK_READY=false
wait_container_ready "steel-hammer-sentinel-gear" || STACK_READY=false
wait_container_ready "steel-hammer-graphite-forge" || STACK_READY=false

log "Restarting Graphite-Forge to refresh runtime DNS cache"
dc restart steel-hammer-graphite-forge > "$EVIDENCE_DIR/graphite-restart.log" 2>&1 || true
wait_container_ready "steel-hammer-graphite-forge" || STACK_READY=false

log "Restarting Sentinel-Gear after Graphite-Forge refresh"
dc restart steel-hammer-sentinel-gear > "$EVIDENCE_DIR/sentinel-restart.log" 2>&1 || true
wait_container_ready "steel-hammer-sentinel-gear" || STACK_READY=false

log "Running real Alice/Bob E2E scenario in internal Docker network"
set +e
dc run --rm --no-deps --entrypoint /bin/bash steel-hammer-test -lc '/workspaces/IronBucket/scripts/e2e/e2e-alice-bob-test.sh' \
  | tee "$EVIDENCE_DIR/e2e-alice-bob.log"
E2E_EXIT=${PIPESTATUS[0]}
set -e

PHASE1_INFRA_OK=false
PHASE2_ALICE_OK=false
PHASE3_BOB_OK=false
KEYCLOAK_MTLS_MINIO_OIDC_OK=false

if grep -q 'Infrastructure verification complete' "$EVIDENCE_DIR/e2e-alice-bob.log"; then
  PHASE1_INFRA_OK=true
fi

if grep -q 'Alice received JWT token' "$EVIDENCE_DIR/e2e-alice-bob.log" \
  && grep -q 'Alice upload/get via Sentinel-Gear successful' "$EVIDENCE_DIR/e2e-alice-bob.log"; then
  PHASE2_ALICE_OK=true
fi

if grep -q 'Bob received JWT token' "$EVIDENCE_DIR/e2e-alice-bob.log" \
  && grep -q 'Bob upload/get via Sentinel-Gear successful' "$EVIDENCE_DIR/e2e-alice-bob.log"; then
  PHASE3_BOB_OK=true
fi

log "Running Keycloak mTLS + MinIO OIDC web identity E2E scenario"
MINIO_OIDC_ROLE_ARN="$(docker logs steel-hammer-minio 2>&1 | grep -Eo 'arn:minio:iam:::[^[:space:]]+' | tail -n1 || true)"
if [[ -n "$MINIO_OIDC_ROLE_ARN" ]]; then
  log "Detected MinIO OIDC role ARN: $MINIO_OIDC_ROLE_ARN"
fi
set +e
dc run --rm --no-deps --entrypoint /bin/bash -e MINIO_OIDC_ROLE_ARN="$MINIO_OIDC_ROLE_ARN" steel-hammer-test -lc '/workspaces/IronBucket/scripts/e2e/e2e-keycloak-mtls-minio-oidc.sh' \
  | tee "$EVIDENCE_DIR/e2e-keycloak-mtls-minio-oidc.log"
KEYCLOAK_MTLS_MINIO_OIDC_EXIT=${PIPESTATUS[0]}
set -e

if [[ "$KEYCLOAK_MTLS_MINIO_OIDC_EXIT" -eq 0 ]]; then
  KEYCLOAK_MTLS_MINIO_OIDC_OK=true
fi

ALICE_OBJECT="$(grep -E 'Object: default-alice-files/' "$EVIDENCE_DIR/e2e-alice-bob.log" | tail -1 | sed -E 's/.*Object: //')"
BOB_OBJECT="$(grep -E 'Object: default-bob-files/' "$EVIDENCE_DIR/e2e-alice-bob.log" | tail -1 | sed -E 's/.*Object: //')"
BUCKET_CREATE_500_WARNINGS="$(grep -c 'bucket create returned 500' "$EVIDENCE_DIR/e2e-alice-bob.log" || true)"

log "Running explicit no-auth deny probe"
set +e
NOAUTH_CODE="$(dc run --rm --no-deps --entrypoint /bin/bash steel-hammer-test -lc '
curl -s -o /tmp/noauth-probe.out -w "%{http_code}" \
  "http://steel-hammer-sentinel-gear:8080/s3/object/default-alice-files/phase1-3-noauth-probe.txt"
cat /tmp/noauth-probe.out > /tmp/noauth-probe-body.txt
' | tail -n1)"
NOAUTH_EXIT=${PIPESTATUS[0]}
set -e

if [[ "$NOAUTH_EXIT" -ne 0 ]]; then
  log "No-auth deny probe execution failed (exit=$NOAUTH_EXIT)"
  NOAUTH_CODE="000"
fi

NOAUTH_DENY_OK=false
if [[ "$NOAUTH_CODE" == "401" || "$NOAUTH_CODE" == "403" ]]; then
  NOAUTH_DENY_OK=true
fi

dc ps > "$EVIDENCE_DIR/compose-ps-final.txt" 2>&1 || true

OVERALL_OK=false
if [[ "$STACK_READY" == "true" && "$E2E_EXIT" -eq 0 && "$PHASE1_INFRA_OK" == "true" && "$PHASE2_ALICE_OK" == "true" && "$PHASE3_BOB_OK" == "true" && "$KEYCLOAK_MTLS_MINIO_OIDC_OK" == "true" && "$NOAUTH_DENY_OK" == "true" ]]; then
  OVERALL_OK=true
fi

cat > "$REPORT_FILE" <<EOF
# Phase 1-3 E2E Proof Report

- Timestamp (UTC): $TIMESTAMP
- Runner: scripts/e2e/prove-phase1-3-complete.sh
- Evidence directory: $EVIDENCE_DIR

## Results

| Check | Result | Evidence |
|---|---|---|
| Stack readiness (core services healthy) | $(bool_text "$STACK_READY") | compose-ps-initial/final + docker health checks |
| Phase 1 infrastructure verification | $(bool_text "$PHASE1_INFRA_OK") | e2e-alice-bob.log |
| Phase 2 Alice auth + upload/get | $(bool_text "$PHASE2_ALICE_OK") | e2e-alice-bob.log |
| Phase 3 Bob auth + upload/get | $(bool_text "$PHASE3_BOB_OK") | e2e-alice-bob.log |
| Keycloak mTLS cert auth + MinIO OIDC web identity (bob + charly) | $(bool_text "$KEYCLOAK_MTLS_MINIO_OIDC_OK") | e2e-keycloak-mtls-minio-oidc.log |
| Unauthenticated access denied (401/403) | $(bool_text "$NOAUTH_DENY_OK") | no-auth probe HTTP=$NOAUTH_CODE |
| No-auth probe script exit code | ${NOAUTH_EXIT:-1} | no-auth probe execution |
| e2e-alice-bob script exit code | $E2E_EXIT | e2e-alice-bob.log |
| keycloak-mtls-minio-oidc script exit code | ${KEYCLOAK_MTLS_MINIO_OIDC_EXIT:-1} | e2e-keycloak-mtls-minio-oidc.log |

## Extracted Artifacts

- Alice object: ${ALICE_OBJECT:-n/a}
- Bob object: ${BOB_OBJECT:-n/a}
- Bucket-create 500 warnings: ${BUCKET_CREATE_500_WARNINGS:-0}

## Gate Decision

EOF

if [[ "$OVERALL_OK" == "true" ]]; then
  echo "✅ Phase 1-3 E2E coverage is complete and verified. Safe to proceed to Phase 4." >> "$REPORT_FILE"
else
  echo "❌ Phase 1-3 E2E gate failed. Do not proceed to Phase 4 until all checks are green." >> "$REPORT_FILE"
fi

if [[ "$KEEP_STACK" != "true" ]]; then
  log "Stopping stack (KEEP_STACK=$KEEP_STACK)"
  dc down > "$EVIDENCE_DIR/compose-down.log" 2>&1 || true
fi

log "Report generated: $REPORT_FILE"

if [[ "$OVERALL_OK" != "true" ]]; then
  exit 1
fi
