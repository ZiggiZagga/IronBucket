#!/bin/bash
################################################################################
# IronBucket Common Utilities Library
# 
# Provides unified logging, error handling, and utility functions for all scripts
# 
# Usage: source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
#
# This should be sourced AFTER .env.defaults
################################################################################

set -euo pipefail

# ============================================================================
# ENSURE ENV IS LOADED
# ============================================================================

if [[ -z "${PROJECT_ROOT:-}" ]]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
    source "$SCRIPT_DIR/.env.defaults"
fi

# =========================================================================
# COLOR / PRINT HELPERS (shared so scripts stop re-defining)
# =========================================================================

# These expect color variables from .env.defaults; define safe fallbacks
: "${RED:=}"
: "${GREEN:=}"
: "${YELLOW:=}"
: "${BLUE:=}"
: "${MAGENTA:=}"
: "${CYAN:=}"
: "${GRAY:=}"
: "${BOLD:=}"
: "${NC:=}"

print_header() {
    local msg="$1"
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}${msg}${BLUE}║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_section() {
    local msg="$1"
    echo ""
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${MAGENTA}  ${msg}${NC}"
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

print_step() {
    echo -e "${YELLOW}▶ $*${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $*${NC}"
}

print_error() {
    echo -e "${RED}❌ $*${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $*${NC}"
}

# ============================================================================
# LOGGING FUNCTIONS
# ============================================================================

# Ensure LOG_FILE is writable
if [[ ! -d "$(dirname "$LOG_FILE")" ]]; then
    mkdir -p "$(dirname "$LOG_FILE")"
fi

# Log to both console and file
log_output() {
    local level="$1"
    shift
    local message="$@"
    local formatted="${BOLD}[${level}]${NC} $(date '+%Y-%m-%d %H:%M:%S') ${message}"
    
    echo -e "$formatted" | tee -a "$LOG_FILE"
}

# Info level
log_info() {
    [[ "$LOG_LEVEL" == "DEBUG" ]] || [[ "$LOG_LEVEL" == "INFO" ]] && \
    log_output "${BLUE}INFO${NC}" "$@" || true
}

# Debug level
log_debug() {
    [[ "$LOG_LEVEL" == "DEBUG" ]] && \
    log_output "${CYAN}DEBUG${NC}" "$@" || true
}

# Success level
log_success() {
    log_output "${GREEN}✅ SUCCESS${NC}" "$@"
}

# Warning level
log_warn() {
    log_output "${YELLOW}⚠️  WARN${NC}" "$@"
}

# Error level
log_error() {
    log_output "${RED}❌ ERROR${NC}" "$@" >&2
}

# Section header
log_section() {
    echo "" | tee -a "$LOG_FILE"
    echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════════╗${NC}" | tee -a "$LOG_FILE"
    echo -e "${MAGENTA}║${NC} $1" | tee -a "$LOG_FILE"
    echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════════╝${NC}" | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
}

# ============================================================================
# ERROR HANDLING
# ============================================================================

# Exit on error with message
error_exit() {
    local exit_code=${1:-1}
    shift
    local message="$@"
    
    log_error "$message"
    log_error "Exiting with code: $exit_code"
    exit "$exit_code"
}

# Trap errors
trap_error() {
    local line_number=$1
    local bash_lineno=$2
    local last_exit_code=$3
    
    log_error "Command failed at line $line_number (exit code: $last_exit_code)"
    log_error "Backtrace: ${BASH_SOURCE[*]}"
}

# Register error trap (call in main script if needed)
register_error_trap() {
    trap 'trap_error ${LINENO} ${BASH_LINENO} $?' ERR
}

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

# Check if command exists
check_command() {
    if command -v "$1" &> /dev/null; then
        log_debug "Command found: $1"
        return 0
    else
        log_warn "Command not found: $1"
        return 1
    fi
}

# Check if running in container
is_container() {
    [[ "$IS_CONTAINER" == "true" ]]
}

# Check if file exists
check_file() {
    if [[ -f "$1" ]]; then
        log_debug "File exists: $1"
        return 0
    else
        log_warn "File not found: $1"
        return 1
    fi
}

# Check if directory exists
check_directory() {
    if [[ -d "$1" ]]; then
        log_debug "Directory exists: $1"
        return 0
    else
        log_warn "Directory not found: $1"
        return 1
    fi
}

# Ensure directory exists or exit
require_directory() {
    if ! check_directory "$1"; then
        error_exit 1 "Required directory not found: $1"
    fi
}

# Ensure file exists or exit
require_file() {
    if ! check_file "$1"; then
        error_exit 1 "Required file not found: $1"
    fi
}

# Ensure generated certificate artifacts exist. If missing, generate them.
ensure_cert_artifacts() {
    local certs_dir="${CERTS_DIR:-${PROJECT_ROOT}/certs}"
    local generator_script="${certs_dir}/generate-certificates.sh"
    local is_container="${IS_CONTAINER:-false}"
    local required_files=(
        "ca/ca.crt"
        "ca/ca-truststore.p12"
        "services/infrastructure/keycloak/tls.crt"
        "services/infrastructure/keycloak/tls.key"
        "services/infrastructure/minio/tls.crt"
        "services/infrastructure/minio/tls.key"
        "services/infrastructure/vault/tls.crt"
        "services/infrastructure/vault/tls.key"
    )
    local missing_files=()
    local rel_path

    # In containerized E2E runs, /certs is the canonical shared mount.
    if [[ "$is_container" == "true" && -d "/certs" ]]; then
        certs_dir="/certs"
        generator_script="${certs_dir}/generate-certificates.sh"
    fi

    if [[ ! -d "${certs_dir}" && -d "/certs" ]]; then
        certs_dir="/certs"
        generator_script="${certs_dir}/generate-certificates.sh"
    fi

    for rel_path in "${required_files[@]}"; do
        if [[ ! -f "${certs_dir}/${rel_path}" ]]; then
            missing_files+=("${rel_path}")
        fi
    done

    if [[ ${#missing_files[@]} -eq 0 ]]; then
        log_info "Certificate artifacts already present"
        return 0
    fi

    if [[ "$certs_dir" == "/certs" && ! -w "$certs_dir" ]]; then
        error_exit 1 "Certificate artifacts are missing in /certs and this mount is read-only. Generate certificates on the host first (for example via scripts/e2e/prove-phase1-3-complete.sh preflight)."
    fi

    log_warn "Missing certificate artifacts detected; generating certificates"
    if [[ ! -f "${generator_script}" ]]; then
        error_exit 1 "Certificate generator script not found: ${generator_script}"
    fi

    (cd "${certs_dir}" && bash "./generate-certificates.sh") || \
        error_exit 1 "Certificate generation failed"

    for rel_path in "${required_files[@]}"; do
        if [[ ! -f "${certs_dir}/${rel_path}" ]]; then
            error_exit 1 "Certificate generation incomplete. Missing: ${certs_dir}/${rel_path}"
        fi
    done

    log_success "Certificate artifacts generated successfully"
}

# Check service availability
check_service_health() {
    local url="$1"
    local service_name="${2:-Service}"
    local max_retries="${3:-3}"
    local retry_delay="${4:-2}"
    
    local attempt=0
    
    while [[ $attempt -lt $max_retries ]]; do
        if curl -s -f -m 5 "$url" > /dev/null 2>&1; then
            log_success "$service_name is healthy: $url"
            return 0
        fi
        
        attempt=$((attempt + 1))
        if [[ $attempt -lt $max_retries ]]; then
            log_warn "$service_name not responding (attempt $attempt/$max_retries), retrying in ${retry_delay}s..."
            sleep "$retry_delay"
        fi
    done
    
    log_error "$service_name is not responding: $url"
    return 1
}

# Wait for an HTTP endpoint with retries and timeout
wait_for_service() {
    local url="$1"
    local name="${2:-Service}"
    local timeout="${3:-120}"
    local interval="${4:-5}"

    local waited=0
    while [[ $waited -lt $timeout ]]; do
        if curl -s -f -m 5 "$url" >/dev/null 2>&1; then
            log_success "$name is ready after ${waited}s"
            return 0
        fi
        echo -n "."
        sleep "$interval"
        waited=$((waited + interval))
    done

    log_error "$name not ready after ${timeout}s"
    return 1
}

# Docker prerequisite checks
require_docker() {
    if [[ "${SKIP_DOCKER_CHECK:-false}" == "true" ]]; then
        log_warn "Skipping docker prerequisite checks"
        return 0
    fi

    local ok=true
    if ! check_command docker; then
        ok=false
    fi
    if ! check_command docker-compose; then
        ok=false
    fi
    if ! docker ps >/dev/null 2>&1; then
        log_error "Docker daemon not running"
        ok=false
    fi

    [[ "$ok" == true ]] && log_success "Docker prerequisites satisfied" || error_exit 1 "Docker prerequisites missing"
}

# ============================================================================
# TEMPORARY FILE MANAGEMENT
# ============================================================================

# Create temp file in project temp dir (not /tmp)
create_temp_file() {
    local prefix="${1:-temp}"
    local temp_file="$TEMP_DIR/${prefix}_$$.tmp"
    
    touch "$temp_file"
    log_debug "Created temp file: $temp_file"
    echo "$temp_file"
}

# Create temp directory in project temp dir
create_temp_dir() {
    local prefix="${1:-tempdir}"
    local temp_dir="$TEMP_DIR/${prefix}_$$"
    
    mkdir -p "$temp_dir"
    log_debug "Created temp directory: $temp_dir"
    echo "$temp_dir"
}

# ============================================================================
# CONTAINER/HOST AWARENESS
# ============================================================================

# Get correct URL based on container/host context
get_service_url() {
    local service="$1"
    
    case "$service" in
        keycloak)
            echo "$KEYCLOAK_URL"
            ;;
        minio)
            echo "$MINIO_URL"
            ;;
        postgres)
            echo "$POSTGRES_HOST"
            ;;
        sentinel-gear)
            echo "$SENTINEL_GEAR_URL"
            ;;
        brazz-nossel)
            echo "$BRAZZ_NOSSEL_URL"
            ;;
        buzzle-vane)
            echo "$BUZZLE_VANE_URL"
            ;;
        *)
            error_exit 1 "Unknown service: $service"
            ;;
    esac
}

