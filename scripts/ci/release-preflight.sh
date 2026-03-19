#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_FULL_ORCHESTRATOR="${RUN_FULL_ORCHESTRATOR:-false}"
BRANCH_PROTECTION_STRICT="${BRANCH_PROTECTION_STRICT:-false}"
VERIFY_BRANCH_PROTECTION="${VERIFY_BRANCH_PROTECTION:-$BRANCH_PROTECTION_STRICT}"

log() {
  printf '[preflight] %s\n' "$*"
}

log "Starting release preflight from $ROOT_DIR"

log "Step 1/7: Roadmap E2E docs/workflow sync"
bash "$ROOT_DIR/scripts/ci/verify-e2e-doc-sync.sh"

log "Step 2/7: Containerized test policy"
bash "$ROOT_DIR/scripts/ci/verify-containerized-tests-only.sh"

if [[ "$VERIFY_BRANCH_PROTECTION" == "true" ]]; then
  log "Step 3/7: Main branch protection required checks"
  BRANCH_PROTECTION_STRICT="$BRANCH_PROTECTION_STRICT" \
  GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-ZiggiZagga/IronBucket}" \
  TARGET_BRANCH="${TARGET_BRANCH:-main}" \
    bash "$ROOT_DIR/scripts/ci/verify-main-branch-protection.sh"
else
  log "Step 3/7: Main branch protection check skipped (set VERIFY_BRANCH_PROTECTION=true or BRANCH_PROTECTION_STRICT=true to enable)"
fi

log "Step 4/7: Backend Java test baseline (containers only)"
bash "$ROOT_DIR/scripts/ci/run-core-module-tests-container.sh"

log "Step 5/7: Sentinel roadmap + behavioral gates (containers only)"
bash "$ROOT_DIR/scripts/ci/run-sentinel-roadmap-gate.sh"
BEHAVIORAL_GATE_STRICT=true bash "$ROOT_DIR/scripts/ci/run-sentinel-behavioral-gate.sh"

log "Step 6/8: Enterprise admin runbook evidence gate"
bash "$ROOT_DIR/scripts/ci/run-enterprise-admin-runbook-gate.sh"

log "Step 7/8: Sentinel presigned security smoke gate"
bash "$ROOT_DIR/scripts/ci/run-presigned-security-smoke.sh"

if [[ "$RUN_FULL_ORCHESTRATOR" == "true" ]]; then
  log "Step 8/8: Full orchestrator (enabled)"
  bash "$ROOT_DIR/scripts/run-all-tests-complete.sh"
else
  log "Step 8/8: Full orchestrator skipped (set RUN_FULL_ORCHESTRATOR=true to enable)"
fi

log "Release preflight passed"