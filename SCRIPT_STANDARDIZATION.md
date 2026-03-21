#!/bin/bash
################################################################################
# IRONBUCKET SCRIPTS - BEST PRACTICES GUIDE
# 
# This document shows the standardized pattern all scripts should follow
# after the refactoring changes.
################################################################################

cat << 'EOF'

╔══════════════════════════════════════════════════════════════════════════════╗
║                   IRONBUCKET SCRIPT STANDARDIZATION                         ║
║                        Best Practices Reference                              ║
╚══════════════════════════════════════════════════════════════════════════════╝

═════════════════════════════════════════════════════════════════════════════════
1. SCRIPT HEADER & INITIALIZATION
═════════════════════════════════════════════════════════════════════════════════

Every script should follow this pattern:

    #!/bin/bash
    # Description of what the script does
    # Usage: ./script.sh [options]

    set -euo pipefail

    # Load environment and common functions
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    source "$SCRIPT_DIR/.env.defaults"
    source "$SCRIPT_DIR/lib/common.sh"

    # Register error trap for better error handling
    register_error_trap

    # Your script code here...

KEY POINTS:
    • set -euo pipefail ensures errors cause exit
    • SCRIPT_DIR uses relative path resolution
    • Load .env.defaults FIRST, then lib/common.sh
    • Always register error trap for debugging


═════════════════════════════════════════════════════════════════════════════════
2. ENVIRONMENT VARIABLES
═════════════════════════════════════════════════════════════════════════════════

After sourcing .env.defaults, these are ALWAYS available:

DIRECTORIES:
    $PROJECT_ROOT           - Project root: /workspaces/IronBucket
    $TEMP_DIR              - Unified temp: $PROJECT_ROOT/build/temp
    $LOG_DIR               - Logs: $PROJECT_ROOT/test-results/logs
    $ARTIFACT_DIR          - Artifacts: $PROJECT_ROOT/test-results/artifacts
    $REPORTS_DIR           - Reports: $PROJECT_ROOT/test-results/reports
    $STEEL_HAMMER_DIR      - Services: $PROJECT_ROOT/steel-hammer

SERVICE ENDPOINTS (Container-Aware):
    $KEYCLOAK_URL          - https://keycloak:8080 (container) or localhost:7081 (host)
    $MINIO_URL             - https://minio:9000 (container) or localhost:9000 (host)
    $SENTINEL_GEAR_URL     - https://sentinel-gear:8080 (container) or localhost:8080 (host)
    $POSTGRES_HOST         - postgres (container) or localhost (host)

CONTEXT:
    $IS_CONTAINER          - true if running in container, false otherwise
    $LOG_LEVEL             - DEBUG, INFO, WARN, or ERROR

═════════════════════════════════════════════════════════════════════════════════
3. TEMP FILES - NEVER USE /tmp DIRECTLY
═════════════════════════════════════════════════════════════════════════════════

❌ WRONG:
    echo "data" > /tmp/myfile.txt
    docker run -v /tmp/myfile.txt:/data ...

✅ CORRECT:
    echo "data" > "$TEMP_DIR/myfile.txt"
    docker run -v "$TEMP_DIR:/data:ro" ...

WHY:
    • /tmp may be cleared on reboot
    • /tmp not accessible from all containers
    • $TEMP_DIR is persistent and shared
    • Centralized location for debugging


═════════════════════════════════════════════════════════════════════════════════
4. LOGGING - USE COMMON.SH FUNCTIONS
═════════════════════════════════════════════════════════════════════════════════

Available functions (automatically logging to $LOG_FILE):

    log_info "Message"          - ℹ️  Info level
    log_debug "Message"         - 🔍 Debug level (only if LOG_LEVEL=DEBUG)
    log_success "Message"       - ✅ Success level
    log_warn "Message"          - ⚠️  Warning level
    log_error "Message"         - ❌ Error level (to stderr)
    log_section "Title"         - 📋 Section header
    error_exit 1 "Message"      - ❌ Log error and exit with code

❌ WRONG:
    echo "Done"
    echo -e "${RED}Failed${NC}"
    echo "..." > /tmp/app.log

✅ CORRECT:
    log_success "Tests passed"
    log_error "Service unavailable"
    log_section "Running Integration Tests"


═════════════════════════════════════════════════════════════════════════════════
5. SERVICE HEALTH CHECKS
═════════════════════════════════════════════════════════════════════════════════

❌ WRONG:
    curl -s https://localhost:8080/health || exit 1

✅ CORRECT:
    check_service_health "$SENTINEL_GEAR_URL/health" "Sentinel-Gear" 5 2

This function:
    • Handles both container and host contexts automatically
    • Retries with exponential backoff
    • Logs appropriately
    • Returns proper exit codes


═════════════════════════════════════════════════════════════════════════════════
6. CONTAINER VS HOST AWARENESS
═════════════════════════════════════════════════════════════════════════════════

Scripts automatically work in both contexts:

IN CONTAINER:
    $IS_CONTAINER = true
    $KEYCLOAK_URL = https://keycloak:8080
    Services accessible via container DNS

ON HOST:
    $IS_CONTAINER = false
    $KEYCLOAK_URL = https://localhost:7081
    Services accessible via localhost + mapped ports

Your script just uses $KEYCLOAK_URL and it works everywhere!

