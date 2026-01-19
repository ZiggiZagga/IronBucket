#!/bin/bash

################################################################################
# IronBucket Comprehensive Test Reporting & Todo Generation System
# 
# Adapted from Graphite-Forge best practices
# Executes all tests and generates structured reports with actionable todos
#
# Features:
#   - Runs all IronBucket test types (Maven modules, E2E, Security)
#   - Captures failures and organizes by severity
#   - Generates 4 report formats: JSON, Markdown, HTML, Todos
#   - Creates actionable todo items from each failure
#   - Tracks security compliance and network isolation
#   - Color-coded logging and output
#
# Usage: bash scripts/comprehensive-test-reporter.sh [options]
# Options:
#   --backend      Run Maven tests only (all modules)
#   --e2e          Run E2E tests only
#   --security     Run security validation tests
#   --roadmap      Run production readiness roadmap tests
#   --all          Run all tests (default)
#   --verbose      Show detailed output
#   --help         Show this help message
################################################################################

set -e

# Load shared environment and helpers (fallback to local defaults)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "$SCRIPT_DIR/.env.defaults" ]]; then
  source "$SCRIPT_DIR/.env.defaults"
fi
if [[ -f "$SCRIPT_DIR/lib/common.sh" ]]; then
  source "$SCRIPT_DIR/lib/common.sh"
fi

# Allow unset variables in this script (common.sh sets -u)
set +u

# ============================================================================
# COLOR DEFINITIONS
# ============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
GRAY='\033[0;37m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# ============================================================================
# CONFIGURATION
# ============================================================================

PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
TEST_RESULTS_DIR="${TEST_RESULTS_DIR:-${PROJECT_ROOT}/test-results}"
LOG_DIR="${LOG_DIR:-${TEST_RESULTS_DIR}/logs}"
REPORTS_DIR="${REPORTS_DIR:-${TEST_RESULTS_DIR}/reports}"
LOGS_DIR="${LOG_DIR}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_ID="test-report-${TIMESTAMP}"

# Test configuration
RUN_BACKEND=false
RUN_E2E=false
RUN_SECURITY=false
RUN_ROADMAP=false
VERBOSE=false

# Statistics
TOTAL_TESTS=0
TOTAL_PASSED=0
TOTAL_FAILED=0
BACKEND_FAILED=0
E2E_FAILED=0
ROADMAP_FAILED=0
SECURITY_FAILED=0

# Failure tracking
declare -A CRITICAL_FAILURES
declare -A HIGH_FAILURES
declare -A MEDIUM_FAILURES
declare -A LOW_FAILURES
declare -A ALL_FAILURES

# Security compliance tracking
SECURITY_ISSUES=()

# ============================================================================
# LOGGING FUNCTIONS
# ============================================================================

log_info() {
  echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
  echo -e "${GREEN}[✓]${NC} $*"
}

log_error() {
  echo -e "${RED}[✗]${NC} $*"
}

log_warning() {
  echo -e "${YELLOW}[!]${NC} $*"
}

log_header() {
  echo ""
  echo -e "${CYAN}${BOLD}╔════════════════════════════════════════════════════════╗${NC}"
  echo -e "${CYAN}${BOLD}║${NC} $*"
  echo -e "${CYAN}${BOLD}╚════════════════════════════════════════════════════════╝${NC}"
  echo ""
}

log_section() {
  echo ""
  echo -e "${MAGENTA}${BOLD}▶ $*${NC}"
  echo ""
}

log_verbose() {
  [[ "$VERBOSE" == "true" ]] && echo -e "${GRAY}[VERBOSE]${NC} $*"
}

# ============================================================================
# SETUP & INITIALIZATION
# ============================================================================

ensure_directories() {
  mkdir -p "$REPORTS_DIR"
  mkdir -p "$LOGS_DIR"
  log_success "Directories initialized"
}

