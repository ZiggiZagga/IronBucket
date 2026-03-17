#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPORT_DIR="${REPORT_DIR:-${ROOT_DIR}/services/Sentinel-Gear/target/surefire-reports}"
PATTERN="${PATTERN:-TEST-com.ironbucket.roadmap.GovernanceIntegrityResilienceTest*.xml}"
OUT_DIR="${ROOT_DIR}/test-results/governance-gates"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "${OUT_DIR}"

JSON_PATH="${OUT_DIR}/governance-evidence-summary-${TIMESTAMP}.json"
MD_PATH="${OUT_DIR}/governance-evidence-summary-${TIMESTAMP}.md"
LATEST_JSON="${OUT_DIR}/LATEST-governance-evidence-summary.json"
LATEST_MD="${OUT_DIR}/LATEST-governance-evidence-summary.md"

export REPORT_DIR PATTERN JSON_PATH MD_PATH

python3 - <<'PY'
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

report_dir = os.environ["REPORT_DIR"]
pattern = os.path.join(report_dir, os.environ["PATTERN"])
json_path = os.environ["JSON_PATH"]
md_path = os.environ["MD_PATH"]

files = sorted(glob.glob(pattern))
if not files:
    print(f"ERROR: No governance report files matched {pattern}", file=sys.stderr)
    sys.exit(1)

total_tests = 0
failures = 0
errors = 0
skipped = 0
retention_tests = 0
replay_tests = 0
drift_tests = 0
signature_tests = 0

def tags_for_testcase(testcase):
    tags = [testcase.attrib.get("name", ""), testcase.attrib.get("classname", "")]
    for child in testcase:
        if child.tag in ("failure", "error") and child.attrib.get("message"):
            tags.append(child.attrib.get("message", ""))
    return " ".join(tags).lower()

for file_path in files:
    root = ET.parse(file_path).getroot()
    testcases = root.findall(".//testcase")
    total_tests += len(testcases)

    for testcase in testcases:
        haystack = tags_for_testcase(testcase)
        if "retention" in haystack and "audit" in haystack:
            retention_tests += 1
        if "replay" in haystack:
            replay_tests += 1
        if "drift" in haystack:
            drift_tests += 1
        if "signature" in haystack or "presigned" in haystack:
            signature_tests += 1

        if testcase.find("failure") is not None:
            failures += 1
        if testcase.find("error") is not None:
            errors += 1
        if testcase.find("skipped") is not None:
            skipped += 1

executed = total_tests - skipped
retention_ok = retention_tests > 0 or (replay_tests > 0 and signature_tests > 0)

payload = {
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "reportPattern": pattern,
    "reportFileCount": len(files),
    "tests": total_tests,
    "executed": executed,
    "skipped": skipped,
    "failures": failures,
    "errors": errors,
    "retentionEvidenceTests": retention_tests,
    "replayEvidenceTests": replay_tests,
    "driftEvidenceTests": drift_tests,
    "signatureEvidenceTests": signature_tests,
    "retentionEvidenceOk": retention_ok,
}

with open(json_path, "w", encoding="utf-8") as f:
    json.dump(payload, f, indent=2)

with open(md_path, "w", encoding="utf-8") as f:
    f.write("# Governance Evidence Summary\n\n")
    f.write(f"Generated: {payload['generatedAtUtc']}\n\n")
    f.write("| Metric | Value |\n")
    f.write("|---|---|\n")
    f.write(f"| Report Files | {payload['reportFileCount']} |\n")
    f.write(f"| Tests | {payload['tests']} |\n")
    f.write(f"| Executed (non-skipped) | {payload['executed']} |\n")
    f.write(f"| Skipped | {payload['skipped']} |\n")
    f.write(f"| Failures | {payload['failures']} |\n")
    f.write(f"| Errors | {payload['errors']} |\n")
    f.write(f"| Retention Evidence Tests | {payload['retentionEvidenceTests']} |\n")
    f.write(f"| Replay Evidence Tests | {payload['replayEvidenceTests']} |\n")
    f.write(f"| Drift Evidence Tests | {payload['driftEvidenceTests']} |\n")
    f.write(f"| Signature Evidence Tests | {payload['signatureEvidenceTests']} |\n")
    f.write(f"| Retention Evidence Present | {payload['retentionEvidenceOk']} |\n")

print(
    "Governance evidence summary: "
    f"files={payload['reportFileCount']} tests={payload['tests']} executed={payload['executed']} "
    f"failures={payload['failures']} errors={payload['errors']} retentionTests={payload['retentionEvidenceTests']}"
)

if payload["failures"] > 0 or payload["errors"] > 0:
    print("ERROR: Governance evidence export detected failing tests", file=sys.stderr)
    sys.exit(1)

if not retention_ok:
    print("ERROR: Governance evidence export requires at least one retention+audit test", file=sys.stderr)
    sys.exit(1)
PY

cp "${JSON_PATH}" "${LATEST_JSON}"
cp "${MD_PATH}" "${LATEST_MD}"

echo "Governance evidence summary generated: ${JSON_PATH}"
echo "Governance evidence summary generated: ${MD_PATH}"
