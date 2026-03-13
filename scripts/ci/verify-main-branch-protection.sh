#!/usr/bin/env bash
set -euo pipefail

# Verifies GitHub branch protection required checks on a target branch.
# BRANCH_PROTECTION_STRICT controls behavior when branch protection cannot be queried.

BRANCH_PROTECTION_STRICT="${BRANCH_PROTECTION_STRICT:-false}"
TARGET_BRANCH="${TARGET_BRANCH:-main}"
DEFAULT_REQUIRED_CHECKS="Build and Test All Modules,Sentinel Roadmap Gate,Sentinel Behavioral Gate,jclouds MinIO CRUD Gate,e2e-complete-suite"
REQUIRED_CHECK_RUNS="${REQUIRED_CHECK_RUNS:-}"
REQUIRED_STATUS_CHECKS="${REQUIRED_STATUS_CHECKS:-}"
GITHUB_API_URL="${GITHUB_API_URL:-https://api.github.com}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-}"
TOKEN="${BRANCH_PROTECTION_TOKEN:-${GITHUB_TOKEN:-}}"

if [[ -n "$REQUIRED_STATUS_CHECKS" ]]; then
  REQUIRED_CHECKS="$REQUIRED_STATUS_CHECKS"
elif [[ -n "$REQUIRED_CHECK_RUNS" ]]; then
  REQUIRED_CHECKS="$REQUIRED_CHECK_RUNS"
else
  REQUIRED_CHECKS="$DEFAULT_REQUIRED_CHECKS"
fi

fail_or_warn() {
  local message="$1"
  if [[ "$BRANCH_PROTECTION_STRICT" == "true" ]]; then
    echo "ERROR: $message" >&2
    exit 1
  fi
  echo "WARN: $message"
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

if [[ -z "$TOKEN" ]]; then
  fail_or_warn "BRANCH_PROTECTION_TOKEN or GITHUB_TOKEN must be set to query branch protection for ${GITHUB_REPOSITORY}/${TARGET_BRANCH}."
  exit 0
fi

echo "[branch-protection] repository=${GITHUB_REPOSITORY} branch=${TARGET_BRANCH} strict=${BRANCH_PROTECTION_STRICT}"

api_endpoint="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/branches/${TARGET_BRANCH}/protection"
response_file="$(mktemp)"
stderr_file="$(mktemp)"
trap 'rm -f "$response_file" "$stderr_file"' EXIT

set +e
http_code="$({
  curl -sS \
    -o "$response_file" \
    -w "%{http_code}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Accept: application/vnd.github+json' \
    "$api_endpoint"
} 2>"$stderr_file")"
curl_exit=$?
set -e

if [[ $curl_exit -ne 0 ]]; then
  if [[ -s "$stderr_file" ]]; then
    cat "$stderr_file" >&2
  fi
  fail_or_warn "Failed to call GitHub API for branch protection."
  exit 0
fi

if [[ "$http_code" != "200" ]]; then
  if [[ "$BRANCH_PROTECTION_STRICT" == "true" ]]; then
    echo "ERROR: Failed to read branch protection for ${GITHUB_REPOSITORY}:${TARGET_BRANCH} (HTTP ${http_code})" >&2
    if [[ -s "$stderr_file" ]]; then
      cat "$stderr_file" >&2
    fi
    if [[ -s "$response_file" ]]; then
      cat "$response_file" >&2
    fi
    echo "Hint: Provide BRANCH_PROTECTION_TOKEN with repository administration read permission." >&2
    exit 1
  fi

  warn_message="Failed to read branch protection for ${GITHUB_REPOSITORY}:${TARGET_BRANCH} (HTTP ${http_code})"
  if [[ -s "$response_file" ]]; then
    response_text="$(cat "$response_file")"
    warn_message+=". Response: ${response_text}"
  fi
  fail_or_warn "$warn_message"
  exit 0
fi

export BRANCH_PROTECTION_JSON="$(cat "$response_file")"
export REQUIRED_CHECKS TARGET_BRANCH
python3 - <<'PY'
import json
import os
import sys

raw = os.environ.get("BRANCH_PROTECTION_JSON", "")
required = [item.strip() for item in os.environ.get("REQUIRED_CHECKS", "").split(",") if item.strip()]
target_branch = os.environ.get("TARGET_BRANCH", "main")

try:
    payload = json.loads(raw)
except json.JSONDecodeError as exc:
    print(f"ERROR: Failed to parse branch-protection payload: {exc}", file=sys.stderr)
    sys.exit(1)

required_status_checks = payload.get("required_status_checks")
if not isinstance(required_status_checks, dict):
    print(f"ERROR: required_status_checks is not configured for branch '{target_branch}'.", file=sys.stderr)
    sys.exit(1)

configured = set()
for context in required_status_checks.get("contexts") or []:
    if isinstance(context, str) and context.strip():
        configured.add(context.strip())

for check in required_status_checks.get("checks") or []:
    if not isinstance(check, dict):
        continue
    context = check.get("context")
    if isinstance(context, str) and context.strip():
        configured.add(context.strip())

missing = [name for name in required if name not in configured]
strict_enabled = bool(required_status_checks.get("strict", False))

print(f"Branch protection verification for branch '{target_branch}':")
print(f"- Expected required checks: {len(required)}")
print(f"- Configured required checks: {len(configured)}")
print(f"- Missing required checks: {len(missing)}")
print(f"- Require up to date before merge (strict): {strict_enabled}")

if configured:
    print("Configured checks:")
    for item in sorted(configured):
        print(f"- {item}")

step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
if step_summary:
    with open(step_summary, "a", encoding="utf-8") as handle:
        handle.write(f"## Branch Protection Verification ({target_branch})\n\n")
        handle.write("| Check | Result |\n")
        handle.write("|---|---|\n")
        for name in required:
            result = "ok" if name in configured else "missing"
            handle.write(f"| {name} | {result} |\n")
        handle.write(f"\nStrict mode enabled on branch: {'yes' if strict_enabled else 'no'}\n")

errors = []
if not strict_enabled:
    errors.append("required_status_checks.strict is false (must be true)")

if missing:
    errors.append("Missing required branch-protection checks:")
    errors.extend([f"- {item}" for item in missing])

if errors:
    for line in errors:
        print(f"ERROR: {line}", file=sys.stderr)
    sys.exit(1)
PY

echo "[branch-protection] verification succeeded"