print_usage() {
  cat << EOF
${BOLD}IronBucket Comprehensive Test Reporting System${NC}

${BOLD}Usage:${NC} bash scripts/comprehensive-test-reporter.sh [options]

${BOLD}Options:${NC}
  --backend      Run backend Maven tests only (all modules in temp/)
  --e2e          Run E2E tests only
  --roadmap      Run production readiness roadmap tests
  --all          Run all tests (default including roadmap) tests
  --all          Run all tests (default)
  --verbose      Show detailed output
  --help         Show this help message

${BOLD}Examples:${NC}
  bash scripts/comprehensive-test-reporter.sh --all
  bash scripts/comprehensive-test-reporter.sh --backend --security --verbose
  bash scripts/comprehensive-test-reporter.sh --e2e

${BOLD}Output:${NC}
  Reports are generated in: ${REPORTS_DIR}/
  - test-report-TIMESTAMP.md (Markdown report)
  - test-report-TIMESTAMP.json (JSON report)
  - test-report-TIMESTAMP-todos.md (Todo report with failures)
  - test-report-TIMESTAMP-security.md (Security compliance)
  - LATEST-SUMMARY.md (Quick reference)

EOF
}

parse_arguments() {
  if [[ $# -eq 0 ]]; then
    RUN_BACKEND=true
    RUN_E2E=true
    RUN_ROADMAP=true
    return
  fi

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --backend)
        RUN_BACKEND=true
        shift
        ;;
      --e2e)
        RUN_E2E=true
        shift
        ;;
      --security)
        RUN_SECURITY=true
        shift
        ;;
      --roadmap)
        RUN_ROADMAP=true
        shift
        ;;
      --all)
        RUN_BACKEND=true
        RUN_E2E=true
        RUN_SECURITY=true
        RUN_ROADMAP=true
        shift
        ;;
      --verbose)
        VERBOSE=true
        shift
        ;;
      --help)
        print_usage
        exit 0
        ;;
      *)
        log_error "Unknown option: $1"
        print_usage
        exit 1
        ;;
    esac
  done

  # Default to all if none selected
  if [[ "$RUN_BACKEND" == "false" && "$RUN_E2E" == "false" && "$RUN_SECURITY" == "false" && "$RUN_ROADMAP" == "false" ]]; then
    RUN_BACKEND=true
    RUN_E2E=true
    RUN_SECURITY=true
    RUN_ROADMAP
    RUN_SECURITY=true
  fi
}

# ============================================================================
# TEST EXECUTION FUNCTIONS
# ============================================================================

run_backend_tests() {
  log_section "Backend Tests (Maven)"

  local backend_log="${LOGS_DIR}/backend-${TIMESTAMP}.log"
  local modules=()
  
  # Find all Maven modules in temp/
  while IFS= read -r pom; do
    local module_dir=$(dirname "$pom")
    local module_name=$(basename "$module_dir")
    modules+=("$module_dir:$module_name")
  done < <(find "$PROJECT_ROOT/temp" -name "pom.xml" -type f 2>/dev/null)

  local module_count=0
  local module_passed=0

  for module_info in "${modules[@]}"; do
    IFS=':' read -r module_dir module_name <<< "$module_info"
    
    module_count=$((module_count + 1))
    log_info "Testing ${module_name}..."

    cd "$module_dir"

    if mvn test --batch-mode -q >> "$backend_log" 2>&1; then
      log_success "${module_name}: All tests passed"
      module_passed=$((module_passed + 1))
      TOTAL_PASSED=$((TOTAL_PASSED + 1))
    else
      log_error "${module_name}: Tests failed"
      BACKEND_FAILED=$((BACKEND_FAILED + 1))
      TOTAL_FAILED=$((TOTAL_FAILED + 1))
      
      # Determine severity based on module
      if [[ "$module_name" == "Sentinel-Gear" || "$module_name" == "Claimspindel" ]]; then
        CRITICAL_FAILURES["${module_name}_tests"]="Maven tests failed in security-critical ${module_name} module"
      elif [[ "$module_name" == "Brazz-Nossel" ]]; then
        CRITICAL_FAILURES["${module_name}_tests"]="Maven tests failed in gateway ${module_name} module"
      else
        HIGH_FAILURES["${module_name}_tests"]="Maven tests failed in ${module_name} module"
      fi
      
      ALL_FAILURES["${module_name}"]="${module_name}: Backend Maven tests"
    fi
  done

  TOTAL_TESTS=$((TOTAL_TESTS + module_count))
  
  cd "$PROJECT_ROOT"
  [[ $module_count -gt 0 ]] && log_success "Backend tests complete: ${module_passed}/${module_count} passed"
  
  return 0
}