# ============================================================================
# VALIDATION FUNCTIONS
# ============================================================================

# Validate prerequisites
validate_prerequisites() {
    local all_valid=true
    
    log_section "Validating Prerequisites"
    
    # Check required commands
    local required_commands=("docker" "docker-compose" "mvn" "curl" "jq")
    for cmd in "${required_commands[@]}"; do
        if ! check_command "$cmd"; then
            all_valid=false
        fi
    done
    
    # Check required directories
    if ! check_directory "$STEEL_HAMMER_DIR"; then
        all_valid=false
    fi
    
    if [[ "$all_valid" == false ]]; then
        error_exit 1 "Prerequisites validation failed"
    fi
    
    log_success "All prerequisites valid"
    return 0
}

# =========================================================================
# MAVEN HELPERS
# =========================================================================

# Run one or more Maven modules (paths relative to PROJECT_ROOT)
# Populates globals: MAVEN_TOTAL_TESTS, MAVEN_TOTAL_PASSED, MAVEN_TOTAL_FAILED,
# MAVEN_FOUND_COUNT, MAVEN_EXPECTED_COUNT, MAVEN_SUMMARY (array)
run_maven_modules() {
    local fail_fast=false
    if [[ "${1:-}" == "--fail-fast" ]]; then
        fail_fast=true
        shift
    fi

    declare -g MAVEN_TOTAL_TESTS=0
    declare -g MAVEN_TOTAL_PASSED=0
    declare -g MAVEN_TOTAL_FAILED=0
    declare -g MAVEN_FOUND_COUNT=0
    declare -g MAVEN_EXPECTED_COUNT=$#
    declare -ag MAVEN_SUMMARY=()

    if [[ $# -eq 0 ]]; then
        log_warn "run_maven_modules called with no modules"
        return 0
    fi

    pushd "$PROJECT_ROOT" >/dev/null
    for module in "$@"; do
        if [[ ! -d "$module" ]]; then
            log_warn "Module not found: $module"
            continue
        fi
        MAVEN_FOUND_COUNT=$((MAVEN_FOUND_COUNT + 1))

        local safe_name=${module//\//-}
        local maven_log="$LOG_DIR/maven-${safe_name}-${TIMESTAMP_SHORT}.log"

        print_step "Testing ${module}"
        set +e  # Disable exit on error for Maven builds
        
        # Run Maven directly without timeout wrapper (timeout + bash -c was causing deadlock)
        # Maven will handle its own process lifecycle
        (cd "$module" && mvn clean test 2>&1) > "$maven_log" 2>&1
        local mvn_exit=$?
        
        # Append to main log file
        cat "$maven_log" >> "$LOG_FILE" 2>/dev/null || true
        
        if [ $mvn_exit -eq 0 ]; then
            local build_summary
            build_summary=$(tail -20 "$maven_log")
            local test_count
            test_count=$(echo "$build_summary" | grep -oP 'Tests run: \K[0-9]+' | tail -1)
            local fail_count
            fail_count=$(echo "$build_summary" | grep -oP 'Failures: \K[0-9]+' | tail -1)
            test_count=${test_count:-0}
            fail_count=${fail_count:-0}

            MAVEN_TOTAL_TESTS=$((MAVEN_TOTAL_TESTS + test_count))
            MAVEN_TOTAL_PASSED=$((MAVEN_TOTAL_PASSED + test_count - fail_count))
            MAVEN_TOTAL_FAILED=$((MAVEN_TOTAL_FAILED + fail_count))

            if [[ $fail_count -gt 0 ]]; then
                MAVEN_SUMMARY+=("❌ ${module}: ${test_count} tests, ${fail_count} failed")
                log_error "${module}: ${test_count} tests, ${fail_count} failed"
            else
                MAVEN_SUMMARY+=("✅ ${module}: ${test_count} tests")
                log_success "${module}: ${test_count} tests passed"
            fi
        else
            local fail_count
            fail_count=$(tail -20 "$maven_log" | grep -oP 'Failures: \K[0-9]+' | tail -1)
            fail_count=${fail_count:-1}
            MAVEN_TOTAL_FAILED=$((MAVEN_TOTAL_FAILED + fail_count))
            MAVEN_SUMMARY+=("❌ ${module}: build failed")
            log_error "${module}: build failed"
        fi
    done
    popd >/dev/null

    if [[ "$fail_fast" == true && $MAVEN_TOTAL_FAILED -gt 0 ]]; then
        return 1
    fi

    return 0
}

# Resolve a module path by searching one or more base directories in priority order.
# Usage: resolve_module_path "Sentinel-Gear" "services" "temp" "tools"
# Returns relative path (e.g., services/Sentinel-Gear) on success.
resolve_module_path() {
    local module_name="$1"
    shift

    local base_dir
    for base_dir in "$@"; do
        local candidate="$base_dir/$module_name"
        if [[ -f "$PROJECT_ROOT/$candidate/pom.xml" ]]; then
            echo "$candidate"
            return 0
        fi
    done

    return 1
}

# Build the canonical Maven module list using stable priority rules.
# Service modules prefer: services/ -> temp/ -> tools/
# Tool modules prefer: tools/ -> temp/ -> services/
# Returns one module path per line.
get_default_maven_modules() {
    local -a modules=()
    local -a missing_modules=()
    local -a service_modules=(
        "Sentinel-Gear"
        "Brazz-Nossel"
        "Claimspindel"
        "Buzzle-Vane"
        "Pactum-Scroll"
        "jclouds-adapter-core"
    )
    local -a tool_modules=(
        "Storage-Conductor"
        "Vault-Smith"
        "graphite-admin-shell"
    )

    local module_name
    for module_name in "${service_modules[@]}"; do
        local resolved_path
        resolved_path="$(resolve_module_path "$module_name" "services" "temp" "tools" || true)"
        if [[ -n "$resolved_path" ]]; then
            modules+=("$resolved_path")
        else
            missing_modules+=("Service module not found: $module_name (searched: services/, temp/, tools/)")
        fi
    done

    for module_name in "${tool_modules[@]}"; do
        local resolved_path
        resolved_path="$(resolve_module_path "$module_name" "tools" "temp" "services" || true)"
        if [[ -n "$resolved_path" ]]; then
            modules+=("$resolved_path")
        else
            missing_modules+=("Tool module not found: $module_name (searched: tools/, temp/, services/)")
        fi
    done

    if [[ ${#missing_modules[@]} -gt 0 ]]; then
        local missing_message
        for missing_message in "${missing_modules[@]}"; do
            log_warn "$missing_message" >&2
        done
    fi

    if [[ ${#modules[@]} -eq 0 ]]; then
        return 0
    fi

    printf '%s\n' "${modules[@]}"
}

# ============================================================================
# TIMING UTILITIES
# ============================================================================

# Start timer
timer_start() {
    TIMER_START=$(date +%s)
    log_debug "Timer started"
}

# Get elapsed time in seconds
timer_elapsed() {
    if [[ -z "${TIMER_START:-}" ]]; then
        echo "0"
    else
        local current=$(date +%s)
        echo $((current - TIMER_START))
    fi
}

# Get formatted elapsed time
timer_elapsed_formatted() {
    local seconds=$(timer_elapsed)
    local hours=$((seconds / 3600))
    local minutes=$(((seconds % 3600) / 60))
    local secs=$((seconds % 60))
    
    printf "%02d:%02d:%02d" $hours $minutes $secs
}

# ============================================================================
# SUMMARY/REPORT FUNCTIONS
# ============================================================================

# Print summary table
print_summary_table() {
    local title="$1"
    shift
    local -n rows=$1
    
    echo ""
    echo -e "${BOLD}${title}${NC}"
    echo "─────────────────────────────────────────────"
    
    for row in "${rows[@]}"; do
        echo "$row"
    done
    
    echo ""
}

# ============================================================================
# EXPORT FUNCTIONS
# ============================================================================

export -f log_info log_debug log_success log_warn log_error log_section
export -f error_exit register_error_trap
export -f check_command check_file check_directory
export -f require_directory require_file
export -f check_service_health wait_for_service require_docker
export -f create_temp_file create_temp_dir
export -f is_container get_service_url
export -f validate_prerequisites run_maven_modules
export -f resolve_module_path get_default_maven_modules
export -f timer_start timer_elapsed timer_elapsed_formatted
export -f print_summary_table
