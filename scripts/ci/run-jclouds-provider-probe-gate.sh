#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "${ROOT_DIR}"

TEST_FILTER="AwsS3CapabilityProbeTest,GcsCapabilityProbeTest,AzureBlobCapabilityProbeTest"

echo "[jclouds-provider-probe-gate] running tests: ${TEST_FILTER}"

bash scripts/ci/run-maven-in-container.sh services/jclouds-adapter-core \
  -B -V -Dtest="${TEST_FILTER}" test

echo "[jclouds-provider-probe-gate] gate passed"
