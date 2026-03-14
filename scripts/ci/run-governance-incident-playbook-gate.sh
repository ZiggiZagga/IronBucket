#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${ROOT_DIR}/test-results/governance-gates"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "${OUT_DIR}"

POLICY_BYPASS_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-policy-bypass.sh")"
ADAPTER_CRASH_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-adapter-crash-during-write.sh")"
TENANT_ISOLATION_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-tenant-isolation.sh")"
ERROR_HANDLING_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-error-handling-e2e.sh")"

if ! grep -q "scenario=policy-bypass" <<<"${POLICY_BYPASS_OUT}"; then
  echo "ERROR: policy bypass fixture missing scenario marker" >&2
  exit 1
fi
if ! grep -q "result=denied" <<<"${POLICY_BYPASS_OUT}"; then
  echo "ERROR: policy bypass fixture missing denied result marker" >&2
  exit 1
fi
if ! grep -q "reconciliation=completed" <<<"${POLICY_BYPASS_OUT}"; then
  echo "ERROR: policy bypass fixture missing reconciliation marker" >&2
  exit 1
fi
if ! grep -q "alerting=triggered" <<<"${POLICY_BYPASS_OUT}"; then
  echo "ERROR: policy bypass fixture missing alerting marker" >&2
  exit 1
fi

if ! grep -q "mode=crash" <<<"${ADAPTER_CRASH_OUT}"; then
  echo "ERROR: adapter crash fixture missing crash mode marker" >&2
  exit 1
fi
if ! grep -q "recovery=rollback" <<<"${ADAPTER_CRASH_OUT}"; then
  echo "ERROR: adapter crash fixture missing rollback marker" >&2
  exit 1
fi
if ! grep -q "integrity=atomic" <<<"${ADAPTER_CRASH_OUT}"; then
  echo "ERROR: adapter crash fixture missing atomic integrity marker" >&2
  exit 1
fi

if ! grep -q "Alice cannot access Bob resources" <<<"${TENANT_ISOLATION_OUT}"; then
  echo "ERROR: tenant isolation fixture missing Alice/Bob denial marker" >&2
  exit 1
fi
if ! grep -q "Bob cannot access Alice resources" <<<"${TENANT_ISOLATION_OUT}"; then
  echo "ERROR: tenant isolation fixture missing Bob/Alice denial marker" >&2
  exit 1
fi

if ! grep -q "401, 403, 404, 500" <<<"${ERROR_HANDLING_OUT}"; then
  echo "ERROR: error handling fixture missing status matrix marker" >&2
  exit 1
fi

JSON_PATH="${OUT_DIR}/governance-incident-playbook-summary-${TIMESTAMP}.json"
MD_PATH="${OUT_DIR}/governance-incident-playbook-summary-${TIMESTAMP}.md"
LATEST_JSON="${OUT_DIR}/LATEST-governance-incident-playbook-summary.json"
LATEST_MD="${OUT_DIR}/LATEST-governance-incident-playbook-summary.md"

python3 - <<PY > "${JSON_PATH}"
import json
from datetime import datetime, timezone

payload = {
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "policyBypassFixture": """${POLICY_BYPASS_OUT}""".strip(),
    "adapterCrashFixture": """${ADAPTER_CRASH_OUT}""".strip(),
    "tenantIsolationFixture": """${TENANT_ISOLATION_OUT}""".strip(),
    "errorHandlingFixture": """${ERROR_HANDLING_OUT}""".strip(),
    "status": "passed"
}
print(json.dumps(payload, indent=2))
PY

{
  echo "# Governance Incident Playbook Gate Summary"
  echo
  echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| Policy bypass and reconciliation fixture | passed |"
  echo "| Adapter crash rollback fixture | passed |"
  echo "| Tenant isolation incident fixture | passed |"
  echo "| Error handling status matrix fixture | passed |"
} > "${MD_PATH}"

cp "${JSON_PATH}" "${LATEST_JSON}"
cp "${MD_PATH}" "${LATEST_MD}"

echo "[governance-incident-playbook-gate] passed"
echo "[governance-incident-playbook-gate] summary: ${JSON_PATH}"
echo "[governance-incident-playbook-gate] summary: ${MD_PATH}"
