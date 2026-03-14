#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "${ROOT_DIR}"

echo "[governance-roadmap-gate] running GovernanceIntegrityResilienceTest"

bash scripts/ci/run-maven-in-container.sh services/Sentinel-Gear -B -V -Dtest=GovernanceIntegrityResilienceTest test -Proadmap

echo "[governance-roadmap-gate] exporting governance evidence summary"
bash scripts/ci/export-governance-evidence-summary.sh

echo "[governance-roadmap-gate] gate passed"
