#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_FULL_ORCHESTRATOR="${RUN_FULL_ORCHESTRATOR:-false}"

log() {
  printf '[preflight] %s\n' "$*"
}

log "Starting release preflight from $ROOT_DIR"

log "Step 1/3: Backend Java test baseline"
bash "$ROOT_DIR/scripts/comprehensive-test-reporter.sh" --backend

log "Step 2/3: Sentinel roadmap + behavioral gates"
(
  cd "$ROOT_DIR/services/Sentinel-Gear"
  mvn -q -Proadmap test
  mvn -q -Pintegration test
)

if [[ "$RUN_FULL_ORCHESTRATOR" == "true" ]]; then
  log "Step 3/3: Full orchestrator (enabled)"
  bash "$ROOT_DIR/scripts/run-all-tests-complete.sh"
else
  log "Step 3/3: Full orchestrator skipped (set RUN_FULL_ORCHESTRATOR=true to enable)"
fi

log "Release preflight passed"