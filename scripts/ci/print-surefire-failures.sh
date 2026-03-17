#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -lt 2 ]]; then
  echo "Usage: $0 <report_dir> <glob_pattern> [max_items]" >&2
  exit 2
fi

REPORT_DIR="$1"
PATTERN="$2"
MAX_ITEMS="${3:-20}"

if [[ ! -d "${REPORT_DIR}" ]]; then
  echo "No surefire directory found at ${REPORT_DIR}" >&2
  exit 0
fi

export REPORT_DIR PATTERN MAX_ITEMS
python3 - <<'PY'
import glob
import os
import sys
import xml.etree.ElementTree as ET

report_dir = os.environ["REPORT_DIR"]
pattern = os.path.join(report_dir, os.environ["PATTERN"])
max_items = int(os.environ.get("MAX_ITEMS", "20"))

files = sorted(glob.glob(pattern))
if not files:
    print(f"No surefire report files matched: {pattern}", file=sys.stderr)
    sys.exit(0)

issues = []
for file_path in files:
    root = ET.parse(file_path).getroot()
    for testcase in root.findall('.//testcase'):
        failure = testcase.find('failure')
        error = testcase.find('error')
        if failure is None and error is None:
            continue

        node = failure if failure is not None else error
        kind = 'failure' if failure is not None else 'error'
        classname = testcase.attrib.get('classname', '<unknown-class>')
        name = testcase.attrib.get('name', '<unknown-test>')
        message = node.attrib.get('message', '').strip()
        text = (node.text or '').strip().splitlines()
        first_line = text[0].strip() if text else ''
        details = message if message else first_line
        issues.append((kind, classname, name, details, os.path.basename(file_path)))

if not issues:
    print(f"No failing testcases found in reports matched by: {pattern}")
    sys.exit(0)

print("---- Surefire Failure Summary ----")
print(f"Report files scanned: {len(files)}")
print(f"Failing testcases found: {len(issues)}")
print("")

for idx, (kind, classname, name, details, report_file) in enumerate(issues[:max_items], start=1):
    print(f"{idx}. [{kind}] {classname}.{name}")
    print(f"   report: {report_file}")
    if details:
        print(f"   detail: {details}")

if len(issues) > max_items:
    print("")
    print(f"... truncated: showing first {max_items} of {len(issues)} failing testcases")
PY