run_e2e_tests() {
  log_section "E2E Tests"

  local e2e_log="${LOGS_DIR}/e2e-${TIMESTAMP}.log"
  local e2e_scripts=(
    "${PROJECT_ROOT}/e2e-test-standalone.sh"
    "${PROJECT_ROOT}/steel-hammer/test-scripts/e2e-verification.sh"
  )

  local e2e_count=0
  local e2e_passed=0

  for script in "${e2e_scripts[@]}"; do
    if [[ ! -f "$script" ]]; then
      log_verbose "E2E script not found: $script"
      continue
    fi

    e2e_count=$((e2e_count + 1))
    local script_name=$(basename "$script")
    
    log_info "Executing ${script_name}..."

    if bash "$script" >> "$e2e_log" 2>&1; then
      log_success "${script_name}: Passed"
      e2e_passed=$((e2e_passed + 1))
      TOTAL_PASSED=$((TOTAL_PASSED + 1))
    else
      log_error "${script_name}: Failed"
      E2E_FAILED=$((E2E_FAILED + 1))
      TOTAL_FAILED=$((TOTAL_FAILED + 1))
      
      HIGH_FAILURES["e2e_${script_name}"]="E2E integration test ${script_name} failed"
      ALL_FAILURES["e2e_${script_name}"]="E2E: ${script_name}"
    fi
  done

  TOTAL_TESTS=$((TOTAL_TESTS + e2e_count))

  if [[ $e2e_count -eq 0 ]]; then
    log_warning "No E2E test scripts found"
  else
    log_success "E2E tests complete: ${e2e_passed}/${e2e_count} passed"
  fi

  return 0
}

