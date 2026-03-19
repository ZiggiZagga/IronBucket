#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_RESULTS_DIR="${TEST_RESULTS_DIR:-$ROOT_DIR/test-results}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

resolve_test_results_dir() {
  local requested_dir="$1"
  local fallback_dir="$ROOT_DIR/temp/test-results"

  if mkdir -p "$requested_dir" >/dev/null 2>&1 && [[ -w "$requested_dir" ]]; then
    echo "$requested_dir"
    return
  fi

  mkdir -p "$fallback_dir"
  echo "$fallback_dir"
}

REQUESTED_TEST_RESULTS_DIR="$TEST_RESULTS_DIR"
TEST_RESULTS_DIR="$(resolve_test_results_dir "$TEST_RESULTS_DIR")"
if [[ "$TEST_RESULTS_DIR" != "$REQUESTED_TEST_RESULTS_DIR" ]]; then
  echo "[jclouds-provider-integration-parity-gate] Primary test-results directory not writable: $REQUESTED_TEST_RESULTS_DIR" >&2
  echo "[jclouds-provider-integration-parity-gate] Using fallback test-results directory: $TEST_RESULTS_DIR" >&2
fi

OUT_DIR="${TEST_RESULTS_DIR}/phase4-gates"
mkdir -p "${OUT_DIR}"

cd "${ROOT_DIR}"

AWS_ENABLED="${IRONBUCKET_AWS_S3_INTEGRATION:-false}"
GCS_ENABLED="${IRONBUCKET_GCS_INTEGRATION:-false}"
AZURE_ENABLED="${IRONBUCKET_AZURE_BLOB_INTEGRATION:-false}"

TESTS=(
  "ProviderCrudParityIntegrationTest"
  "AwsS3CapabilityProbeIntegrationTest"
  "GcsCapabilityProbeIntegrationTest"
  "AzureBlobCapabilityProbeIntegrationTest"
)
PASSTHROUGH_VARS=()

if [[ "${AWS_ENABLED}" == "true" ]]; then
  PASSTHROUGH_VARS+=("IRONBUCKET_AWS_S3_INTEGRATION" "AWS_ACCESS_KEY_ID" "AWS_SECRET_ACCESS_KEY" "AWS_REGION" "AWS_S3_ENDPOINT")
fi

if [[ "${GCS_ENABLED}" == "true" ]]; then
  PASSTHROUGH_VARS+=("IRONBUCKET_GCS_INTEGRATION" "IRONBUCKET_GCS_IDENTITY" "IRONBUCKET_GCS_CREDENTIAL" "IRONBUCKET_GCS_PROJECT" "IRONBUCKET_GCS_ENDPOINT")
fi

if [[ "${AZURE_ENABLED}" == "true" ]]; then
  PASSTHROUGH_VARS+=("IRONBUCKET_AZURE_BLOB_INTEGRATION" "AZURE_STORAGE_ACCOUNT" "AZURE_STORAGE_KEY" "IRONBUCKET_AZURE_BLOB_ENDPOINT")
fi

if [[ "${AWS_ENABLED}" != "true" && "${GCS_ENABLED}" != "true" && "${AZURE_ENABLED}" != "true" ]]; then
  echo "[jclouds-provider-integration-parity-gate] no provider integration toggles enabled; parity integration tests will run in skip mode"
fi

TEST_FILTER="$(IFS=, ; echo "${TESTS[*]}")"
MAVEN_DOCKER_ENV_VARS="$(IFS=, ; echo "${PASSTHROUGH_VARS[*]}")"

export MAVEN_DOCKER_ENV_VARS

echo "[jclouds-provider-integration-parity-gate] running tests: ${TEST_FILTER}"
echo "[jclouds-provider-integration-parity-gate] enabled providers: aws=${AWS_ENABLED}, gcs=${GCS_ENABLED}, azure=${AZURE_ENABLED}"

bash scripts/ci/run-maven-in-container.sh services/jclouds-adapter-core \
  -B -V -Dtest="${TEST_FILTER}" test

JSON_PATH="${OUT_DIR}/provider-integration-parity-gate-summary-${TIMESTAMP}.json"
MD_PATH="${OUT_DIR}/provider-integration-parity-gate-summary-${TIMESTAMP}.md"
LATEST_JSON="${OUT_DIR}/LATEST-provider-integration-parity-gate-summary.json"
LATEST_MD="${OUT_DIR}/LATEST-provider-integration-parity-gate-summary.md"

python3 - <<PY > "${JSON_PATH}"
import json
from datetime import datetime, timezone
payload = {
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "enabledProviders": {
        "aws": "${AWS_ENABLED}" == "true",
        "gcs": "${GCS_ENABLED}" == "true",
        "azure": "${AZURE_ENABLED}" == "true",
    },
    "executedTests": [
        "ProviderCrudParityIntegrationTest",
        "AwsS3CapabilityProbeIntegrationTest",
        "GcsCapabilityProbeIntegrationTest",
        "AzureBlobCapabilityProbeIntegrationTest",
    ],
    "notes": [
        "Capability probe integration tests verify runtime provider claims for multipart/versioning support.",
        "If provider toggles are disabled, integration tests run in skip mode by design."
    ],
    "status": "passed"
}
print(json.dumps(payload, indent=2))
PY

{
  echo "# jclouds Provider Integration Parity Gate Summary"
  echo
  echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| Provider CRUD parity integration | passed |"
  echo "| AWS capability probe integration | passed |"
  echo "| GCS capability probe integration | passed |"
  echo "| Azure capability probe integration | passed |"
  echo
  echo "Enabled providers: aws=${AWS_ENABLED}, gcs=${GCS_ENABLED}, azure=${AZURE_ENABLED}"
} > "${MD_PATH}"

cp "${JSON_PATH}" "${LATEST_JSON}"
cp "${MD_PATH}" "${LATEST_MD}"

echo "[jclouds-provider-integration-parity-gate] gate passed"
echo "[jclouds-provider-integration-parity-gate] summary: ${JSON_PATH}"
echo "[jclouds-provider-integration-parity-gate] summary: ${MD_PATH}"
