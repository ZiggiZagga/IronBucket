#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_UNDER_TEST="$ROOT_DIR/scripts/ci/verify-main-branch-protection.sh"
FIXTURE_ROOT=""
SERVER_PID=""

cleanup() {
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
  fi
  if [[ -n "$FIXTURE_ROOT" && -d "$FIXTURE_ROOT" ]]; then
    rm -rf "$FIXTURE_ROOT"
  fi
}

create_payload() {
  local fixture_root="$1"
  local strict_value="$2"
  local include_behavioral="$3"

  mkdir -p "$fixture_root/repos/ironbucket/test/branches/main"

  local behavioral_line='      {"context": "Sentinel Behavioral Gate", "app_id": 1},'
  if [[ "$include_behavioral" != "true" ]]; then
    behavioral_line=''
  fi

  cat >"$fixture_root/repos/ironbucket/test/branches/main/protection" <<EOF
{
  "required_status_checks": {
    "strict": ${strict_value},
    "checks": [
      {"context": "Build and Test All Modules", "app_id": 1},
      {"context": "Sentinel Roadmap Gate", "app_id": 1},
${behavioral_line}
      {"context": "jclouds MinIO CRUD Gate", "app_id": 1},
      {"context": "e2e-complete-suite", "app_id": 1}
    ]
  }
}
EOF
}

start_fixture_server() {
  local fixture_root="$1"
  local port="$2"

  (
    cd "$fixture_root"
    python3 -m http.server "$port" --bind 127.0.0.1 >/dev/null 2>&1
  ) &
  SERVER_PID="$!"

  # Wait until server responds.
  for _ in $(seq 1 40); do
    if curl -fsS "http://127.0.0.1:${port}/" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.25
  done

  echo "Fixture HTTP server did not become ready"
  return 1
}

find_free_port() {
  python3 - <<'PY'
import socket

with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

run_positive_case() {
  local port="$1"

  BRANCH_PROTECTION_STRICT=true \
  GITHUB_API_URL="http://127.0.0.1:${port}" \
  GITHUB_REPOSITORY="ironbucket/test" \
  GITHUB_TOKEN="fake-token" \
  TARGET_BRANCH="main" \
  REQUIRED_CHECK_RUNS="Build and Test All Modules,Sentinel Roadmap Gate,Sentinel Behavioral Gate,jclouds MinIO CRUD Gate,e2e-complete-suite" \
  bash "$SCRIPT_UNDER_TEST" >/dev/null
}

run_negative_case_missing_required_check() {
  local fixture_root="$1"
  local port="$2"

  create_payload "$fixture_root" true false

  if BRANCH_PROTECTION_STRICT=true \
    GITHUB_API_URL="http://127.0.0.1:${port}" \
    GITHUB_REPOSITORY="ironbucket/test" \
    GITHUB_TOKEN="fake-token" \
    TARGET_BRANCH="main" \
    REQUIRED_CHECK_RUNS="Build and Test All Modules,Sentinel Roadmap Gate,Sentinel Behavioral Gate,jclouds MinIO CRUD Gate,e2e-complete-suite" \
    bash "$SCRIPT_UNDER_TEST" >/dev/null 2>&1; then
    echo "Expected verify-main-branch-protection to fail when a required check is missing"
    return 1
  fi
}

main() {
  if [[ ! -f "$SCRIPT_UNDER_TEST" ]]; then
    echo "Missing script under test: $SCRIPT_UNDER_TEST"
    exit 1
  fi

  FIXTURE_ROOT="$(mktemp -d)"
  trap cleanup EXIT

  local port
  port="$(find_free_port)"
  create_payload "$FIXTURE_ROOT" true true
  start_fixture_server "$FIXTURE_ROOT" "$port"

  run_positive_case "$port"
  run_negative_case_missing_required_check "$FIXTURE_ROOT" "$port"

  echo "test-verify-main-branch-protection: all checks passed"
}

main "$@"