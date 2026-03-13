#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

ROOT_DIR="${IRONBUCKET_ROOT_DIR:-$ROOT_DIR}"

WORKFLOW_FILE="${E2E_WORKFLOW_FILE:-$ROOT_DIR/.github/workflows/e2e-complete-suite.yml}"
CI_DOC_FILE="${CI_DOC_FILE:-$ROOT_DIR/docs/CI-CD-PIPELINE.md}"
E2E_DOC_FILE="${E2E_DOC_FILE:-$ROOT_DIR/docs/E2E-QUICKSTART.md}"
E2E_OBS_DOC_FILE="${E2E_OBS_DOC_FILE:-$ROOT_DIR/docs/E2E-OBSERVABILITY-GUIDE.md}"
README_FILE="${README_FILE:-$ROOT_DIR/README.md}"
PROOF_SCRIPT="${PHASE14_PROOF_SCRIPT:-scripts/e2e/prove-phase1-4-complete.sh}"
PHASE2_PROOF_SCRIPT="${PHASE2_PROOF_SCRIPT:-scripts/e2e/prove-phase2-observability.sh}"
FIRST_USER_GATE_SCRIPT="${FIRST_USER_GATE_SCRIPT:-scripts/ci/run-first-user-experience-gate.sh}"
OBS_INFRA_GATE_SCRIPT="${OBS_INFRA_GATE_SCRIPT:-scripts/ci/run-observability-infra-gate.sh}"
ALL_PROJECTS_GATE_SCRIPT="${ALL_PROJECTS_GATE_SCRIPT:-scripts/ci/run-all-projects-e2e-gate.sh}"
PHASE14_ARTIFACT="${PHASE14_ARTIFACT:-test-results/phase1-4-proof/}"
PHASE2_ARTIFACT="${PHASE2_ARTIFACT:-test-results/phase2-observability/}"
UI_TRACE_ARTIFACT="${UI_TRACE_ARTIFACT:-test-results/ui-e2e-traces/}"
UI_BASELINE_TEST="${UI_BASELINE_TEST:-tests/ui-live-upload-persistence.spec.ts}"

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

echo "[e2e-sync] validating roadmap E2E proof references"

assert_file_exists "$WORKFLOW_FILE"
assert_file_exists "$CI_DOC_FILE"
assert_file_exists "$E2E_DOC_FILE"
assert_file_exists "$E2E_OBS_DOC_FILE"
assert_file_exists "$README_FILE"
assert_file_exists "$ROOT_DIR/$PROOF_SCRIPT"
assert_file_exists "$ROOT_DIR/$PHASE2_PROOF_SCRIPT"
assert_file_exists "$ROOT_DIR/$ALL_PROJECTS_GATE_SCRIPT"

# Workflow must execute the current roadmap proof gate and upload phase 1-4 artifacts.
if grep -Fq "$PROOF_SCRIPT" "$WORKFLOW_FILE" || grep -Fq "$FIRST_USER_GATE_SCRIPT" "$WORKFLOW_FILE"; then
  :
else
  echo "ERROR: Expected '$PROOF_SCRIPT' or '$FIRST_USER_GATE_SCRIPT' in $WORKFLOW_FILE" >&2
  exit 1
fi

# Workflow must execute the observability proof gate and upload phase 2 artifacts.
if grep -Fq "$PHASE2_PROOF_SCRIPT" "$WORKFLOW_FILE" || grep -Fq "$OBS_INFRA_GATE_SCRIPT" "$WORKFLOW_FILE"; then
  :
else
  echo "ERROR: Expected '$PHASE2_PROOF_SCRIPT' or '$OBS_INFRA_GATE_SCRIPT' in $WORKFLOW_FILE" >&2
  exit 1
fi

assert_contains "$WORKFLOW_FILE" "$PHASE14_ARTIFACT"
assert_contains "$WORKFLOW_FILE" "$PHASE2_ARTIFACT"
assert_contains "$WORKFLOW_FILE" "$ALL_PROJECTS_GATE_SCRIPT"
assert_contains "$WORKFLOW_FILE" "$UI_TRACE_ARTIFACT"

# CI/CD docs must mention the active E2E proof command and workflow gate.
assert_contains "$CI_DOC_FILE" "e2e-complete-suite"
assert_contains "$CI_DOC_FILE" "$PROOF_SCRIPT"
assert_contains "$CI_DOC_FILE" "$PHASE2_PROOF_SCRIPT"
assert_contains "$CI_DOC_FILE" "$ALL_PROJECTS_GATE_SCRIPT"

# E2E quickstart must include direct usage of the roadmap proof command.
assert_contains "$E2E_DOC_FILE" "$PROOF_SCRIPT"
assert_contains "$E2E_DOC_FILE" "$UI_TRACE_ARTIFACT"
assert_contains "$E2E_DOC_FILE" "$UI_BASELINE_TEST"

# E2E observability guide and README must reference the Phase 2 proof gate.
assert_contains "$E2E_OBS_DOC_FILE" "$PHASE2_PROOF_SCRIPT"
assert_contains "$README_FILE" "E2E-QUICKSTART.md"
assert_contains "$README_FILE" "E2E-OBSERVABILITY-GUIDE.md"

echo "[e2e-sync] workflow + docs are in sync with active Phase 1-4 and Phase 2 E2E proof gates"
