#!/bin/bash
################################################################################
# MIGRATION CHECKLIST FOR EXISTING SCRIPTS
# 
# Use this as a reference when updating existing scripts to the new standards
################################################################################

cat << 'EOF'

╔══════════════════════════════════════════════════════════════════════════════╗
║            SCRIPT MIGRATION CHECKLIST - STANDARDIZATION v1.0                ║
╚══════════════════════════════════════════════════════════════════════════════╝

PHASE 1: HEADER & INITIALIZATION
════════════════════════════════════════════════════════════════════════════════

□ Change "set -e" to "set -euo pipefail"
  ✓ Prevents undefined variable errors
  ✓ Prevents pipe errors from being ignored

□ Remove hardcoded PROJECT_ROOT, color definitions, timestamps
  ✓ These are now in .env.defaults

□ Add environment loading after shebang:
  
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  source "$SCRIPT_DIR/.env.defaults"
  source "$SCRIPT_DIR/lib/common.sh"
  register_error_trap

□ Remove all custom logging functions
  ✓ Use log_info, log_debug, log_success, log_error, log_warn, log_section


PHASE 2: ENVIRONMENT VARIABLES
════════════════════════════════════════════════════════════════════════════════

□ Replace ALL hardcoded paths:
  ❌ /workspaces/IronBucket → ✅ $PROJECT_ROOT
  ❌ /workspaces/IronBucket/temp → ✅ $TEMP_DIR
  ❌ /workspaces/IronBucket/test-results/logs → ✅ $LOG_DIR
  ❌ /workspaces/IronBucket/steel-hammer → ✅ $STEEL_HAMMER_DIR

□ Replace service URLs with environment variables:
  ❌ http://localhost:7081 → ✅ $KEYCLOAK_URL
  ❌ http://localhost:9000 → ✅ $MINIO_URL
  ❌ localhost → ✅ $POSTGRES_HOST
  ❌ http://keycloak:8080 (for containers) → ✅ $KEYCLOAK_URL

□ Check script works in BOTH contexts:
  ✓ IS_CONTAINER=false ./script.sh (host mode)
  ✓ IS_CONTAINER=true ./script.sh (container mode)


PHASE 3: TEMP FILE HANDLING
════════════════════════════════════════════════════════════════════════════════

□ CRITICAL: Replace ALL /tmp references with $TEMP_DIR:
  ❌ /tmp/test-file.txt → ✅ $TEMP_DIR/test-file.txt
  ❌ mkdir -p /tmp/... → ✅ mkdir -p "$TEMP_DIR/..."
  ❌ docker run -v /tmp:... → ✅ docker run -v "$TEMP_DIR:..."

□ For log files:
  ❌ tee -a /tmp/app.log → ✅ tee -a "$LOG_FILE"
  ❌ > /tmp/maven.log → ✅ >> "$LOG_DIR/maven-$TIMESTAMP_SHORT.log"


PHASE 4: LOGGING
════════════════════════════════════════════════════════════════════════════════

□ Replace echo with log functions:
  ❌ echo "Starting tests..." → ✅ log_info "Starting tests..."
  ❌ echo -e "${GREEN}✅ Done${NC}" → ✅ log_success "Done"
  ❌ echo -e "${RED}Error${NC}" >&2 → ✅ log_error "Error"
  ❌ echo "━━━━━━━━━━━" → ✅ log_section "Title"

□ Ensure all script output is logged:
  ✓ Use tee -a "$LOG_FILE" for important operations
  ✓ All log_* functions auto-log to $LOG_FILE


PHASE 5: ERROR HANDLING
════════════════════════════════════════════════════════════════════════════════

□ Add error handling for critical commands:
  ❌ mvn clean test
  ✅ mvn clean test || error_exit 1 "Maven tests failed"

□ Validate prerequisites at start:
  ✓ require_directory "$TEMP_DIR"
  ✓ require_file "$PROJECT_ROOT/pom.xml"
  ✓ check_command "docker"

□ Add timeout handling for services:
  ✓ check_service_health "$KEYCLOAK_URL/health" "Keycloak" 5 2


PHASE 6: SERVICE HEALTH CHECKS
════════════════════════════════════════════════════════════════════════════════

□ Replace manual curl checks:
  ❌ curl -s http://localhost:8080/health || exit 1
  ✅ check_service_health "$SENTINEL_GEAR_URL/health" "Sentinel-Gear" 5 2

□ Use container-aware URLs:
  ✅ Works for both: if is_container; then ...; fi


PHASE 7: TIMING & METRICS
════════════════════════════════════════════════════════════════════════════════

□ Add execution timing:
  timer_start
  # ... do work ...
  log_info "Completed in: $(timer_elapsed_formatted)"

□ Track important milestones:
  ✓ Start of major sections
  ✓ Test results
  ✓ File operations


