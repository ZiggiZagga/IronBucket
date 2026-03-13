#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

export MAVEN_DOCKER_ENV_VARS="${MAVEN_DOCKER_ENV_VARS:-S3_TESTS_ENABLED}"
export S3_TESTS_ENABLED="${S3_TESTS_ENABLED:-true}"
export MAVEN_CONTAINER_NETWORK="${MAVEN_CONTAINER_NETWORK:-host}"
AUTO_START_MINIO_FOR_TESTS="${AUTO_START_MINIO_FOR_TESTS:-true}"
MINIO_CONTAINER_NAME="${MINIO_CONTAINER_NAME:-ironbucket-core-tests-minio}"
MINIO_STARTED_BY_SCRIPT=false

cleanup() {
  if [[ "$MINIO_STARTED_BY_SCRIPT" == "true" ]]; then
    docker rm -f "$MINIO_CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

wait_minio_ready() {
  local max_attempts="${1:-40}"
  for ((attempt=1; attempt<=max_attempts; attempt++)); do
    if curl -fsS http://127.0.0.1:9000/minio/health/live >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

ensure_minio_for_s3_tests() {
  if [[ "$S3_TESTS_ENABLED" != "true" ]]; then
    return 0
  fi

  if curl -fsS http://127.0.0.1:9000/minio/health/live >/dev/null 2>&1; then
    echo "[core-tests] MinIO already reachable at http://127.0.0.1:9000"
    return 0
  fi

  if [[ "$AUTO_START_MINIO_FOR_TESTS" != "true" ]]; then
    echo "[core-tests] MinIO not reachable and AUTO_START_MINIO_FOR_TESTS=false" >&2
    return 1
  fi

  echo "[core-tests] Starting MinIO test container (${MINIO_CONTAINER_NAME})"
  docker rm -f "$MINIO_CONTAINER_NAME" >/dev/null 2>&1 || true
  docker run -d \
    --name "$MINIO_CONTAINER_NAME" \
    -p 9000:9000 \
    -e MINIO_ROOT_USER=minioadmin \
    -e MINIO_ROOT_PASSWORD=minioadmin \
    minio/minio:latest server /data >/dev/null

  if ! wait_minio_ready 40; then
    echo "[core-tests] MinIO failed readiness" >&2
    docker logs "$MINIO_CONTAINER_NAME" || true
    return 1
  fi

  MINIO_STARTED_BY_SCRIPT=true
  echo "[core-tests] MinIO started for containerized test run"
}

echo "[core-tests] Running core module tests in containers only"
ensure_minio_for_s3_tests

echo "[core-tests] Build Pactum-Scroll shared contracts"
bash "${SCRIPT_DIR}/run-maven-in-container.sh" "services/Pactum-Scroll" clean install -B -V

echo "[core-tests] Install Vault-Smith for dependent modules"
bash "${SCRIPT_DIR}/run-maven-in-container.sh" "tools/Vault-Smith" clean install -DskipTests -B -V

MODULES=(
  "services/Sentinel-Gear"
  "services/Claimspindel"
  "services/Brazz-Nossel"
  "services/Buzzle-Vane"
  "tools/Storage-Conductor"
)

for module in "${MODULES[@]}"; do
  echo "[core-tests] Testing ${module}"
  bash "${SCRIPT_DIR}/run-maven-in-container.sh" "${module}" clean test -B -V
done

echo "[core-tests] All core module tests passed in containers"