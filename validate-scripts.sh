#!/bin/bash
################################################################################
# IRONBUCKET SCRIPT VALIDATION TOOL
# 
# Validates that all scripts follow the new standardization guidelines
# Run this to verify compliance before committing changes
################################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Counters
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
WARNINGS=0

print_header() {
    echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${MAGENTA}║${NC} $1"
    echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════════╝${NC}"
}

print_check() {
    local status="$1"
    local message="$2"
    
    if [[ "$status" == "PASS" ]]; then
        echo -e "${GREEN}✅${NC} $message"
        ((PASSED_CHECKS++))
    elif [[ "$status" == "FAIL" ]]; then
        echo -e "${RED}❌${NC} $message"
        ((FAILED_CHECKS++))
    else
        echo -e "${YELLOW}⚠️${NC} $message"
        ((WARNINGS++))
    fi
    ((TOTAL_CHECKS++))
}

check_file_exists() {
    local file="$1"
    if [[ -f "$file" ]]; then
        print_check "PASS" "File exists: $file"
    else
        print_check "FAIL" "File missing: $file"
    fi
}

check_file_content() {
    local file="$1"
    local pattern="$2"
    local description="$3"
    
    if grep -q "$pattern" "$file" 2>/dev/null; then
        print_check "PASS" "$description in $(basename $file)"
    else
        print_check "FAIL" "$description NOT FOUND in $(basename $file)"
    fi
}

check_no_pattern() {
    local file="$1"
    local pattern="$2"
    local description="$3"
    
    if ! grep -q "$pattern" "$file" 2>/dev/null; then
        print_check "PASS" "No $description in $(basename $file)"
    else
        print_check "WARN" "Found $description in $(basename $file)"
    fi
}

