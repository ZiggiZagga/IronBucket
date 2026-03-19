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
  echo "[adapter-upgrade-safety-gate] Primary test-results directory not writable: $REQUESTED_TEST_RESULTS_DIR" >&2
  echo "[adapter-upgrade-safety-gate] Using fallback test-results directory: $TEST_RESULTS_DIR" >&2
fi

OUT_DIR="${TEST_RESULTS_DIR}/resilience-gates"

mkdir -p "${OUT_DIR}"

bash "${ROOT_DIR}/scripts/ci/run-maven-in-container.sh" services/Brazz-Nossel -B -V \
  -Dtest=AdapterSchemaUpgradeTest test

JSON_PATH="${OUT_DIR}/adapter-upgrade-safety-summary-${TIMESTAMP}.json"
MD_PATH="${OUT_DIR}/adapter-upgrade-safety-summary-${TIMESTAMP}.md"
LATEST_JSON="${OUT_DIR}/LATEST-adapter-upgrade-safety-summary.json"
LATEST_MD="${OUT_DIR}/LATEST-adapter-upgrade-safety-summary.md"

python3 - <<PY > "${JSON_PATH}"
import json
from datetime import datetime, timezone
payload = {
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "suite": "AdapterSchemaUpgradeTest",
    "module": "services/Brazz-Nossel",
    "status": "passed"
}
print(json.dumps(payload, indent=2))
PY

{
  echo "# Adapter Upgrade Safety Gate Summary"
  echo
  echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| AdapterSchemaUpgradeTest | passed |"
} > "${MD_PATH}"

cp "${JSON_PATH}" "${LATEST_JSON}"
cp "${MD_PATH}" "${LATEST_MD}"

echo "[adapter-upgrade-safety-gate] passed"
echo "[adapter-upgrade-safety-gate] summary: ${JSON_PATH}"
echo "[adapter-upgrade-safety-gate] summary: ${MD_PATH}"
