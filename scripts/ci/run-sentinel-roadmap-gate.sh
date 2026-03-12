#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ROADMAP_MIN_TESTS="${ROADMAP_MIN_TESTS:-50}"

cd "${REPO_ROOT}/services/Sentinel-Gear"

echo "Running Sentinel-Gear roadmap profile tests..."
mvn -B -V test -Proadmap

REPORT_DIR="target/surefire-reports"
PATTERN="TEST-com.ironbucket.roadmap*.xml"

if ! compgen -G "${REPORT_DIR}/${PATTERN}" > /dev/null; then
  echo "ERROR: No roadmap surefire reports found in ${REPORT_DIR}/${PATTERN}" >&2
  exit 1
fi

python3 - <<'PY'
import glob
import os
import sys
import xml.etree.ElementTree as ET

report_dir = "target/surefire-reports"
pattern = os.path.join(report_dir, "TEST-com.ironbucket.roadmap*.xml")
min_tests = int(os.environ.get("ROADMAP_MIN_TESTS", "50"))

files = sorted(glob.glob(pattern))
if not files:
    print(f"ERROR: No roadmap report files matched {pattern}", file=sys.stderr)
    sys.exit(1)

total_tests = 0
total_failures = 0
total_errors = 0
total_skipped = 0

for file_path in files:
    root = ET.parse(file_path).getroot()
    testcases = root.findall(".//testcase")
    total_tests += len(testcases)
    for testcase in testcases:
        if testcase.find("failure") is not None:
            total_failures += 1
        if testcase.find("error") is not None:
            total_errors += 1
        if testcase.find("skipped") is not None:
            total_skipped += 1

executed = total_tests - total_skipped
summary = (
    f"Sentinel roadmap gate summary: files={len(files)}, tests={total_tests}, "
    f"executed={executed}, skipped={total_skipped}, failures={total_failures}, errors={total_errors}"
)
print(summary)

if total_failures > 0 or total_errors > 0:
    print("ERROR: Roadmap gate failed due to failing tests", file=sys.stderr)
    sys.exit(1)

if executed < min_tests:
    print(
        f"ERROR: Roadmap gate requires at least {min_tests} executed tests, got {executed}",
        file=sys.stderr,
    )
    sys.exit(1)

step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
if step_summary:
    with open(step_summary, "a", encoding="utf-8") as f:
        f.write("## Sentinel Roadmap Gate\n\n")
        f.write("| Metric | Value |\n")
        f.write("|---|---|\n")
        f.write(f"| Report Files | {len(files)} |\n")
        f.write(f"| Tests | {total_tests} |\n")
        f.write(f"| Executed (non-skipped) | {executed} |\n")
        f.write(f"| Skipped | {total_skipped} |\n")
        f.write(f"| Failures | {total_failures} |\n")
        f.write(f"| Errors | {total_errors} |\n")
        f.write(f"| Minimum Required Executed | {min_tests} |\n")
PY
