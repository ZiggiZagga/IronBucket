#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BEHAVIORAL_MIN_TESTS="${BEHAVIORAL_MIN_TESTS:-20}"
BEHAVIORAL_GATE_STRICT="${BEHAVIORAL_GATE_STRICT:-false}"
MODULE_PATH="services/Sentinel-Gear"

export MAVEN_DOCKER_ENV_VARS="${MAVEN_DOCKER_ENV_VARS:-BEHAVIORAL_MIN_TESTS,BEHAVIORAL_GATE_STRICT}"
export REPO_ROOT MODULE_PATH

echo "Running Sentinel-Gear integration profile tests in container..."
set +e
bash "${SCRIPT_DIR}/run-maven-in-container.sh" "${MODULE_PATH}" -B -V test -Pintegration
MVN_EXIT=$?
set -e

REPORT_DIR="${REPO_ROOT}/${MODULE_PATH}/target/surefire-reports"
PATTERN="TEST-com.ironbucket.sentinelgear.integration*.xml"

if ! compgen -G "${REPORT_DIR}/${PATTERN}" > /dev/null; then
  echo "ERROR: No integration surefire reports found in ${REPORT_DIR}/${PATTERN}" >&2
  exit 1
fi

export MVN_EXIT
python3 - <<'PY'
import glob
import os
import sys
import xml.etree.ElementTree as ET

report_dir = os.path.join(os.environ["REPO_ROOT"], os.environ["MODULE_PATH"], "target", "surefire-reports")
pattern = os.path.join(report_dir, "TEST-com.ironbucket.sentinelgear.integration*.xml")
min_tests = int(os.environ.get("BEHAVIORAL_MIN_TESTS", "20"))
strict = os.environ.get("BEHAVIORAL_GATE_STRICT", "false").lower() == "true"
mvn_exit = int(os.environ.get("MVN_EXIT", "1"))

files = sorted(glob.glob(pattern))
if not files:
    print(f"ERROR: No integration report files matched {pattern}", file=sys.stderr)
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
has_failures = mvn_exit != 0 or total_failures > 0 or total_errors > 0
insufficient_execution = executed < min_tests

summary = (
    f"Sentinel behavioral gate summary: strict={strict}, files={len(files)}, tests={total_tests}, "
    f"executed={executed}, skipped={total_skipped}, failures={total_failures}, errors={total_errors}, mvn_exit={mvn_exit}"
)
print(summary)

step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
if step_summary:
    with open(step_summary, "a", encoding="utf-8") as f:
        f.write("## Sentinel Behavioral Gate\n\n")
        f.write("| Metric | Value |\n")
        f.write("|---|---|\n")
        f.write(f"| Strict Mode | {strict} |\n")
        f.write(f"| Report Files | {len(files)} |\n")
        f.write(f"| Tests | {total_tests} |\n")
        f.write(f"| Executed (non-skipped) | {executed} |\n")
        f.write(f"| Skipped | {total_skipped} |\n")
        f.write(f"| Failures | {total_failures} |\n")
        f.write(f"| Errors | {total_errors} |\n")
        f.write(f"| Maven Exit Code | {mvn_exit} |\n")
        f.write(f"| Minimum Required Executed | {min_tests} |\n")

if strict:
    if has_failures:
        print("ERROR: Behavioral gate failed due to failing integration tests", file=sys.stderr)
        sys.exit(1)
    if insufficient_execution:
        print(
            f"ERROR: Behavioral gate requires at least {min_tests} executed integration tests, got {executed}",
            file=sys.stderr,
        )
        sys.exit(1)
else:
    if has_failures:
        print("WARNING: Behavioral gate detected failing integration tests (report-only mode)", file=sys.stderr)
    if insufficient_execution:
        print(
            f"WARNING: Behavioral gate executed tests below threshold ({executed} < {min_tests}) in report-only mode",
            file=sys.stderr,
        )
PY
