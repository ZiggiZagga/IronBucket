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
  echo "[phase4-versioning-multipart-gate] Primary test-results directory not writable: $REQUESTED_TEST_RESULTS_DIR" >&2
  echo "[phase4-versioning-multipart-gate] Using fallback test-results directory: $TEST_RESULTS_DIR" >&2
fi

OUT_DIR="${TEST_RESULTS_DIR}/phase4-gates"

mkdir -p "${OUT_DIR}"

VERSIONING_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-versioning-delete-markers.sh")"

if ! grep -q "migration=versioning-enabled" <<<"${VERSIONING_OUT}"; then
  echo "ERROR: versioning fixture missing migration marker" >&2
  exit 1
fi
if ! grep -q "delete-marker" <<<"${VERSIONING_OUT}"; then
  echo "ERROR: versioning fixture missing delete-marker marker" >&2
  exit 1
fi

bash "${ROOT_DIR}/scripts/ci/run-maven-in-container.sh" services/jclouds-adapter-core -B -V \
  -Dtest=ProviderNeutralParityContractTest,ProviderCapabilityRegistryTest test

JSON_PATH="${OUT_DIR}/phase4-versioning-multipart-gate-summary-${TIMESTAMP}.json"
MD_PATH="${OUT_DIR}/phase4-versioning-multipart-gate-summary-${TIMESTAMP}.md"
LATEST_JSON="${OUT_DIR}/LATEST-phase4-versioning-multipart-gate-summary.json"
LATEST_MD="${OUT_DIR}/LATEST-phase4-versioning-multipart-gate-summary.md"

python3 - <<PY > "${JSON_PATH}"
import json
from datetime import datetime, timezone
payload = {
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "versioningDeleteMarkerFixture": """${VERSIONING_OUT}""".strip(),
    "jcloudsParityContracts": [
        "ProviderNeutralParityContractTest",
        "ProviderCapabilityRegistryTest"
    ],
    "status": "passed"
}
print(json.dumps(payload, indent=2))
PY

{
  echo "# Phase 4 Versioning/Multipart Gate Summary"
  echo
  echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| Versioning+delete-marker fixture | passed |"
  echo "| ProviderNeutralParityContractTest | passed |"
  echo "| ProviderCapabilityRegistryTest | passed |"
} > "${MD_PATH}"

cp "${JSON_PATH}" "${LATEST_JSON}"
cp "${MD_PATH}" "${LATEST_MD}"

echo "[phase4-versioning-multipart-gate] passed"
echo "[phase4-versioning-multipart-gate] summary: ${JSON_PATH}"
echo "[phase4-versioning-multipart-gate] summary: ${MD_PATH}"
