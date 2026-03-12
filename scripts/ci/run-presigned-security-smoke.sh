#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${REPO_ROOT}/services/Sentinel-Gear"

echo "Running presigned security smoke test..."
mvn -B -V -Dtest=com.ironbucket.sentinelgear.filter.PresignedRequestSecurityFilterTest test

REPORT_FILE="target/surefire-reports/TEST-com.ironbucket.sentinelgear.filter.PresignedRequestSecurityFilterTest.xml"
if [[ ! -f "${REPORT_FILE}" ]]; then
  echo "ERROR: Missing smoke report: ${REPORT_FILE}" >&2
  exit 1
fi

python3 - <<'PY'
import os
import sys
import xml.etree.ElementTree as ET

report_file = "target/surefire-reports/TEST-com.ironbucket.sentinelgear.filter.PresignedRequestSecurityFilterTest.xml"
root = ET.parse(report_file).getroot()

tests = int(root.attrib.get("tests", "0"))
failures = int(root.attrib.get("failures", "0"))
errors = int(root.attrib.get("errors", "0"))
skipped = int(root.attrib.get("skipped", "0"))
executed = tests - skipped

print(
    "Presigned smoke summary: "
    f"tests={tests}, executed={executed}, skipped={skipped}, failures={failures}, errors={errors}"
)

if failures > 0 or errors > 0:
    print("ERROR: Presigned smoke test failed", file=sys.stderr)
    sys.exit(1)

if executed < 1:
    print("ERROR: Presigned smoke test did not execute", file=sys.stderr)
    sys.exit(1)

step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
if step_summary:
    with open(step_summary, "a", encoding="utf-8") as f:
        f.write("## Presigned Security Smoke Gate\n\n")
        f.write("| Metric | Value |\n")
        f.write("|---|---|\n")
        f.write(f"| Tests | {tests} |\n")
        f.write(f"| Executed (non-skipped) | {executed} |\n")
        f.write(f"| Skipped | {skipped} |\n")
        f.write(f"| Failures | {failures} |\n")
        f.write(f"| Errors | {errors} |\n")
PY