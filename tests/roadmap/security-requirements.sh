#!/usr/bin/env bash

################################################################################
# IronBucket Security Requirements Test Suite
#
# Purpose: Validate critical security features are implemented
# Status: RED - These tests MUST FAIL initially, then we implement fixes
# Priority: P0 CRITICAL - Must pass before production deployment
#
# Based on: docs/PRODUCTION-READINESS-ROADMAP.md
# Marathon Mindset: Tests define what "secure" means
################################################################################

set -uo pipefail  # Removed -e to continue after failures

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
CRITICAL_FAILURES=0

# Test results array
declare -a TEST_RESULTS

################################################################################
# Helper Functions
################################################################################

log_test_start() {
    local test_name="$1"
    echo -e "${BLUE}[TEST]${NC} $test_name"
    ((TOTAL_TESTS++))
}

log_test_pass() {
    local test_name="$1"
    echo -e "${GREEN}  ✓${NC} PASS: $test_name"
    ((PASSED_TESTS++))
    TEST_RESULTS+=("PASS: $test_name")
}

log_test_fail() {
    local test_name="$1"
    local severity="${2:-HIGH}"
    local message="$3"
    echo -e "${RED}  ✗${NC} FAIL: $test_name"
    echo -e "${RED}    Severity: $severity${NC}"
    echo -e "${RED}    Issue: $message${NC}"
    ((FAILED_TESTS++))
    
    if [[ "$severity" == "CRITICAL" ]]; then
        ((CRITICAL_FAILURES++))
    fi
    
    TEST_RESULTS+=("FAIL ($severity): $test_name - $message")
}

################################################################################
# P0 CRITICAL Security Tests
################################################################################

test_network_policies_deployed() {
    log_test_start "NetworkPolicies Deployed"
    
    # In Docker Compose, NetworkPolicies don't apply
    # This is a CRITICAL limitation for production
    log_test_fail "NetworkPolicies NOT applicable in Docker Compose" \
        "CRITICAL" \
        "Running in Docker Compose. NetworkPolicies only work in Kubernetes. For production, deploy to K8s with: kubectl apply -f docs/k8s-network-policies.yaml"
}

test_network_isolation_enforced() {
    log_test_start "Network Isolation Enforced"
    
    # In Docker Compose, check if MinIO is accessible directly
    if curl -s --max-time 2 https://localhost:9000 &> /dev/null; then
        log_test_fail "MinIO directly accessible on localhost:9000" \
            "CRITICAL" \
            "Direct MinIO access possible, bypassing security layers. Deploy NetworkPolicies in Kubernetes."
    elif docker ps 2>/dev/null | grep -q minio; then
        log_test_fail "MinIO accessible in Docker network (no isolation)" \
            "CRITICAL" \
            "Docker Compose does not provide network isolation. Deploy to Kubernetes with NetworkPolicies."
    else
        log_test_pass "MinIO not directly accessible from this context"
    fi
}

