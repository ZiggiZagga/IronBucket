#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_RESULTS_DIR="${TEST_RESULTS_DIR:-$ROOT_DIR/test-results}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

resolve_test_results_dir() {
  local requested_dir="$1"
  local fallback_dir="$ROOT_DIR/temp/test-results"

  if mkdir -p "$requested_dir" >/dev/null 2>&1 && [[ -w "$requested_dir" ]]; then
    echo "$requested_dir"
    return
  fi

  mkdir -p "$fallback_dir"
  echo "$fallback_dir"
}

REQUESTED_TEST_RESULTS_DIR="$TEST_RESULTS_DIR"
TEST_RESULTS_DIR="$(resolve_test_results_dir "$TEST_RESULTS_DIR")"
if [[ "$TEST_RESULTS_DIR" != "$REQUESTED_TEST_RESULTS_DIR" ]]; then
  echo "[enterprise-admin-runbook-gate] Primary test-results directory not writable: $REQUESTED_TEST_RESULTS_DIR" >&2
  echo "[enterprise-admin-runbook-gate] Using fallback test-results directory: $TEST_RESULTS_DIR" >&2
fi

OUT_DIR="${TEST_RESULTS_DIR}/release-gates"
mkdir -p "${OUT_DIR}"

require_file() {
  local file="$1"
  if [[ ! -f "$ROOT_DIR/$file" ]]; then
    echo "[enterprise-admin-runbook-gate] Missing required file: $file" >&2
    exit 1
  fi
}

require_pattern() {
  local file="$1"
  local pattern="$2"
  local label="$3"

  if ! grep -q "$pattern" "$ROOT_DIR/$file"; then
    echo "[enterprise-admin-runbook-gate] Missing required content ($label) in $file" >&2
    exit 1
  fi
}

require_file "docs/security/PRESIGNED-REPLAY-TAMPER-RUNBOOK.md"
require_file "docs/CI-CD-PIPELINE.md"
require_file "docs/DEPLOYMENT.md"
require_file "scripts/ci/release-preflight.sh"

require_pattern "docs/CI-CD-PIPELINE.md" "Minimal runbook sequence" "minimal runbook sequence"
require_pattern "docs/CI-CD-PIPELINE.md" "Branch Protection Required Checks (main)" "main branch required checks"
require_pattern "docs/DEPLOYMENT.md" "PRESIGNED-REPLAY-TAMPER-RUNBOOK.md" "deployment runbook reference"
require_pattern "scripts/ci/release-preflight.sh" "verify-main-branch-protection.sh" "preflight branch-protection verification"
require_pattern "scripts/ci/release-preflight.sh" "run-presigned-security-smoke.sh" "preflight presigned security smoke"

JSON_PATH="${OUT_DIR}/enterprise-admin-runbook-gate-summary-${TIMESTAMP}.json"
MD_PATH="${OUT_DIR}/enterprise-admin-runbook-gate-summary-${TIMESTAMP}.md"
LATEST_JSON="${OUT_DIR}/LATEST-enterprise-admin-runbook-gate-summary.json"
LATEST_MD="${OUT_DIR}/LATEST-enterprise-admin-runbook-gate-summary.md"

python3 - <<PY > "${JSON_PATH}"
import json
from datetime import datetime, timezone
payload = {
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "checks": [
        "docs/security/PRESIGNED-REPLAY-TAMPER-RUNBOOK.md exists",
        "docs/CI-CD-PIPELINE.md contains minimal runbook sequence",
        "docs/CI-CD-PIPELINE.md contains main branch required checks",
        "docs/DEPLOYMENT.md references presigned replay/tamper runbook",
        "scripts/ci/release-preflight.sh verifies main branch protection",
        "scripts/ci/release-preflight.sh runs presigned security smoke"
    ],
    "status": "passed"
}
print(json.dumps(payload, indent=2))
PY

{
  echo "# Enterprise Admin Runbook Gate Summary"
  echo
  echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| Security runbook file exists | passed |"
  echo "| CI/CD runbook sequence documented | passed |"
  echo "| Main required checks documented | passed |"
  echo "| Deployment references security runbook | passed |"
  echo "| Release preflight branch-protection guard | passed |"
  echo "| Release preflight presigned smoke guard | passed |"
} > "${MD_PATH}"

cp "${JSON_PATH}" "${LATEST_JSON}"
cp "${MD_PATH}" "${LATEST_MD}"

echo "[enterprise-admin-runbook-gate] passed"
echo "[enterprise-admin-runbook-gate] summary: ${JSON_PATH}"
echo "[enterprise-admin-runbook-gate] summary: ${MD_PATH}"
