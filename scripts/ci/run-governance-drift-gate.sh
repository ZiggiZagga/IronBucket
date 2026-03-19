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
  echo "[governance-drift-gate] Primary test-results directory not writable: $REQUESTED_TEST_RESULTS_DIR" >&2
  echo "[governance-drift-gate] Using fallback test-results directory: $TEST_RESULTS_DIR" >&2
fi

OUT_DIR="${TEST_RESULTS_DIR}/governance-gates"

mkdir -p "${OUT_DIR}"

DRIFT_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-drift-monitoring.sh")"
METADATA_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-metadata-drift.sh")"
RECONCILE_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-reconciliation-after-partition.sh")"

if ! grep -q "collector=inventory" <<<"${DRIFT_OUT}"; then
  echo "ERROR: drift monitoring output missing collector marker" >&2
  exit 1
fi
if ! grep -q "operation=diff" <<<"${DRIFT_OUT}"; then
  echo "ERROR: drift monitoring output missing diff operation" >&2
  exit 1
fi

if ! grep -q "metadata-index" <<<"${METADATA_OUT}"; then
  echo "ERROR: metadata drift output missing source inventory marker" >&2
  exit 1
fi
if ! grep -q "backend-metadata" <<<"${METADATA_OUT}"; then
  echo "ERROR: metadata drift output missing target inventory marker" >&2
  exit 1
fi
if ! grep -q "checksum" <<<"${METADATA_OUT}"; then
  echo "ERROR: metadata drift output missing checksum field" >&2
  exit 1
fi
if ! grep -q "acl" <<<"${METADATA_OUT}"; then
  echo "ERROR: metadata drift output missing ACL field" >&2
  exit 1
fi
if ! grep -q "versions" <<<"${METADATA_OUT}"; then
  echo "ERROR: metadata drift output missing versions field" >&2
  exit 1
fi
if ! grep -q "tags" <<<"${METADATA_OUT}"; then
  echo "ERROR: metadata drift output missing tags field" >&2
  exit 1
fi

if ! grep -q "operation=reconcile" <<<"${RECONCILE_OUT}"; then
  echo "ERROR: reconciliation output missing reconcile operation" >&2
  exit 1
fi
if ! grep -q "converges to zero" <<<"${RECONCILE_OUT}"; then
  echo "ERROR: reconciliation output missing convergence expectation" >&2
  exit 1
fi

JSON_PATH="${OUT_DIR}/governance-drift-gate-summary-${TIMESTAMP}.json"
MD_PATH="${OUT_DIR}/governance-drift-gate-summary-${TIMESTAMP}.md"
LATEST_JSON="${OUT_DIR}/LATEST-governance-drift-gate-summary.json"
LATEST_MD="${OUT_DIR}/LATEST-governance-drift-gate-summary.md"

python3 - <<PY > "${JSON_PATH}"
import json
from datetime import datetime, timezone
payload = {
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "driftMonitoringOutput": """${DRIFT_OUT}""".strip(),
    "metadataDriftOutput": """${METADATA_OUT}""".strip(),
    "reconciliationOutput": """${RECONCILE_OUT}""".strip(),
    "status": "passed"
}
print(json.dumps(payload, indent=2))
PY

{
  echo "# Governance Drift Gate Summary"
  echo
  echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| Drift monitoring fixture | passed |"
  echo "| Metadata drift fixture | passed |"
  echo "| Reconciliation fixture | passed |"
  echo
  echo "## Outputs"
  echo
  echo "- drift: ${DRIFT_OUT}"
  echo "- metadata: ${METADATA_OUT}"
  echo "- reconciliation: ${RECONCILE_OUT}"
} > "${MD_PATH}"

cp "${JSON_PATH}" "${LATEST_JSON}"
cp "${MD_PATH}" "${LATEST_MD}"

echo "[governance-drift-gate] passed"
echo "[governance-drift-gate] summary: ${JSON_PATH}"
echo "[governance-drift-gate] summary: ${MD_PATH}"
