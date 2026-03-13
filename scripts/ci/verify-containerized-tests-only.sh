#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

WORKFLOW_GLOB="${WORKFLOW_GLOB:-${REPO_ROOT}/.github/workflows/*.yml}"

echo "[container-test-policy] checking workflow commands for host-side Maven test execution"

violations=()

for workflow in $WORKFLOW_GLOB; do
  [[ -f "$workflow" ]] || continue

  line_no=0
  while IFS= read -r line; do
    line_no=$((line_no + 1))
    stripped="${line#${line%%[![:space:]]*}}"

    if [[ -z "$stripped" || "$stripped" == \#* ]]; then
      continue
    fi

    # Block direct host-side maven test goals in workflows.
    if [[ "$stripped" =~ (^|[[:space:]])mvn[[:space:]].*(^|[[:space:]])(test|verify|integration-test|failsafe:integration-test|failsafe:verify)([[:space:]]|$) ]]; then
      if [[ "$stripped" =~ -DskipTests(=true)?|-Dmaven\.test\.skip(=true)? ]]; then
        continue
      fi

      violations+=("${workflow}:${line_no}: ${stripped}")
    fi
  done < "$workflow"
done

if [[ ${#violations[@]} -gt 0 ]]; then
  echo "[container-test-policy] ERROR: found host-side Maven test execution in workflow files:" >&2
  for v in "${violations[@]}"; do
    echo "  - $v" >&2
  done
  echo "[container-test-policy] Use scripts/ci/run-maven-in-container.sh or scripts/ci/run-core-module-tests-container.sh instead." >&2
  exit 1
fi

echo "[container-test-policy] PASS: workflows comply with containerized-test policy"