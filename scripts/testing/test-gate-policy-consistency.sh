#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

python3 - <<'PY'
import re
import sys
from pathlib import Path

root = Path("/workspaces/IronBucket")

files = {
    "release_workflow": root / ".github/workflows/release.yml",
    "build_workflow": root / ".github/workflows/build-and-test.yml",
    "verify_branch_script": root / "scripts/ci/verify-main-branch-protection.sh",
    "verify_checks_script": root / "scripts/ci/verify-required-check-runs.sh",
    "gate_summary_script": root / "scripts/ci/export-gate-policy-summary.sh",
}

patterns = {
    "release_workflow": r"REQUIRED_STATUS_CHECKS:\s*([^\n]+)",
    "build_workflow": r"REQUIRED_CHECKS:\s*([^\n]+)",
    "verify_branch_script": r"DEFAULT_REQUIRED_CHECKS=\"([^\"]+)\"",
    "verify_checks_script": r"REQUIRED_CHECK_RUNS=\"\$\{REQUIRED_CHECK_RUNS:-([^\"]+)\}\"",
    "gate_summary_script": r"REQUIRED_CHECKS=\"\$\{REQUIRED_CHECKS:-([^\"]+)\}\"",
}

values = {}
for key, path in files.items():
    content = path.read_text(encoding="utf-8")
    match = re.search(patterns[key], content)
    if not match:
        print(f"ERROR: could not extract checks from {path}", file=sys.stderr)
        sys.exit(1)
    values[key] = match.group(1).strip().strip('"')

normalized = {k: [item.strip() for item in v.split(',') if item.strip()] for k, v in values.items()}

baseline_key = "release_workflow"
baseline = normalized[baseline_key]

errors = []
for key, checks in normalized.items():
    if checks != baseline:
        errors.append((key, checks))

if errors:
    print("ERROR: gate policy checks are inconsistent across files", file=sys.stderr)
    print(f"Baseline ({baseline_key}): {baseline}", file=sys.stderr)
    for key, checks in errors:
        print(f"- {key}: {checks}", file=sys.stderr)
    sys.exit(1)

print("test-gate-policy-consistency: all checks passed")
print("Checks:")
for item in baseline:
    print(f"- {item}")
PY