✅ ALWAYS USE:
    if is_container; then
        # Container-specific logic
    else
        # Host-specific logic
    fi

    get_service_url "keycloak"  # Returns correct URL


═════════════════════════════════════════════════════════════════════════════════
7. VALIDATION & ERROR HANDLING
═════════════════════════════════════════════════════════════════════════════════

❌ WRONG:
    mvn test
    # Hope it worked?

✅ CORRECT:
    if ! mvn test; then
        log_error "Maven tests failed"
        exit 1
    fi
    log_success "Maven tests passed"

Or use error_exit:
    mvn test || error_exit 1 "Maven tests failed"

Validate prerequisites:
    require_file "$TEMP_DIR/pom.xml"
    require_directory "$STEEL_HAMMER_DIR"
    check_command "docker"


═════════════════════════════════════════════════════════════════════════════════
8. TIMING & METRICS
═════════════════════════════════════════════════════════════════════════════════

track execution time:
    timer_start
    
    # ... do work ...
    
    log_info "Completed in: $(timer_elapsed_formatted)"

Outputs: HH:MM:SS format


═════════════════════════════════════════════════════════════════════════════════
9. COMPLETE EXAMPLE
═════════════════════════════════════════════════════════════════════════════════

EOF

cat << 'EXAMPLE'
#!/bin/bash
# My Test Script - Runs comprehensive validation
# Usage: ./my-script.sh [--debug]

set -euo pipefail

# Load environment
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/.env.defaults"
source "$SCRIPT_DIR/lib/common.sh"
register_error_trap

# Parse arguments
DEBUG_MODE=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --debug)
            DEBUG_MODE=true
            LOG_LEVEL=DEBUG
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

main() {
    log_section "Starting My Test Script"
    timer_start
    
    # Validate prerequisites
    log_info "Checking prerequisites..."
    require_directory "$TEMP_DIR"
    require_directory "$STEEL_HAMMER_DIR"
    check_command "docker" || error_exit 1 "Docker is required"
    
    # Check services
    log_info "Checking services..."
    if ! check_service_health "$KEYCLOAK_URL/health" "Keycloak" 5 2; then
        log_warn "Keycloak may not be ready yet, continuing..."
    fi
    
    # Create temp files
    log_info "Preparing test files..."
    TEST_FILE="$TEMP_DIR/test-$RANDOM.txt"
    echo "Test content" > "$TEST_FILE"
    log_success "Created: $TEST_FILE"
    
    # Run tests
    log_section "Running Tests"
    if mvn clean test -q; then
        log_success "All tests passed!"
    else
        log_error "Tests failed!"
        exit 1
    fi
    
    # Summary
    log_section "Execution Complete"
    log_info "Total time: $(timer_elapsed_formatted)"
    log_success "Script completed successfully"
}

main "$@"
EXAMPLE

cat << 'EOF'

═════════════════════════════════════════════════════════════════════════════════
10. TROUBLESHOOTING
═════════════════════════════════════════════════════════════════════════════════

TEMP FILES NOT FOUND:
    → Check: ls -la "$TEMP_DIR"
    → Verify PROJECT_ROOT is set correctly
    → Don't use hardcoded /tmp paths

SERVICE HEALTH CHECKS FAILING:
    → Enable DEBUG: LOG_LEVEL=DEBUG ./script.sh
    → Check: is_container returns correct value
    → Verify service URLs: echo $KEYCLOAK_URL
    → If container: docker logs <service>
    → If host: docker ps + check localhost ports

SCRIPTS NOT SOURCING CORRECTLY:
    → Check SCRIPT_DIR calculation with: echo "$SCRIPT_DIR"
    → Verify .env.defaults and lib/common.sh exist
    → Check file permissions: chmod +x script.sh

═════════════════════════════════════════════════════════════════════════════════
11. QUICK REFERENCE - BEFORE/AFTER
═════════════════════════════════════════════════════════════════════════════════

BEFORE (OLD WAY):
    #!/bin/bash
    set -e
    PROJECT_ROOT="/workspaces/IronBucket"
    RED='\033[0;31m'
    KEYCLOAK_URL="https://localhost:7081"
    
    echo -e "${RED}Testing...${NC}"
    mkdir -p /tmp/tests
    echo "data" > /tmp/test.txt
    
    mvn test || exit 1
    echo "Done"

AFTER (NEW WAY):
    #!/bin/bash
    set -euo pipefail
    
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    source "$SCRIPT_DIR/.env.defaults"
    source "$SCRIPT_DIR/lib/common.sh"
    register_error_trap
    
    log_section "Running Tests"
    timer_start
    
    require_directory "$TEMP_DIR"
    mkdir -p "$TEMP_DIR/tests"
    echo "data" > "$TEMP_DIR/test.txt"
    
    mvn test || error_exit 1 "Maven failed"
    log_success "Tests passed in $(timer_elapsed_formatted)"

BENEFITS:
    ✅ Centralized environment
    ✅ Consistent logging
    ✅ Container-aware
    ✅ Better error handling
    ✅ Easier debugging
    ✅ Persistent temp files

═════════════════════════════════════════════════════════════════════════════════

For more details, see:
    • scripts/.env.defaults - Environment variables
    • scripts/lib/common.sh - Available functions
    • Any script in scripts/ - Real examples

EOF