test_no_hardcoded_credentials() {
    log_test_start "No Hardcoded Credentials"
    
    # Search for hardcoded credentials in configuration files
    local hardcoded_found=false
    local files_with_credentials=()
    
    # Check docker-compose files
    if grep -r "minioadmin" steel-hammer/*.yml steel-hammer/*.yaml 2>/dev/null; then
        hardcoded_found=true
        files_with_credentials+=("docker-compose files")
    fi
    
    # Check Java application.yml files
    if find temp/ -name "application*.yml" -o -name "application*.yaml" | xargs grep -l "minioadmin" 2>/dev/null; then
        hardcoded_found=true
        files_with_credentials+=("Spring Boot config files")
    fi
    
    if [ "$hardcoded_found" = true ]; then
        log_test_fail "Hardcoded credentials found" \
            "CRITICAL" \
            "Default MinIO credentials (minioadmin) found in: ${files_with_credentials[*]}. Implement Vault integration."
    else
        log_test_pass "No hardcoded credentials found"
    fi
}

test_vault_integration_exists() {
    log_test_start "Vault Integration Implemented"
    
    # Check if Vault configuration exists in Java services
    local vault_config_found=false
    
    if find temp/ -name "pom.xml" | xargs grep -q "spring-cloud-vault" 2>/dev/null; then
        vault_config_found=true
    fi
    
    if [ "$vault_config_found" = true ]; then
        log_test_pass "Vault integration dependencies found"
    else
        log_test_fail "Vault integration NOT implemented" \
            "CRITICAL" \
            "Services do not have Vault dependencies. Add spring-cloud-vault-config to all service POMs."
    fi
}

test_tls_enabled_services() {
    log_test_start "TLS Enabled on All Services"
    
    # Check if services respond to HTTPS (not HTTP)
    local services=("sentinel-gear:8081" "brazz-nossel:8082" "claimspindel:8083" "buzzle-vane:8761")
    local http_services=()
    
    for service in "${services[@]}"; do
        if curl -s -k --max-time 2 "http://${service}/actuator/health" &> /dev/null; then
            http_services+=("$service")
        fi
    done
    
    if [ ${#http_services[@]} -gt 0 ]; then
        log_test_fail "TLS NOT enabled on all services" \
            "CRITICAL" \
            "Services accepting HTTP: ${http_services[*]}. Enable TLS for all inter-service communication."
    else
        log_test_pass "All services require TLS"
    fi
}

test_mtls_between_services() {
    log_test_start "mTLS Between Services"
    
    # Check if services require client certificates
    # This is a placeholder - actual implementation would check cert validation
    
    log_test_fail "mTLS NOT implemented" \
        "CRITICAL" \
        "Mutual TLS not configured between services. Implement cert-manager and configure mTLS."
}

################################################################################
# P1 HIGH Priority Security Tests
################################################################################

test_security_tests_use_gateway() {
    log_test_start "Security Tests Use Brazz-Nossel Gateway"
    
    # Check if E2E tests bypass security by accessing MinIO directly
    local bypass_tests=()
    
    if grep -r "minio:9000\|localhost:9000" steel-hammer/test*.sh 2>/dev/null; then
        bypass_tests+=("steel-hammer test scripts")
    fi
    
    if grep -r "minio:9000\|localhost:9000" steel-hammer/tests/ 2>/dev/null; then
        bypass_tests+=("steel-hammer/tests/")
    fi
    
    if [ ${#bypass_tests[@]} -gt 0 ]; then
        log_test_fail "Test scripts bypass Brazz-Nossel gateway" \
            "HIGH" \
            "Tests access MinIO directly: ${bypass_tests[*]}. Refactor to use brazz-nossel:8082 endpoint."
    else
        log_test_pass "All tests use Brazz-Nossel gateway"
    fi
}

test_jwt_validation_comprehensive() {
    log_test_start "Comprehensive JWT Validation Tests"
    
    # Check if JWT validation tests exist
    local jwt_test_file="temp/Sentinel-Gear/src/test/java/com/ironbucket/sentinelgear/identity/SentinelGearJWTValidationTest.java"
    
    if [ -f "$jwt_test_file" ]; then
        # Check test coverage
        local test_count=$(grep -c "@Test" "$jwt_test_file" || echo "0")
        
        if [ "$test_count" -ge 10 ]; then
            log_test_pass "JWT validation has $test_count tests"
        else
            log_test_fail "JWT validation tests insufficient" \
                "HIGH" \
                "Only $test_count JWT tests found. Need tests for: expired tokens, wrong signature, missing claims, tenant isolation."
        fi
    else
        log_test_fail "JWT validation tests NOT found" \
            "HIGH" \
            "Missing comprehensive JWT validation test suite"
    fi
}

test_audit_logging_complete() {
    log_test_start "Audit Logging Comprehensive"
    
    # Check if audit logging tests exist
    local audit_test_file="temp/Sentinel-Gear/src/test/java/com/ironbucket/sentinelgear/integration/SentinelGearAuditLoggingTest.java"
    
    if [ -f "$audit_test_file" ]; then
        log_test_pass "Audit logging tests exist"
    else
        log_test_fail "Audit logging tests NOT found" \
            "HIGH" \
            "Missing audit logging test coverage"
    fi
}

test_policy_enforcement_tests() {
    log_test_start "Policy Enforcement Tests Comprehensive"
    
    # Check if policy enforcement tests exist
    local policy_test_file="temp/Sentinel-Gear/src/test/java/com/ironbucket/sentinelgear/integration/SentinelGearPolicyEnforcementTest.java"
    
    if [ -f "$policy_test_file" ]; then
        local test_count=$(grep -c "@Test" "$policy_test_file" || echo "0")
        
        if [ "$test_count" -ge 5 ]; then
            log_test_pass "Policy enforcement has $test_count tests"
        else
            log_test_fail "Policy enforcement tests insufficient" \
                "HIGH" \
                "Only $test_count policy tests. Need: bucket policies, object ACLs, deny precedence, multi-tenant isolation."
        fi
    else
        log_test_fail "Policy enforcement tests NOT found" \
            "HIGH" \
            "Missing policy enforcement test coverage"
    fi
}

################################################################################
# P2 MEDIUM Priority Security Tests
################################################################################

test_secrets_not_in_logs() {
    log_test_start "Secrets Not Logged"
    
    # Check recent logs for leaked secrets
    # This is a placeholder - actual implementation would check log files
    
    log_test_fail "Secret logging validation NOT implemented" \
        "MEDIUM" \
        "Need automated tests to verify secrets are not logged"
}

test_security_headers_present() {
    log_test_start "Security Headers Present"
    
    # Check if security headers are set
    local service_url="https://sentinel-gear:8081"
    
    if command -v curl &> /dev/null; then
        local headers=$(curl -sI "$service_url/actuator/health" 2>/dev/null || echo "")
        
        local missing_headers=()
        if ! echo "$headers" | grep -qi "Strict-Transport-Security"; then
            missing_headers+=("HSTS")
        fi
        if ! echo "$headers" | grep -qi "X-Content-Type-Options"; then
            missing_headers+=("X-Content-Type-Options")
        fi
        if ! echo "$headers" | grep -qi "X-Frame-Options"; then
            missing_headers+=("X-Frame-Options")
        fi
        
        if [ ${#missing_headers[@]} -gt 0 ]; then
            log_test_fail "Security headers missing" \
                "MEDIUM" \
                "Missing headers: ${missing_headers[*]}"
        else
            log_test_pass "All security headers present"
        fi
    else
        log_test_fail "curl not available" \
            "MEDIUM" \
            "Cannot verify security headers"
    fi
}

test_dependency_vulnerabilities() {
    log_test_start "No Dependency Vulnerabilities"
    
    # Check if OWASP dependency check ran recently
    if [ -f "target/dependency-check-report.html" ]; then
        log_test_pass "Dependency vulnerability scan completed"
    else
        log_test_fail "Dependency vulnerability scan NOT run" \
            "MEDIUM" \
            "Run: mvn org.owasp:dependency-check-maven:check"
    fi
}

################################################################################
# Main Execution
################################################################################

main() {
    echo "════════════════════════════════════════════════════════════════"
    echo "  IronBucket Security Requirements Test Suite"
    echo "  Status: RED (Tests expected to FAIL initially)"
    echo "  Priority: P0 CRITICAL"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    
    # P0 CRITICAL Tests
    echo -e "${RED}═══ P0 CRITICAL Security Tests ═══${NC}"
    test_network_policies_deployed
    test_network_isolation_enforced
    test_no_hardcoded_credentials
    test_vault_integration_exists
    test_tls_enabled_services
    test_mtls_between_services
    echo ""
    
    # P1 HIGH Tests
    echo -e "${YELLOW}═══ P1 HIGH Priority Security Tests ═══${NC}"
    test_security_tests_use_gateway
    test_jwt_validation_comprehensive
    test_audit_logging_complete
    test_policy_enforcement_tests
    echo ""
    
    # P2 MEDIUM Tests
    echo -e "${BLUE}═══ P2 MEDIUM Priority Security Tests ═══${NC}"
    test_secrets_not_in_logs
    test_security_headers_present
    test_dependency_vulnerabilities
    echo ""
    
    # Summary
    echo "════════════════════════════════════════════════════════════════"
    echo "  Test Results Summary"
    echo "════════════════════════════════════════════════════════════════"
    echo "Total Tests:      $TOTAL_TESTS"
    echo -e "${GREEN}Passed:           $PASSED_TESTS${NC}"
    echo -e "${RED}Failed:           $FAILED_TESTS${NC}"
    echo -e "${RED}CRITICAL Issues:  $CRITICAL_FAILURES${NC}"
    echo ""
    
    # Calculate pass rate
    if [ $TOTAL_TESTS -gt 0 ]; then
        local pass_rate=$((100 * PASSED_TESTS / TOTAL_TESTS))
        echo "Pass Rate:        ${pass_rate}%"
    fi
    
    echo ""
    echo "════════════════════════════════════════════════════════════════"
    
    # Production readiness verdict
    if [ $CRITICAL_FAILURES -gt 0 ]; then
        echo -e "${RED}⚠️  PRODUCTION READINESS: BLOCKED${NC}"
        echo -e "${RED}   $CRITICAL_FAILURES CRITICAL security issues must be fixed${NC}"
        echo ""
        echo "Next Steps:"
        echo "  1. Deploy NetworkPolicies: kubectl apply -f docs/k8s-network-policies.yaml"
        echo "  2. Implement Vault integration for credentials"
        echo "  3. Enable TLS/mTLS for all services"
        echo "  4. Refactor test scripts to use Brazz-Nossel gateway"
        echo ""
        return 1
    elif [ $FAILED_TESTS -gt 0 ]; then
        echo -e "${YELLOW}⚠️  PRODUCTION READINESS: PARTIAL${NC}"
        echo -e "${YELLOW}   $FAILED_TESTS non-critical issues remain${NC}"
        return 0
    else
        echo -e "${GREEN}✅ PRODUCTION READINESS: SECURITY APPROVED${NC}"
        return 0
    fi
}

# Run main function
main
exit_code=$?

# Generate report for test reporter
if [ -d "test-results/reports" ]; then
    cat > "test-results/reports/security-requirements-$(date +%Y%m%d-%H%M%S).txt" << EOF
Security Requirements Test Report
Generated: $(date)

Total Tests: $TOTAL_TESTS
Passed: $PASSED_TESTS
Failed: $FAILED_TESTS
CRITICAL Failures: $CRITICAL_FAILURES

Detailed Results:
$(printf '%s\n' "${TEST_RESULTS[@]}")
EOF
fi

exit $exit_code
