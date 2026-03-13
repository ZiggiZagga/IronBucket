#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <module-path> <maven-args...>" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

MODULE_PATH="$1"
shift

if [[ ! -d "${REPO_ROOT}/${MODULE_PATH}" ]]; then
  echo "ERROR: Module path not found: ${MODULE_PATH}" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker command not found" >&2
  exit 1
fi

MAVEN_CONTAINER_IMAGE="${MAVEN_CONTAINER_IMAGE:-maven:3.9.11-eclipse-temurin-25}"
MAVEN_CONTAINER_NETWORK="${MAVEN_CONTAINER_NETWORK:-bridge}"
MAVEN_CACHE_DIR="${MAVEN_CACHE_DIR:-${HOME}/.m2}"
MAVEN_USER_ID="${MAVEN_USER_ID:-$(id -u)}"
MAVEN_GROUP_ID="${MAVEN_GROUP_ID:-$(id -g)}"

mkdir -p "${MAVEN_CACHE_DIR}"

ENV_ARGS=()
if [[ -n "${MAVEN_DOCKER_ENV_VARS:-}" ]]; then
  IFS=',' read -r -a PASSTHROUGH_VARS <<< "${MAVEN_DOCKER_ENV_VARS}"
  for var_name in "${PASSTHROUGH_VARS[@]}"; do
    var_name="${var_name//[[:space:]]/}"
    [[ -z "${var_name}" ]] && continue
    if [[ -n "${!var_name:-}" ]]; then
      ENV_ARGS+=("-e" "${var_name}=${!var_name}")
    fi
  done
fi

echo "[maven-container] image=${MAVEN_CONTAINER_IMAGE} network=${MAVEN_CONTAINER_NETWORK} module=${MODULE_PATH}"

docker run --rm \
  --user "${MAVEN_USER_ID}:${MAVEN_GROUP_ID}" \
  --network "${MAVEN_CONTAINER_NETWORK}" \
  --add-host host.docker.internal:host-gateway \
  -e "HOME=/tmp" \
  -e "MAVEN_CONFIG=${MAVEN_CACHE_DIR}" \
  -e "MAVEN_OPTS=-Dmaven.repo.local=${MAVEN_CACHE_DIR}/repository" \
  "${ENV_ARGS[@]}" \
  -v "${REPO_ROOT}:/workspace" \
  -v "${MAVEN_CACHE_DIR}:${MAVEN_CACHE_DIR}" \
  -w "/workspace/${MODULE_PATH}" \
  "${MAVEN_CONTAINER_IMAGE}" \
  mvn "$@"