════════════════════════════════════════════════════════════════════════════════
COMMON MIGRATION PATTERNS
════════════════════════════════════════════════════════════════════════════════

PATTERN 1: Directory Setup
────────────────────────────
BEFORE:
    mkdir -p "$PROJECT_ROOT/test-results/logs"
    LOG_FILE="$PROJECT_ROOT/test-execution.log"

AFTER:
    # Directories already created by .env.defaults
    # LOG_FILE already defined
    echo "Logs will be in: $LOG_DIR"


PATTERN 2: Service Checks
────────────────────────────
BEFORE:
    KEYCLOAK_URL="http://localhost:7081"
    if ! curl -s "$KEYCLOAK_URL/health" > /dev/null; then
        echo "ERROR: Keycloak not responding"
        exit 1
    fi

AFTER:
    check_service_health "$KEYCLOAK_URL/health" "Keycloak" 5 2 || {
        log_error "Keycloak not ready"
        exit 1
    }


PATTERN 3: Temp File Handling
────────────────────────────
BEFORE:
    mkdir -p /tmp/ironbucket-test
    echo "data" > /tmp/ironbucket-test/test.txt
    docker run -v /tmp/ironbucket-test:/data ...

AFTER:
    mkdir -p "$TEMP_DIR/ironbucket-test"
    echo "data" > "$TEMP_DIR/ironbucket-test/test.txt"
    docker run -v "$TEMP_DIR:/data:ro" ...


PATTERN 4: Logging Output
────────────────────────────
BEFORE:
    #!/bin/bash
    set -e
    
    echo -e "${BLUE}=== Running Tests ===${NC}"
    if mvn test > /tmp/maven.log 2>&1; then
        echo -e "${GREEN}✅ Tests passed${NC}"
    else
        echo -e "${RED}❌ Tests failed${NC}"
        cat /tmp/maven.log
        exit 1
    fi

AFTER:
    #!/bin/bash
    set -euo pipefail
    
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    source "$SCRIPT_DIR/.env.defaults"
    source "$SCRIPT_DIR/lib/common.sh"
    register_error_trap
    
    log_section "Running Tests"
    if mvn test >> "$LOG_DIR/maven-$TIMESTAMP_SHORT.log" 2>&1; then
        log_success "Tests passed"
    else
        log_error "Tests failed"
        tail -20 "$LOG_DIR/maven-$TIMESTAMP_SHORT.log"
        exit 1
    fi


════════════════════════════════════════════════════════════════════════════════
TESTING AFTER MIGRATION
════════════════════════════════════════════════════════════════════════════════

1. Test on host (default):
   $ ./scripts/my-script.sh
   ✓ Verifies: localhost URLs, /tmp not used, logs created

2. Test in container:
   $ IS_CONTAINER=true ./scripts/my-script.sh
   ✓ Verifies: container URLs, $TEMP_DIR accessible, env vars correct

3. Verify no hardcoded paths:
   $ grep -n "localhost\|/tmp\|/workspaces" scripts/my-script.sh
   ✓ Should return 0 results (or only in comments)

4. Check environment loading:
   $ grep -n "^PROJECT_ROOT\|^KEYCLOAK_URL" scripts/my-script.sh
   ✓ Should only be in .env.defaults, not in individual scripts

5. Verify logging:
   $ tail -50 test-results/logs/script-execution.log
   ✓ Should see structured log entries with timestamps


════════════════════════════════════════════════════════════════════════════════
SCRIPTS ALREADY MIGRATED
════════════════════════════════════════════════════════════════════════════════

✅ scripts/spinup.sh
✅ scripts/run-all-tests-complete.sh
✅ scripts/comprehensive-test-reporter.sh
✅ scripts/verify-test-pathway.py
✅ scripts/e2e/e2e-alice-bob-test.sh
✅ steel-hammer/test-s3-operations.sh
✅ tools/Storage-Conductor/run-integration-tests.sh

SCRIPTS NEEDING MIGRATION:

□ scripts/e2e/e2e-test-standalone.sh
□ scripts/e2e/run-containerized-tests.sh
□ scripts/e2e/test-containerized.sh
□ tools/Storage-Conductor/run-tests.sh
□ tools/Storage-Conductor/orchestrate-tests.sh
□ tools/Storage-Conductor/docker-entrypoint.sh
□ steel-hammer/test-s3-docker.sh
□ steel-hammer/test-scripts/*.sh
□ tests/roadmap/*.sh


════════════════════════════════════════════════════════════════════════════════

Quick Start - Copy/Paste Template for New Scripts:

#!/bin/bash
# [Your script description]
# Usage: ./script.sh [options]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/.env.defaults"
source "$SCRIPT_DIR/lib/common.sh"
register_error_trap

main() {
    log_section "Script Title"
    timer_start
    
    # Your code here
    
    log_success "Completed in $(timer_elapsed_formatted)"
}

main "$@"

EOF
