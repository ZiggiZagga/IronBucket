#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${ROOT_DIR}/test-results/ci-gates"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

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
