#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

WORKFLOW_FILE="$ROOT_DIR/.github/workflows/e2e-complete-suite.yml"
CI_DOC_FILE="$ROOT_DIR/docs/CI-CD-PIPELINE.md"
E2E_DOC_FILE="$ROOT_DIR/docs/E2E-QUICKSTART.md"
PROOF_SCRIPT="scripts/e2e/prove-phase1-4-complete.sh"
PHASE14_ARTIFACT="test-results/phase1-4-proof/"

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
assert_file_exists "$ROOT_DIR/$PROOF_SCRIPT"

# Workflow must execute the current roadmap proof gate and upload phase 1-4 artifacts.
assert_contains "$WORKFLOW_FILE" "$PROOF_SCRIPT"
assert_contains "$WORKFLOW_FILE" "$PHASE14_ARTIFACT"

# CI/CD docs must mention the active E2E proof command and workflow gate.
assert_contains "$CI_DOC_FILE" "e2e-complete-suite"
assert_contains "$CI_DOC_FILE" "$PROOF_SCRIPT"

# E2E quickstart must include direct usage of the roadmap proof command.
assert_contains "$E2E_DOC_FILE" "$PROOF_SCRIPT"

echo "[e2e-sync] workflow + docs are in sync with the active Phase 1-4 E2E proof gate"
