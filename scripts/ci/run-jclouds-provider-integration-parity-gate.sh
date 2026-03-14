#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "${ROOT_DIR}"

AWS_ENABLED="${IRONBUCKET_AWS_S3_INTEGRATION:-false}"
GCS_ENABLED="${IRONBUCKET_GCS_INTEGRATION:-false}"
AZURE_ENABLED="${IRONBUCKET_AZURE_BLOB_INTEGRATION:-false}"

TESTS=("ProviderCrudParityIntegrationTest")
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

echo "[jclouds-provider-integration-parity-gate] gate passed"
