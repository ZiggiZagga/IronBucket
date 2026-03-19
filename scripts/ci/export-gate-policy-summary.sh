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
  echo "[export-gate-policy-summary] Primary test-results directory not writable: $REQUESTED_TEST_RESULTS_DIR" >&2
  echo "[export-gate-policy-summary] Using fallback test-results directory: $TEST_RESULTS_DIR" >&2
fi

OUT_DIR="${TEST_RESULTS_DIR}/ci-gates"

REQUIRED_CHECKS="${REQUIRED_CHECKS:-Build and Test All Modules,jclouds MinIO CRUD Gate,Sentinel Roadmap Gate,Sentinel Behavioral Gate,e2e-complete-suite}"
IFS=',' read -r -a CHECK_ARRAY <<< "${REQUIRED_CHECKS}"

mkdir -p "${OUT_DIR}"
JSON_PATH="${OUT_DIR}/gate-policy-summary-${TIMESTAMP}.json"
MD_PATH="${OUT_DIR}/gate-policy-summary-${TIMESTAMP}.md"
LATEST_JSON="${OUT_DIR}/LATEST-gate-policy-summary.json"
LATEST_MD="${OUT_DIR}/LATEST-gate-policy-summary.md"

python3 - <<'PY' > "${JSON_PATH}"
import json
import os
from datetime import datetime, timezone

checks = [item.strip() for item in os.environ.get("REQUIRED_CHECKS", "").split(",") if item.strip()]
payload = {
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "requiredChecks": checks,
    "requiredCheckCount": len(checks),
    "sourceWorkflows": [
        ".github/workflows/build-and-test.yml",
        ".github/workflows/release.yml",
    ],
    "sourceScripts": [
        "scripts/ci/verify-main-branch-protection.sh",
        "scripts/ci/verify-required-check-runs.sh",
    ],
    "strictBranchProtectionInRelease": True,
}
print(json.dumps(payload, indent=2))
PY

{
  echo "# CI Gate Policy Summary"
  echo
  echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "## Required Checks"
  for item in "${CHECK_ARRAY[@]}"; do
    trimmed="$(echo "$item" | xargs)"
    [[ -z "${trimmed}" ]] && continue
    echo "- ${trimmed}"
  done
  echo
  echo "## Source of Truth"
  echo "- .github/workflows/build-and-test.yml"
  echo "- .github/workflows/release.yml"
  echo "- scripts/ci/verify-main-branch-protection.sh"
  echo "- scripts/ci/verify-required-check-runs.sh"
  echo
  echo "## Release Guard"
  echo "- strict branch protection verification in release workflow: enabled"
} > "${MD_PATH}"

cp "${JSON_PATH}" "${LATEST_JSON}"
cp "${MD_PATH}" "${LATEST_MD}"

echo "Gate policy summary generated: ${JSON_PATH}"
echo "Gate policy summary generated: ${MD_PATH}"