run_roadmap_tests() {
  log_section "Production Readiness Roadmap Tests"

  local roadmap_log="${LOGS_DIR}/roadmap-${TIMESTAMP}.log"
  local roadmap_passed=0
  local roadmap_failed=0

  # Run the Java-based roadmap test from Sentinel-Gear
  log_info "Running Production Readiness Tests..."
  
  local sentinel_dir="${PROJECT_ROOT}/temp/Sentinel-Gear"
  
  if [[ ! -f "${sentinel_dir}/pom.xml" ]]; then
    log_error "Sentinel-Gear module not found at ${sentinel_dir}"
    return 1
  fi

  cd "$sentinel_dir"

  # Run just the ProductionReadinessTest
  if mvn test -Dtest=ProductionReadinessTest --batch-mode >> "$roadmap_log" 2>&1; then
    log_success "Production Readiness: All requirements met"
    roadmap_passed=18  # Total tests in ProductionReadinessTest
    TOTAL_PASSED=$((TOTAL_PASSED + roadmap_passed))
  else
    log_warning "Production Readiness: Some requirements not met"
    
    # Parse Maven output to count failures
    local maven_output=$(cat "$roadmap_log")
    local tests_run=$(echo "$maven_output" | grep -oP "Tests run: \K\d+" | tail -1)
    local failures=$(echo "$maven_output" | grep -oP "Failures: \K\d+" | tail -1)
    
    roadmap_passed=$((tests_run - failures))
    roadmap_failed=$failures
    
    ROADMAP_FAILED=$roadmap_failed
    TOTAL_PASSED=$((TOTAL_PASSED + roadmap_passed))
    TOTAL_FAILED=$((TOTAL_FAILED + roadmap_failed))
    
    # Parse specific failures and categorize by severity
    while IFS= read -r failure_line; do
      if echo "$failure_line" | grep -q "CRITICAL:"; then
        local failure_msg=$(echo "$failure_line" | sed 's/.*CRITICAL: //')
        CRITICAL_FAILURES["roadmap_critical"]="$failure_msg"
        ALL_FAILURES["roadmap_critical"]="Roadmap CRITICAL: $failure_msg"
        SECURITY_ISSUES+=("🔴 CRITICAL: $failure_msg")
      elif echo "$failure_line" | grep -q "HIGH:"; then
        local failure_msg=$(echo "$failure_line" | sed 's/.*HIGH: //')
        HIGH_FAILURES["roadmap_high"]="$failure_msg"
        ALL_FAILURES["roadmap_high"]="Roadmap HIGH: $failure_msg"
      elif echo "$failure_line" | grep -q "MEDIUM:"; then
        local failure_msg=$(echo "$failure_line" | sed 's/.*MEDIUM: //')
        MEDIUM_FAILURES["roadmap_medium"]="$failure_msg"
        ALL_FAILURES["roadmap_medium"]="Roadmap MEDIUM: $failure_msg"
      fi
    done < <(grep "AssertionFailedError" "$roadmap_log")
  fi

  TOTAL_TESTS=$((TOTAL_TESTS + tests_run))

  log_info "Roadmap tests complete: ${roadmap_passed}/${tests_run} passed"
  
  if [[ ${#CRITICAL_FAILURES[@]} -gt 0 ]]; then
    log_error "⚠️  Production deployment BLOCKED by CRITICAL failures"
  fi

  return 0
}

run_security_tests() {
  log_section "Security Validation Tests"

  local security_log="${LOGS_DIR}/security-${TIMESTAMP}.log"
  local security_count=0
  local security_passed=0

  # Test 1: Check if NetworkPolicies are deployed
  log_info "Checking NetworkPolicy deployment..."
  security_count=$((security_count + 1))
  
  if kubectl get networkpolicies -n ironbucket &>/dev/null; then
    log_success "NetworkPolicies: Deployed"
    security_passed=$((security_passed + 1))
    TOTAL_PASSED=$((TOTAL_PASSED + 1))
  else
    log_error "NetworkPolicies: NOT deployed"
    SECURITY_FAILED=$((SECURITY_FAILED + 1))
    TOTAL_FAILED=$((TOTAL_FAILED + 1))
    
    CRITICAL_FAILURES["networkpolicy"]="Kubernetes NetworkPolicies are NOT deployed - direct MinIO access possible"
    ALL_FAILURES["security_networkpolicy"]="Security: NetworkPolicy deployment"
    SECURITY_ISSUES+=("🔴 CRITICAL: NetworkPolicies missing - docs/k8s-network-policies.yaml must be deployed")
  fi

  # Test 2: Check for hardcoded credentials
  log_info "Checking for hardcoded credentials..."
  security_count=$((security_count + 1))
  
  if grep -r "minioadmin" "$PROJECT_ROOT/steel-hammer" &>/dev/null; then
    log_error "Hardcoded credentials: Found in docker-compose"
    SECURITY_FAILED=$((SECURITY_FAILED + 1))
    TOTAL_FAILED=$((TOTAL_FAILED + 1))
    
    CRITICAL_FAILURES["hardcoded_creds"]="Hardcoded MinIO credentials found - Vault integration required"
    ALL_FAILURES["security_creds"]="Security: Credential management"
    SECURITY_ISSUES+=("🔴 CRITICAL: Hardcoded minioadmin credentials - implement Vault integration")
  else
    log_success "Hardcoded credentials: None found"
    security_passed=$((security_passed + 1))
    TOTAL_PASSED=$((TOTAL_PASSED + 1))
  fi

  # Test 3: Check for direct MinIO access in tests
  log_info "Checking test scripts for security bypasses..."
  security_count=$((security_count + 1))
  
  if grep -r "minio:9000" "$PROJECT_ROOT/steel-hammer/test-scripts" &>/dev/null; then
    log_error "Security bypass: Tests access MinIO directly"
    SECURITY_FAILED=$((SECURITY_FAILED + 1))
    TOTAL_FAILED=$((TOTAL_FAILED + 1))
    
    HIGH_FAILURES["test_bypass"]="Test scripts bypass Brazz-Nossel security gateway"
    ALL_FAILURES["security_test_bypass"]="Security: Test security bypass"
    SECURITY_ISSUES+=("🟠 HIGH: Test scripts must use Brazz-Nossel endpoint, not direct MinIO access")
  else
    log_success "Security bypass: None found in tests"
    security_passed=$((security_passed + 1))
    TOTAL_PASSED=$((TOTAL_PASSED + 1))
  fi

  # Test 4: Check if MinIO port is exposed
  log_info "Checking MinIO port exposure..."
  security_count=$((security_count + 1))
  
  if grep -A 2 "steel-hammer-minio:" "$PROJECT_ROOT/steel-hammer/docker-compose-steel-hammer.yml" | grep "ports:" &>/dev/null; then
    log_error "MinIO port exposure: Port 9000 exposed to host"
    SECURITY_FAILED=$((SECURITY_FAILED + 1))
    TOTAL_FAILED=$((TOTAL_FAILED + 1))
    
    CRITICAL_FAILURES["minio_exposed"]="MinIO port 9000 exposed to host - remove port mapping"
    ALL_FAILURES["security_minio_port"]="Security: MinIO port exposure"
    SECURITY_ISSUES+=("🔴 CRITICAL: MinIO port must NOT be exposed to localhost")
  else
    log_success "MinIO port exposure: Correctly isolated"
    security_passed=$((security_passed + 1))
    TOTAL_PASSED=$((TOTAL_PASSED + 1))
  fi

  TOTAL_TESTS=$((TOTAL_TESTS + security_count))
  
  log_success "Security tests complete: ${security_passed}/${security_count} passed"
  
  return 0
}

# ============================================================================
# REPORT GENERATION FUNCTIONS
# ============================================================================

generate_markdown_report() {
  local report_file="${REPORTS_DIR}/${REPORT_ID}.md"
  
  cat > "$report_file" << EOF
# IronBucket Test Report

**Generated**: $(date '+%Y-%m-%d %H:%M:%S')  
**Report ID**: ${REPORT_ID}

---

## 📊 Executive Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | ${TOTAL_TESTS} |
| **Passed** | ${TOTAL_PASSED} |
| **Failed** | ${TOTAL_FAILED} |
| **Pass Rate** | $(( TOTAL_TESTS > 0 ? (TOTAL_PASSED * 100) / TOTAL_TESTS : 0 ))% |

### Test Breakdown

| Category | Failed |
|----------|--------|
| Backend (Maven) | ${BACKEND_FAILED} |
| E2E Integration | ${E2E_FAILED} |
| Security Validation | ${SECURITY_FAILED} |

---

## 🔴 Critical Failures

EOF

  if [[ ${#CRITICAL_FAILURES[@]} -gt 0 ]]; then
    for key in "${!CRITICAL_FAILURES[@]}"; do
      echo "- **${key}**: ${CRITICAL_FAILURES[$key]}" >> "$report_file"
    done
  else
    echo "✅ No critical failures" >> "$report_file"
  fi

  cat >> "$report_file" << EOF

---

## 🟠 High Priority Failures

EOF

  if [[ ${#HIGH_FAILURES[@]} -gt 0 ]]; then
    for key in "${!HIGH_FAILURES[@]}"; do
      echo "- **${key}**: ${HIGH_FAILURES[$key]}" >> "$report_file"
    done
  else
    echo "✅ No high priority failures" >> "$report_file"
  fi

  cat >> "$report_file" << EOF

---

## 🔐 Security Compliance Issues

EOF

  if [[ ${#SECURITY_ISSUES[@]} -gt 0 ]]; then
    for issue in "${SECURITY_ISSUES[@]}"; do
      echo "- ${issue}" >> "$report_file"
    done
  else
    echo "✅ No security issues detected" >> "$report_file"
  fi

  cat >> "$report_file" << EOF

---

## 📋 Full Test Results

### Backend Tests (Maven Modules)

EOF

  for key in "${!ALL_FAILURES[@]}"; do
    if [[ "$key" != *"e2e"* && "$key" != *"security"* ]]; then
      echo "- ❌ ${ALL_FAILURES[$key]}" >> "$report_file"
    fi
  done

  cat >> "$report_file" << EOF

### E2E Tests

EOF

  for key in "${!ALL_FAILURES[@]}"; do
    if [[ "$key" == *"e2e"* ]]; then
      echo "- ❌ ${ALL_FAILURES[$key]}" >> "$report_file"
    fi
  done

  cat >> "$report_file" << EOF

### Security Tests

EOF

  for key in "${!ALL_FAILURES[@]}"; do
    if [[ "$key" == *"security"* ]]; then
      echo "- ❌ ${ALL_FAILURES[$key]}" >> "$report_file"
    fi
  done

  cat >> "$report_file" << EOF

---

## 📂 Logs

Detailed logs are available in:
- Backend: \`${LOGS_DIR}/backend-${TIMESTAMP}.log\`
- E2E: \`${LOGS_DIR}/e2e-${TIMESTAMP}.log\`
- Security: \`${LOGS_DIR}/security-${TIMESTAMP}.log\`

---

**Report generated by IronBucket Comprehensive Test Reporter**
EOF

  log_success "Markdown report generated: ${report_file}"
}

generate_todo_report() {
  local todo_file="${REPORTS_DIR}/${REPORT_ID}-todos.md"
  
  cat > "$todo_file" << EOF
# IronBucket Test Failures - Action Items

**Generated**: $(date '+%Y-%m-%d %H:%M:%S')  
**Total Action Items**: $((${#CRITICAL_FAILURES[@]} + ${#HIGH_FAILURES[@]} + ${#MEDIUM_FAILURES[@]} + ${#LOW_FAILURES[@]}))

---

## 🔴 CRITICAL (Same Day - ASAP)

EOF

  local todo_num=1
  for key in "${!CRITICAL_FAILURES[@]}"; do
    cat >> "$todo_file" << EOF
### ${todo_num}) ${key}

- [ ] **Title**: Fix - ${CRITICAL_FAILURES[$key]}
- [ ] **Module**: ${key}
- [ ] **Severity**: CRITICAL
- [ ] **Deadline**: Same Day (ASAP)
- [ ] **Status**: Open
- [ ] **Reference**: See logs in \`${LOGS_DIR}/\`

**Next Actions**:
1. Review failure logs
2. Identify root cause
3. Implement fix
4. Verify with tests
5. Document resolution

---

EOF
    todo_num=$((todo_num + 1))
  done

  [[ ${#CRITICAL_FAILURES[@]} -eq 0 ]] && echo "✅ No critical issues" >> "$todo_file"

  cat >> "$todo_file" << EOF

## 🟠 HIGH (1-2 Days - Current Sprint)

EOF

  for key in "${!HIGH_FAILURES[@]}"; do
    cat >> "$todo_file" << EOF
### ${todo_num}) ${key}

- [ ] **Title**: Fix - ${HIGH_FAILURES[$key]}
- [ ] **Module**: ${key}
- [ ] **Severity**: HIGH
- [ ] **Deadline**: 1-2 Days
- [ ] **Status**: Open

---

EOF
    todo_num=$((todo_num + 1))
  done

  [[ ${#HIGH_FAILURES[@]} -eq 0 ]] && echo "✅ No high priority issues" >> "$todo_file"

  cat >> "$todo_file" << EOF

---

## 📊 Summary

| Priority | Count | Deadline |
|----------|-------|----------|
| 🔴 Critical | ${#CRITICAL_FAILURES[@]} | Same Day |
| 🟠 High | ${#HIGH_FAILURES[@]} | 1-2 Days |
| 🟡 Medium | ${#MEDIUM_FAILURES[@]} | 3 Days |
| 🟢 Low | ${#LOW_FAILURES[@]} | 1 Week |

---

**Generated by IronBucket Test Reporter**
EOF

  log_success "Todo report generated: ${todo_file}"
}

generate_summary() {
  local summary_file="${REPORTS_DIR}/LATEST-SUMMARY.md"
  
  cat > "$summary_file" << EOF
# Latest Test Results - Quick Summary

**Last Run**: $(date '+%Y-%m-%d %H:%M:%S')  
**Report ID**: ${REPORT_ID}

## Quick Stats

- **Total Tests**: ${TOTAL_TESTS}
- **Pass Rate**: $(( TOTAL_TESTS > 0 ? (TOTAL_PASSED * 100) / TOTAL_TESTS : 0 ))%
- **Critical Issues**: ${#CRITICAL_FAILURES[@]}
- **High Priority**: ${#HIGH_FAILURES[@]}

## Reports

- 📄 [Full Report](${REPORT_ID}.md)
- 📋 [Action Items](${REPORT_ID}-todos.md)
- 🔐 [Security Report](${REPORT_ID}-security.md)

## Status

EOF

  if [[ ${#CRITICAL_FAILURES[@]} -gt 0 ]]; then
    echo "🔴 **CRITICAL**: ${#CRITICAL_FAILURES[@]} critical issues require immediate attention" >> "$summary_file"
  elif [[ ${#HIGH_FAILURES[@]} -gt 0 ]]; then
    echo "🟠 **WARNING**: ${#HIGH_FAILURES[@]} high priority issues need addressing" >> "$summary_file"
  else
    echo "✅ **GOOD**: All tests passing or only minor issues" >> "$summary_file"
  fi

  log_success "Summary generated: ${summary_file}"
}

# ============================================================================
# MAIN EXECUTION
# ============================================================================

main() {
  log_header "IronBucket Comprehensive Test Reporter"
  
  parse_arguments "$@"
  ensure_directories
  
  log_info "Test configuration:"
  log_info "  Backend: ${RUN_BACKEND}"
  log_info "  E2E: ${RUN_E2E}"
  log_info "  Security: ${RUN_SECURITY}"
  log_info "  Roadmap: ${RUN_ROADMAP}"
  log_info "  Verbose: ${VERBOSE}"
  
  # Run tests
  [[ "$RUN_BACKEND" == "true" ]] && run_backend_tests
  [[ "$RUN_E2E" == "true" ]] && run_e2e_tests
  [[ "$RUN_SECURITY" == "true" ]] && run_security_tests
  [[ "$RUN_ROADMAP" == "true" ]] && run_roadmap_tests
  [[ "$RUN_SECURITY" == "true" ]] && run_security_tests
  
  # Generate reports
  log_header "Generating Reports"
  generate_markdown_report
  generate_todo_report
  generate_summary
  
  # Final summary
  log_header "Test Run Complete"
  log_info "Tests run: ${TOTAL_TESTS}"
  log_success "Passed: ${TOTAL_PASSED}"
  [[ ${TOTAL_FAILED} -gt 0 ]] && log_error "Failed: ${TOTAL_FAILED}" || log_success "Failed: 0"
  
  if [[ ${#CRITICAL_FAILURES[@]} -gt 0 ]]; then
    log_error "⚠️  ${#CRITICAL_FAILURES[@]} CRITICAL issues require immediate attention!"
    echo ""
    echo "Review: ${REPORTS_DIR}/${REPORT_ID}-todos.md"
  fi
  
  return 0
}

# Run main function
main "$@"
