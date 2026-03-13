#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_UNDER_TEST="$ROOT_DIR/scripts/ci/verify-e2e-doc-sync.sh"
FIXTURE_ROOT=""

create_fixture() {
  local fixture_root="$1"

  mkdir -p "$fixture_root/.github/workflows"
  mkdir -p "$fixture_root/docs"
  mkdir -p "$fixture_root/scripts/e2e"

  cat >"$fixture_root/.github/workflows/e2e-complete-suite.yml" <<'EOF'
name: e2e-complete-suite
jobs:
  e2e:
    steps:
      - run: bash scripts/ci/run-first-user-experience-gate.sh
      - run: bash scripts/ci/run-observability-infra-gate.sh
      - run: upload test-results/phase1-4-proof/
      - run: upload test-results/phase2-observability/
EOF

  cat >"$fixture_root/docs/CI-CD-PIPELINE.md" <<'EOF'
# CI/CD
Uses e2e-complete-suite with scripts/e2e/prove-phase1-4-complete.sh and scripts/e2e/prove-phase2-observability.sh.
EOF

  cat >"$fixture_root/docs/E2E-QUICKSTART.md" <<'EOF'
Run: scripts/e2e/prove-phase1-4-complete.sh
EOF

  cat >"$fixture_root/docs/E2E-OBSERVABILITY-GUIDE.md" <<'EOF'
Run: scripts/e2e/prove-phase2-observability.sh
EOF

  cat >"$fixture_root/README.md" <<'EOF'
See E2E-QUICKSTART.md and E2E-OBSERVABILITY-GUIDE.md
EOF

  cat >"$fixture_root/scripts/e2e/prove-phase1-4-complete.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

  cat >"$fixture_root/scripts/e2e/prove-phase2-observability.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
}

run_positive_case() {
  local fixture_root="$1"
  IRONBUCKET_ROOT_DIR="$fixture_root" bash "$SCRIPT_UNDER_TEST" >/dev/null
}

run_negative_case_missing_phase2_doc_reference() {
  local fixture_root="$1"
  # Remove required Phase 2 proof reference to assert failure mode.
  cat >"$fixture_root/docs/E2E-OBSERVABILITY-GUIDE.md" <<'EOF'
# Missing required proof command on purpose
EOF

  if IRONBUCKET_ROOT_DIR="$fixture_root" bash "$SCRIPT_UNDER_TEST" >/dev/null 2>&1; then
    echo "Expected verify-e2e-doc-sync to fail when Phase 2 proof command is missing"
    return 1
  fi
}

main() {
  if [[ ! -f "$SCRIPT_UNDER_TEST" ]]; then
    echo "Missing script under test: $SCRIPT_UNDER_TEST"
    exit 1
  fi

  FIXTURE_ROOT="$(mktemp -d)"
  trap 'rm -rf "$FIXTURE_ROOT"' EXIT

  create_fixture "$FIXTURE_ROOT"

  run_positive_case "$FIXTURE_ROOT"
  run_negative_case_missing_phase2_doc_reference "$FIXTURE_ROOT"

  echo "test-verify-e2e-doc-sync: all checks passed"
}

main "$@"
