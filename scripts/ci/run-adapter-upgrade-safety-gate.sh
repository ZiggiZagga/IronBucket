#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${ROOT_DIR}/test-results/resilience-gates"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

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
