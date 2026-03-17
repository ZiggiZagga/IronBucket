#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "${ROOT_DIR}"

echo "[governance-roadmap-gate] running GovernanceIntegrityResilienceTest"

set +e
bash scripts/ci/run-maven-in-container.sh services/Sentinel-Gear -B -V -Dtest=GovernanceIntegrityResilienceTest test -Proadmap
MVN_EXIT=$?
set -e

if [[ "${MVN_EXIT}" -ne 0 ]]; then
	echo "[governance-roadmap-gate] test execution failed, collecting surefire diagnostics"
	bash scripts/ci/print-surefire-failures.sh services/Sentinel-Gear/target/surefire-reports "TEST-com.ironbucket.roadmap.GovernanceIntegrityResilienceTest*.xml" 15 || true
	exit "${MVN_EXIT}"
fi

echo "[governance-roadmap-gate] exporting governance evidence summary"
bash scripts/ci/export-governance-evidence-summary.sh

echo "[governance-roadmap-gate] gate passed"
