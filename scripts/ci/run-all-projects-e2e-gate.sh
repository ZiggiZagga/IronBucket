#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="$ROOT_DIR/test-results/all-projects-e2e-gate/$TIMESTAMP"
LOG_FILE="$OUT_DIR/gate.log"
REPORT_FILE="$OUT_DIR/ALL_PROJECTS_E2E_GATE_REPORT.md"

mkdir -p "$OUT_DIR"

log() {
  printf '[all-projects-e2e-gate] %s\n' "$*" | tee -a "$LOG_FILE"
}

run_java_modules() {
  local -a modules=(
    "services/Pactum-Scroll"
    "services/Sentinel-Gear"
    "services/Claimspindel"
    "services/Brazz-Nossel"
    "services/Buzzle-Vane"
    "services/Graphite-Forge"
    "services/jclouds-adapter-core"
    "tools/Vault-Smith"
    "tools/Storage-Conductor"
    "tools/graphite-admin-shell"
    "tools/test-suite"
  )

  JAVA_TOTAL=${#modules[@]}
  JAVA_PASSED=0

  for module in "${modules[@]}"; do
    log "Java module test: $module"
    if (cd "$ROOT_DIR/$module" && mvn -B test) >>"$LOG_FILE" 2>&1; then
      JAVA_PASSED=$((JAVA_PASSED + 1))
      log "PASS: $module"
    else
      log "FAIL: $module"
      JAVA_FAILED_MODULES+="$module\n"
    fi
  done
}

run_ui_projects() {
  local -a projects=(
    "ironbucket-app"
    "ironbucket-app-nextjs"
  )

  UI_TOTAL=${#projects[@]}
  UI_PASSED=0

  for project in "${projects[@]}"; do
    log "UI project checks (npm ci/test/build): $project"
    if (cd "$ROOT_DIR/$project" && npm ci --no-audit --no-fund && npm test && npm run build) >>"$LOG_FILE" 2>&1; then
      if [[ "$project" == "ironbucket-app-nextjs" ]]; then
        log "Installing Playwright Chromium browser: $project"
        if ! (cd "$ROOT_DIR/$project" && npx playwright install chromium) >>"$LOG_FILE" 2>&1; then
          log "FAIL: $project playwright browser install"
          UI_FAILED_PROJECTS+="$project (playwright install)\n"
          continue
        fi

        log "UI playwright e2e scenario: $project"
        if ! (cd "$ROOT_DIR/$project" && npm run test:e2e:ui) >>"$LOG_FILE" 2>&1; then
          log "FAIL: $project playwright e2e"
          UI_FAILED_PROJECTS+="$project (playwright e2e)\n"
          continue
        fi
      fi

      UI_PASSED=$((UI_PASSED + 1))
      log "PASS: $project"
    else
      log "FAIL: $project"
      UI_FAILED_PROJECTS+="$project\n"
    fi
  done
}

JAVA_TOTAL=0
JAVA_PASSED=0
UI_TOTAL=0
UI_PASSED=0
JAVA_FAILED_MODULES=""
UI_FAILED_PROJECTS=""

log "Starting all-projects e2e gate"
run_java_modules
run_ui_projects

JAVA_OK=false
UI_OK=false
OVERALL_OK=false

if [[ "$JAVA_PASSED" -eq "$JAVA_TOTAL" ]]; then
  JAVA_OK=true
fi

if [[ "$UI_PASSED" -eq "$UI_TOTAL" ]]; then
  UI_OK=true
fi

if [[ "$JAVA_OK" == "true" && "$UI_OK" == "true" ]]; then
  OVERALL_OK=true
fi

cat > "$REPORT_FILE" <<EOF
# All Projects E2E Gate Report

- Timestamp (UTC): $TIMESTAMP
- Log file: $LOG_FILE

## Results

| Area | Passed | Total | Result |
|---|---:|---:|---|
| Java projects | $JAVA_PASSED | $JAVA_TOTAL | $JAVA_OK |
| UI projects | $UI_PASSED | $UI_TOTAL | $UI_OK |
| Overall | - | - | $OVERALL_OK |

## Failures

### Java module failures
$(if [[ -n "$JAVA_FAILED_MODULES" ]]; then printf '%b' "$JAVA_FAILED_MODULES" | sed 's/^/- /'; else echo "- none"; fi)

### UI project failures
$(if [[ -n "$UI_FAILED_PROJECTS" ]]; then printf '%b' "$UI_FAILED_PROJECTS" | sed 's/^/- /'; else echo "- none"; fi)
EOF

log "Report generated: $REPORT_FILE"

if [[ "$OVERALL_OK" != "true" ]]; then
  exit 1
fi

log "All-projects e2e gate passed"
