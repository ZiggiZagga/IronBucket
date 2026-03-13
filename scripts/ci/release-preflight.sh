#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_FULL_ORCHESTRATOR="${RUN_FULL_ORCHESTRATOR:-false}"
BRANCH_PROTECTION_STRICT="${BRANCH_PROTECTION_STRICT:-false}"

log() {
  printf '[preflight] %s\n' "$*"
}

log "Starting release preflight from $ROOT_DIR"

log "Step 1/6: Roadmap E2E docs/workflow sync"
bash "$ROOT_DIR/scripts/ci/verify-e2e-doc-sync.sh"

log "Step 2/6: Main branch protection policy"
BRANCH_PROTECTION_STRICT="$BRANCH_PROTECTION_STRICT" \
  bash "$ROOT_DIR/scripts/ci/verify-main-branch-protection.sh"

log "Step 3/6: Backend Java test baseline"
bash "$ROOT_DIR/scripts/comprehensive-test-reporter.sh" --backend

log "Step 4/6: Sentinel roadmap + behavioral gates"
(
  cd "$ROOT_DIR/services/Sentinel-Gear"
  mvn -q -Proadmap test
  mvn -q -Pintegration test
)

log "Step 5/6: Sentinel presigned security smoke gate"
bash "$ROOT_DIR/scripts/ci/run-presigned-security-smoke.sh"

if [[ "$RUN_FULL_ORCHESTRATOR" == "true" ]]; then
  log "Step 6/6: Full orchestrator (enabled)"
  bash "$ROOT_DIR/scripts/run-all-tests-complete.sh"
else
  log "Step 6/6: Full orchestrator skipped (set RUN_FULL_ORCHESTRATOR=true to enable)"
fi

log "Release preflight passed"