#!/usr/bin/env bash
set -euo pipefail

# Verifies GitHub branch protection on a target branch, focusing on required status checks.
# This is intended to operationalize Phase E gate-hardening policy.

BRANCH_PROTECTION_STRICT="${BRANCH_PROTECTION_STRICT:-false}"
TARGET_BRANCH="${TARGET_BRANCH:-main}"
REQUIRED_CHECK_RUNS="${REQUIRED_CHECK_RUNS:-Build and Test All Modules,Sentinel Roadmap Gate,Sentinel Behavioral Gate,jclouds MinIO CRUD Gate,e2e-complete-suite}"
GITHUB_API_URL="${GITHUB_API_URL:-https://api.github.com}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

fail_or_warn() {
  local message="$1"
  if [[ "$BRANCH_PROTECTION_STRICT" == "true" ]]; then
    echo "ERROR: $message" >&2
    exit 1
  fi
  echo "WARN: $message"
  return 0
}

if [[ -z "$GITHUB_REPOSITORY" ]]; then
  remote_url="$(git config --get remote.origin.url 2>/dev/null || true)"
  if [[ "$remote_url" =~ github.com[:/]([^/]+/[^/.]+)(\.git)?$ ]]; then
    GITHUB_REPOSITORY="${BASH_REMATCH[1]}"
  fi
fi

if [[ -z "$GITHUB_REPOSITORY" ]]; then
  fail_or_warn "GITHUB_REPOSITORY is not set and could not be inferred from git remote."
  exit 0
fi

if [[ -z "$GITHUB_TOKEN" ]]; then
  fail_or_warn "GITHUB_TOKEN is not set; cannot query branch protection API for ${GITHUB_REPOSITORY}/${TARGET_BRANCH}."
  exit 0
fi

echo "[branch-protection] repository=${GITHUB_REPOSITORY} branch=${TARGET_BRANCH} strict=${BRANCH_PROTECTION_STRICT}"

set +e
response_with_code="$({
  curl -sS \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H 'Accept: application/vnd.github+json' \
    -w "\n%{http_code}" \
    "${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/branches/${TARGET_BRANCH}/protection"
})"
curl_exit=$?
set -e

if [[ $curl_exit -ne 0 ]]; then
  fail_or_warn "Failed to call GitHub API for branch protection."
  exit 0
fi

http_code="$(printf '%s' "$response_with_code" | tail -n1)"
response_body="$(printf '%s' "$response_with_code" | sed '$d')"

case "$http_code" in
  200) ;;
  401|403)
    fail_or_warn "Token does not have permission to read branch protection (${http_code})."
    exit 0
    ;;
  404)
    fail_or_warn "Branch protection not found on ${TARGET_BRANCH} (${http_code})."
    exit 0
    ;;
  *)
    fail_or_warn "Unexpected HTTP status ${http_code} from branch protection API."
    exit 0
    ;;
esac

export BP_JSON="$response_body"
export REQUIRED_CHECK_RUNS
export TARGET_BRANCH
python3 - <<'PY'
import json
import os
import sys

required = [s.strip() for s in os.environ.get("REQUIRED_CHECK_RUNS", "").split(",") if s.strip()]
branch = os.environ.get("TARGET_BRANCH", "main")
raw = os.environ.get("BP_JSON", "")

try:
    payload = json.loads(raw)
except json.JSONDecodeError as exc:
    print(f"ERROR: Failed to parse branch protection payload: {exc}", file=sys.stderr)
    sys.exit(1)

required_status_checks = payload.get("required_status_checks")
if not required_status_checks:
    print(f"ERROR: required_status_checks is not configured for branch '{branch}'.", file=sys.stderr)
    sys.exit(1)

contexts = required_status_checks.get("contexts") or []
checks = required_status_checks.get("checks") or []
check_names = []
for item in checks:
    if isinstance(item, dict):
        name = (item.get("context") or "").strip()
        if name:
            check_names.append(name)

configured = {c.strip() for c in contexts if isinstance(c, str) and c.strip()}
configured.update(check_names)
missing = [name for name in required if name not in configured]

strict_mode = bool(required_status_checks.get("strict", False))
if not strict_mode:
    print("ERROR: required_status_checks.strict is false (must be true).", file=sys.stderr)
    sys.exit(1)

if missing:
    print("ERROR: Missing required status checks on protected branch:", file=sys.stderr)
    for name in missing:
        print(f"- {name}", file=sys.stderr)
    print("Configured checks:", file=sys.stderr)
    for name in sorted(configured):
        print(f"- {name}", file=sys.stderr)
    sys.exit(1)

print(f"Branch protection verification passed for '{branch}'.")
print(f"Required checks verified: {len(required)}")
print(f"Configured checks discovered: {len(configured)}")
PY

echo "[branch-protection] verification succeeded"
