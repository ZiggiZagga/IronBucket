#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOT_DIR="${IRONBUCKET_ROOT_DIR:-$ROOT_DIR}"

WORKFLOW_FILE="${E2E_WORKFLOW_FILE:-$ROOT_DIR/.github/workflows/e2e-complete-suite.yml}"
CI_DOC_FILE="${CI_DOC_FILE:-$ROOT_DIR/docs/CI-CD-PIPELINE.md}"
E2E_DOC_FILE="${E2E_DOC_FILE:-$ROOT_DIR/docs/E2E-QUICKSTART.md}"
ROADMAP_FILE="${ROADMAP_FILE:-$ROOT_DIR/ROADMAP.md}"

PHASE14_PROOF_SCRIPT="${PHASE14_PROOF_SCRIPT:-scripts/e2e/prove-phase1-4-complete.sh}"
PHASE2_PROOF_SCRIPT="${PHASE2_PROOF_SCRIPT:-scripts/e2e/prove-phase2-observability.sh}"
FIRST_USER_GATE_SCRIPT="${FIRST_USER_GATE_SCRIPT:-scripts/ci/run-first-user-experience-gate.sh}"
OBS_INFRA_GATE_SCRIPT="${OBS_INFRA_GATE_SCRIPT:-scripts/ci/run-observability-infra-gate.sh}"
STEEL_HAMMER_E2E_SCRIPT="${STEEL_HAMMER_E2E_SCRIPT:-steel-hammer/test-scripts/e2e-complete-suite.sh}"
LEGACY_E2E_GATE_SCRIPT="${LEGACY_E2E_GATE_SCRIPT:-scripts/ci/run-all-projects-e2e-gate.sh}"

PHASE14_ARTIFACT="${PHASE14_ARTIFACT:-test-results/phase1-4-proof/}"
PHASE2_ARTIFACT="${PHASE2_ARTIFACT:-test-results/phase2-observability/}"

assert_file_exists() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "ERROR: Missing expected file: $path" >&2
    exit 1
  fi
}

assert_contains() {
  local file="$1"
  local needle="$2"
  if ! grep -Fq "$needle" "$file"; then
    echo "ERROR: Expected '$needle' in $file" >&2
    exit 1
  fi
}

assert_not_contains() {
  local file="$1"
  local needle="$2"
  if grep -Fq "$needle" "$file"; then
    echo "ERROR: Unexpected stale reference '$needle' in $file" >&2
    exit 1
  fi
}

echo "[e2e-sync] validating roadmap E2E proof references"

assert_file_exists "$WORKFLOW_FILE"
assert_file_exists "$CI_DOC_FILE"
assert_file_exists "$E2E_DOC_FILE"
assert_file_exists "$ROADMAP_FILE"
assert_file_exists "$ROOT_DIR/$PHASE14_PROOF_SCRIPT"
assert_file_exists "$ROOT_DIR/$PHASE2_PROOF_SCRIPT"
assert_file_exists "$ROOT_DIR/$FIRST_USER_GATE_SCRIPT"
assert_file_exists "$ROOT_DIR/$OBS_INFRA_GATE_SCRIPT"
assert_file_exists "$ROOT_DIR/$STEEL_HAMMER_E2E_SCRIPT"

# Workflow must execute the active first-user and observability gates and publish both artifact sets.
assert_contains "$WORKFLOW_FILE" "$FIRST_USER_GATE_SCRIPT"
assert_contains "$WORKFLOW_FILE" "$OBS_INFRA_GATE_SCRIPT"
assert_contains "$WORKFLOW_FILE" "$PHASE14_ARTIFACT"
assert_contains "$WORKFLOW_FILE" "$PHASE2_ARTIFACT"
assert_not_contains "$WORKFLOW_FILE" "$LEGACY_E2E_GATE_SCRIPT"

# CI/CD docs must mention active workflow, gate wrappers, and deterministic proof commands.
assert_contains "$CI_DOC_FILE" "e2e-complete-suite"
assert_contains "$CI_DOC_FILE" "$PHASE14_PROOF_SCRIPT"
assert_contains "$CI_DOC_FILE" "$FIRST_USER_GATE_SCRIPT"
assert_contains "$CI_DOC_FILE" "$OBS_INFRA_GATE_SCRIPT"

# E2E quickstart must document both proof scripts and steel-hammer suite location.
assert_contains "$E2E_DOC_FILE" "$PHASE14_PROOF_SCRIPT"
assert_contains "$E2E_DOC_FILE" "$PHASE2_PROOF_SCRIPT"
assert_contains "$E2E_DOC_FILE" "$STEEL_HAMMER_E2E_SCRIPT"

# Roadmap must continue to track steel-hammer complete suite evidence path.
assert_contains "$ROADMAP_FILE" "$STEEL_HAMMER_E2E_SCRIPT"

echo "[e2e-sync] workflow + docs are in sync with active Phase 1-4, Phase 2, and steel-hammer E2E references"
