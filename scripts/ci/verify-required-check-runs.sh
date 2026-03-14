#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_TOKEN:?GITHUB_TOKEN must be set}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY must be set (owner/repo)}"

TARGET_SHA="${TARGET_SHA:-${GITHUB_SHA:-}}"
if [[ -z "$TARGET_SHA" ]]; then
  echo "ERROR: TARGET_SHA or GITHUB_SHA must be set" >&2
  exit 1
fi

REQUIRED_CHECK_RUNS="${REQUIRED_CHECK_RUNS:-Build and Test All Modules,jclouds MinIO CRUD Gate,Sentinel Roadmap Gate,Sentinel Behavioral Gate,e2e-complete-suite}"
GITHUB_API_URL="${GITHUB_API_URL:-https://api.github.com}"

CHECK_RUNS_JSON="$(curl -fsSL \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H 'Accept: application/vnd.github+json' \
  "${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/commits/${TARGET_SHA}/check-runs?per_page=100")"

CHECK_RUNS_JSON_FILE="$(mktemp)"
trap 'rm -f "$CHECK_RUNS_JSON_FILE"' EXIT
printf '%s' "$CHECK_RUNS_JSON" > "$CHECK_RUNS_JSON_FILE"

export CHECK_RUNS_JSON_FILE REQUIRED_CHECK_RUNS TARGET_SHA
python3 - <<'PY'
import json
import os
import sys

json_path = os.environ.get("CHECK_RUNS_JSON_FILE", "")
required = [item.strip() for item in os.environ.get("REQUIRED_CHECK_RUNS", "").split(",") if item.strip()]
target_sha = os.environ.get("TARGET_SHA", "")

if not json_path:
    print("ERROR: CHECK_RUNS_JSON_FILE is not set", file=sys.stderr)
    sys.exit(1)

try:
    with open(json_path, "r", encoding="utf-8") as handle:
        raw = handle.read()
except OSError as exc:
    print(f"ERROR: Failed to read check-runs payload file: {exc}", file=sys.stderr)
    sys.exit(1)

try:
    payload = json.loads(raw)
except json.JSONDecodeError as exc:
    print(f"ERROR: Failed to parse check-runs payload: {exc}", file=sys.stderr)
    sys.exit(1)

check_runs = payload.get("check_runs", [])
latest_by_name = {}
for run in check_runs:
    name = (run.get("name") or "").strip()
    if not name:
        continue
    completed_at = run.get("completed_at") or ""
    current = latest_by_name.get(name)
    if current is None or completed_at > (current.get("completed_at") or ""):
        latest_by_name[name] = run

missing = []
non_green = []
for name in required:
    run = latest_by_name.get(name)
    if run is None:
        missing.append(name)
        continue

    status = (run.get("status") or "").lower()
    conclusion = (run.get("conclusion") or "").lower()
    if status != "completed" or conclusion != "success":
        non_green.append(
            {
                "name": name,
                "status": status or "unknown",
                "conclusion": conclusion or "unknown",
                "url": run.get("html_url") or "",
            }
        )

print(f"Required check-run verification for commit {target_sha}:")
print(f"- Required checks: {len(required)}")
print(f"- Available check runs: {len(check_runs)}")
print(f"- Missing required checks: {len(missing)}")
print(f"- Non-green required checks: {len(non_green)}")

if missing:
    print("Missing required checks:", file=sys.stderr)
    for name in missing:
        print(f"- {name}", file=sys.stderr)

if non_green:
    print("Required checks not green:", file=sys.stderr)
    for item in non_green:
        suffix = f" ({item['url']})" if item["url"] else ""
        print(
            f"- {item['name']}: status={item['status']}, conclusion={item['conclusion']}{suffix}",
            file=sys.stderr,
        )

step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
if step_summary:
    with open(step_summary, "a", encoding="utf-8") as handle:
        handle.write("## Required Check-Run Verification\n\n")
        handle.write(f"Commit: `{target_sha}`\n\n")
        handle.write("| Check | Result | Status | Conclusion |\n")
        handle.write("|---|---|---|---|\n")
        for name in required:
            run = latest_by_name.get(name)
            if run is None:
                handle.write(f"| {name} | missing | - | - |\n")
            else:
                status = run.get("status") or "unknown"
                conclusion = run.get("conclusion") or "unknown"
                result = "ok" if status == "completed" and conclusion == "success" else "not green"
                handle.write(f"| {name} | {result} | {status} | {conclusion} |\n")

if missing or non_green:
    sys.exit(1)
PY