main() {
    print_header "IRONBUCKET SCRIPT VALIDATION v1.0"
    echo ""
    
    # ============================================================================
    # CHECK 1: Required Files Exist
    # ============================================================================
    
    echo -e "${BLUE}═══ CHECK 1: Required Foundation Files ===${NC}"
    check_file_exists "$PROJECT_ROOT/scripts/.env.defaults"
    check_file_exists "$PROJECT_ROOT/scripts/lib/common.sh"
    check_file_exists "$PROJECT_ROOT/SCRIPT_STANDARDIZATION.md"
    check_file_exists "$PROJECT_ROOT/SCRIPT_MIGRATION_CHECKLIST.md"
    check_file_exists "$PROJECT_ROOT/REFACTORING_IMPLEMENTATION_SUMMARY.md"
    echo ""
    
    # ============================================================================
    # CHECK 2: .env.defaults Content
    # ============================================================================
    
    echo -e "${BLUE}═══ CHECK 2: .env.defaults Configuration ===${NC}"
    check_file_content "$PROJECT_ROOT/scripts/.env.defaults" "PROJECT_ROOT" "PROJECT_ROOT variable"
    check_file_content "$PROJECT_ROOT/scripts/.env.defaults" "TEMP_DIR" "TEMP_DIR variable"
    check_file_content "$PROJECT_ROOT/scripts/.env.defaults" "LOG_DIR" "LOG_DIR variable"
    check_file_content "$PROJECT_ROOT/scripts/.env.defaults" "KEYCLOAK_URL" "KEYCLOAK_URL variable"
    check_file_content "$PROJECT_ROOT/scripts/.env.defaults" "IS_CONTAINER" "IS_CONTAINER detection"
    check_file_content "$PROJECT_ROOT/scripts/.env.defaults" "export" "export statements"
    echo ""
    
    # ============================================================================
    # CHECK 3: lib/common.sh Functions
    # ============================================================================
    
    echo -e "${BLUE}═══ CHECK 3: lib/common.sh Functions ===${NC}"
    check_file_content "$PROJECT_ROOT/scripts/lib/common.sh" "log_info\|log_success\|log_error" "Logging functions"
    check_file_content "$PROJECT_ROOT/scripts/lib/common.sh" "error_exit" "error_exit function"
    check_file_content "$PROJECT_ROOT/scripts/lib/common.sh" "check_service_health" "check_service_health function"
    check_file_content "$PROJECT_ROOT/scripts/lib/common.sh" "timer_start\|timer_elapsed" "Timing functions"
    check_file_content "$PROJECT_ROOT/scripts/lib/common.sh" "export -f" "Function exports"
    echo ""
    
    # ============================================================================
    # CHECK 4: Migrated Scripts - Header
    # ============================================================================
    
    echo -e "${BLUE}═══ CHECK 4: Migrated Scripts - Header ===${NC}"
    
    local migrated_scripts=(
        "scripts/spinup.sh"
        "scripts/run-all-tests-complete.sh"
        "scripts/comprehensive-test-reporter.sh"
        "scripts/e2e/e2e-alice-bob-test.sh"
    )
    
    for script in "${migrated_scripts[@]}"; do
        if [[ -f "$PROJECT_ROOT/$script" ]]; then
            if grep -q "source.*\.env.defaults" "$PROJECT_ROOT/$script" 2>/dev/null; then
                print_check "PASS" "$script sources .env.defaults"
            else
                print_check "FAIL" "$script does NOT source .env.defaults"
            fi
            
            if grep -q "source.*lib/common.sh" "$PROJECT_ROOT/$script" 2>/dev/null; then
                print_check "PASS" "$script sources lib/common.sh"
            else
                print_check "FAIL" "$script does NOT source lib/common.sh"
            fi
        fi
    done
    echo ""
    
    # ============================================================================
    # CHECK 5: Hardcoded Paths (Anti-Pattern)
    # ============================================================================
    
    echo -e "${BLUE}═══ CHECK 5: Hardcoded Paths Detection ===${NC}"
    
    local hardcoded_errors=0
    
    # Check for hardcoded /workspaces
    if grep -r "/workspaces/IronBucket" scripts/*.sh 2>/dev/null | grep -v ".env.defaults\|lib/common.sh" > /dev/null; then
        print_check "WARN" "Found hardcoded /workspaces in scripts (should use \$PROJECT_ROOT)"
        ((hardcoded_errors++))
    else
        print_check "PASS" "No hardcoded /workspaces paths in scripts"
    fi
    
    # Check for hardcoded /tmp usage in script files
    if grep -r "mkdir.*-p.*/tmp\|> /tmp\|--data-binary @/tmp" scripts/*.sh 2>/dev/null | grep -v ".env.defaults" > /dev/null; then
        print_check "WARN" "Found /tmp paths in scripts (should use \$TEMP_DIR)"
        ((hardcoded_errors++))
    else
        print_check "PASS" "No /tmp paths in scripts"
    fi
    
    # Check for hardcoded localhost without container awareness
    if grep -r "localhost:708\|localhost:900" scripts/*.sh 2>/dev/null | grep -v ".env.defaults\|lib/common.sh" > /dev/null; then
        print_check "WARN" "Found hardcoded localhost URLs (should use environment variables)"
        ((hardcoded_errors++))
    else
        print_check "PASS" "No hardcoded service URLs in scripts"
    fi
    
    echo ""
    
    # ============================================================================
    # CHECK 6: Logging Functions (Anti-Pattern)
    # ============================================================================
    
    echo -e "${BLUE}═══ CHECK 6: Logging Pattern Compliance ===${NC}"
    
    # Check spinup.sh for direct echo usage (should be rare)
    local echo_count=$(grep -c "^[[:space:]]*echo " "$PROJECT_ROOT/scripts/spinup.sh" 2>/dev/null || echo "0")
    if [[ "$echo_count" -lt 20 ]]; then
        print_check "PASS" "spinup.sh uses mostly logging functions (echo count: $echo_count)"
    else
        print_check "WARN" "spinup.sh uses many direct echo commands ($echo_count)"
    fi
    
    # Check for color code duplication
    if grep -c "RED='\\\\033" scripts/*.sh 2>/dev/null || echo "0" > /dev/null; then
        print_check "WARN" "Found color code definitions in scripts (should come from common.sh)"
    else
        print_check "PASS" "Color codes sourced from common.sh, not duplicated"
    fi
    
    echo ""
    
    # ============================================================================
    # CHECK 7: Error Handling
    # ============================================================================
    
    echo -e "${BLUE}═══ CHECK 7: Error Handling ===${NC}"
    
    if grep -q "set -euo pipefail" "$PROJECT_ROOT/scripts/spinup.sh" 2>/dev/null; then
        print_check "PASS" "spinup.sh uses set -euo pipefail"
    else
        print_check "WARN" "spinup.sh does not use set -euo pipefail"
    fi
    
    if grep -q "register_error_trap" "$PROJECT_ROOT/scripts/spinup.sh" 2>/dev/null; then
        print_check "PASS" "spinup.sh registers error trap"
    else
        print_check "WARN" "spinup.sh does not register error trap"
    fi
    
    echo ""
    
    # ============================================================================
    # CHECK 8: Python Scripts
    # ============================================================================
    
    echo -e "${BLUE}═══ CHECK 8: Python Script Updates ===${NC}"
    
    if grep -q "os.environ" "$PROJECT_ROOT/scripts/verify-test-pathway.py" 2>/dev/null; then
        print_check "PASS" "verify-test-pathway.py uses environment variables"
    else
        print_check "FAIL" "verify-test-pathway.py does not use environment variables"
    fi
    
    if grep -q "sys.exit" "$PROJECT_ROOT/scripts/verify-test-pathway.py" 2>/dev/null; then
        print_check "PASS" "verify-test-pathway.py has proper exit codes"
    else
        print_check "FAIL" "verify-test-pathway.py missing exit code handling"
    fi
    
    echo ""
    
    # ============================================================================
    # CHECK 9: Container Awareness
    # ============================================================================
    
    echo -e "${BLUE}═══ CHECK 9: Container Awareness ===${NC}"
    
    if grep -q "IS_CONTAINER\|is_container" "$PROJECT_ROOT/scripts/lib/common.sh" 2>/dev/null; then
        print_check "PASS" "Container detection implemented in common.sh"
    else
        print_check "FAIL" "Container detection missing from common.sh"
    fi
    
    if grep -q "get_service_url" "$PROJECT_ROOT/scripts/lib/common.sh" 2>/dev/null; then
        print_check "PASS" "Service URL helper implemented in common.sh"
    else
        print_check "FAIL" "Service URL helper missing from common.sh"
    fi
    
    echo ""
    
    # ============================================================================
    # SUMMARY
    # ============================================================================
    
    print_header "VALIDATION SUMMARY"
    echo ""
    echo -e "Total Checks:     ${BLUE}${TOTAL_CHECKS}${NC}"
    echo -e "Passed:           ${GREEN}${PASSED_CHECKS}${NC}"
    echo -e "Failed:           ${RED}${FAILED_CHECKS}${NC}"
    echo -e "Warnings:         ${YELLOW}${WARNINGS}${NC}"
    echo ""
    
    if [[ $FAILED_CHECKS -eq 0 ]]; then
        echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
        echo -e "${GREEN}✅ ALL CRITICAL CHECKS PASSED! Script standardization is in place.${NC}"
        echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
        
        if [[ $WARNINGS -gt 0 ]]; then
            echo ""
            echo -e "${YELLOW}⚠️  $WARNINGS warnings found - consider reviewing${NC}"
        fi
        
        return 0
    else
        echo -e "${RED}════════════════════════════════════════════════════════════════${NC}"
        echo -e "${RED}❌ VALIDATION FAILED! $FAILED_CHECKS critical issues must be fixed.${NC}"
        echo -e "${RED}════════════════════════════════════════════════════════════════${NC}"
        return 1
    fi
}

main "$@"
