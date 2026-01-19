#!/usr/bin/env bash

################################################################################
# IronBucket Integration Requirements Test Suite
#
# Purpose: Validate end-to-end integration flows work correctly
# Status: RED - These tests MUST FAIL initially for missing features
# Priority: P1 HIGH - Critical for production operations
#
# Based on: E2E-COMPLETE.md, alice-bob test scenarios
# Marathon Mindset: Tests define complete user workflows
################################################################################

set -euo pipefail

# Load shared env/common if available
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
if [[ -f "$ROOT_DIR/scripts/.env.defaults" ]]; then
    source "$ROOT_DIR/scripts/.env.defaults"
fi
if [[ -f "$ROOT_DIR/scripts/lib/common.sh" ]]; then
    source "$ROOT_DIR/scripts/lib/common.sh"
fi

LOG_DIR="${LOG_DIR:-${ROOT_DIR}/test-results/logs}"
mkdir -p "$LOG_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
HIGH_FAILURES=0

declare -a TEST_RESULTS

################################################################################
# Helper Functions
################################################################################

log_test_start() {
    echo -e "${BLUE}[TEST]${NC} $1"
    ((TOTAL_TESTS++))
}

log_test_pass() {
    echo -e "${GREEN}  ✓${NC} PASS: $1"
    ((PASSED_TESTS++))
    TEST_RESULTS+=("PASS: $1")
}

log_test_fail() {
    local test_name="$1"
    local severity="${2:-MEDIUM}"
    local message="$3"
    echo -e "${RED}  ✗${NC} FAIL: $test_name"
    echo -e "${RED}    Severity: $severity${NC}"
    echo -e "${RED}    Issue: $message${NC}"
    ((FAILED_TESTS++))
    
    if [[ "$severity" == "HIGH" ]]; then
        ((HIGH_FAILURES++))
    fi
    
    TEST_RESULTS+=("FAIL ($severity): $test_name - $message")
}

################################################################################
# P1 HIGH Priority E2E Integration Tests
################################################################################

test_alice_bob_e2e_complete() {
    log_test_start "Alice & Bob E2E Scenario Complete"
    
    # Check if comprehensive E2E test exists and passes
    if [ -f "steel-hammer/tests/e2e-alice-bob-container.sh" ]; then
        if bash steel-hammer/tests/e2e-alice-bob-container.sh &> "$LOG_DIR/alice-bob-test.log"; then
            log_test_pass "Alice & Bob E2E test passes"
        else
            log_test_fail "Alice & Bob E2E test FAILS" \
                "HIGH" \
                "E2E workflow has failures. Check $LOG_DIR/alice-bob-test.log for details."
        fi
    else
        log_test_fail "Alice & Bob E2E test NOT found" \
            "HIGH" \
            "Missing comprehensive E2E test: steel-hammer/tests/e2e-alice-bob-container.sh"
    fi
}

test_multi_tenant_isolation() {
    log_test_start "Multi-Tenant Isolation Enforced"
    
    # Test that Alice cannot access Bob's buckets
    # This test MUST exist to validate tenant isolation
    
    if [ -f "steel-hammer/tests/test-tenant-isolation.sh" ]; then
        if bash steel-hammer/tests/test-tenant-isolation.sh &> "$LOG_DIR/tenant-isolation-test.log"; then
            log_test_pass "Tenant isolation properly enforced"
        else
            log_test_fail "Tenant isolation test FAILS" \
                "HIGH" \
                "CRITICAL: Alice may be able to access Bob's data!"
        fi
    else
        log_test_fail "Tenant isolation test NOT found" \
            "HIGH" \
            "Create test: steel-hammer/tests/test-tenant-isolation.sh"
    fi
}

test_policy_enforcement_e2e() {
    log_test_start "Policy Enforcement End-to-End"
    
    # Test that policies are enforced across the entire flow:
    # Keycloak -> Sentinel-Gear -> Claimspindel -> Brazz-Nossel -> MinIO
    
    if [ -f "steel-hammer/tests/test-policy-enforcement-e2e.sh" ]; then
        if bash steel-hammer/tests/test-policy-enforcement-e2e.sh &> "$LOG_DIR/policy-e2e-test.log"; then
            log_test_pass "Policy enforcement works end-to-end"
        else
            log_test_fail "Policy enforcement E2E test FAILS" \
                "HIGH" \
                "Policies may not be enforced in complete flow"
        fi
    else
        log_test_fail "Policy enforcement E2E test NOT found" \
            "HIGH" \
            "Create test: steel-hammer/tests/test-policy-enforcement-e2e.sh"
    fi
}

test_audit_trail_complete() {
    log_test_start "Complete Audit Trail Generated"
    
    # Test that ALL operations are audited:
    # - Authentication events
    # - Policy decisions (allow/deny)
    # - S3 operations (PUT, GET, DELETE)
    # - User context (tenant, user, timestamp)
    
    if [ -f "steel-hammer/tests/test-audit-trail-e2e.sh" ]; then
        if bash steel-hammer/tests/test-audit-trail-e2e.sh &> "$LOG_DIR/audit-trail-test.log"; then
            log_test_pass "Complete audit trail generated"
        else
            log_test_fail "Audit trail incomplete" \
                "HIGH" \
                "Some operations may not be audited"
        fi
    else
        log_test_fail "Audit trail E2E test NOT found" \
            "HIGH" \
            "Create test: steel-hammer/tests/test-audit-trail-e2e.sh to validate: auth, policy, S3 ops are all logged"
    fi
}

test_service_discovery_integration() {
    log_test_start "Service Discovery Integration Works"
    
    # Test that services can discover each other via Buzzle-Vane (Eureka)
    # and that discovery updates when services restart
    
    if [ -f "temp/Buzzle-Vane/src/test/java/com/ironbucket/buzzlevane/DiscoveryServiceTests.java" ]; then
        cd temp/Buzzle-Vane
        if mvn test -Dtest=DiscoveryServiceTests -q &> "$LOG_DIR/discovery-test.log"; then
            log_test_pass "Service discovery integration works"
        else
            log_test_fail "Service discovery integration FAILS" \
                "HIGH" \
                "Services may not be able to find each other"
        fi
        cd /workspaces/IronBucket
    else
        log_test_fail "Service discovery integration test NOT found" \
            "HIGH" \
            "Missing test: temp/Buzzle-Vane/src/test/java/.../DiscoveryServiceTests.java"
    fi
}

################################################################################
# P2 MEDIUM Priority Integration Tests
################################################################################

test_jwt_propagation_e2e() {
    log_test_start "JWT Propagation Across All Services"
    
    # Test that JWT is correctly propagated:
    # Client -> Sentinel-Gear -> Claimspindel -> Brazz-Nossel -> MinIO (as identity)
    
    if [ -f "temp/Sentinel-Gear/src/test/java/com/ironbucket/sentinelgear/integration/SentinelGearIdentityPropagationTest.java" ]; then
        cd temp/Sentinel-Gear
        if mvn test -Dtest=SentinelGearIdentityPropagationTest -q &> "$LOG_DIR/jwt-propagation-test.log"; then
            log_test_pass "JWT propagation works E2E"
        else
            log_test_fail "JWT propagation E2E FAILS" \
                "MEDIUM" \
                "JWT may not be correctly propagated through all services"
        fi
        cd /workspaces/IronBucket
    else
        log_test_fail "JWT propagation E2E test NOT found" \
            "MEDIUM" \
            "Verify test exists: SentinelGearIdentityPropagationTest.java"
    fi
}

test_error_handling_e2e() {
    log_test_start "Error Handling Consistent Across Services"
    
    # Test that errors are handled consistently:
    # - 401 Unauthorized for auth failures
    # - 403 Forbidden for policy denials
    # - 404 Not Found for missing objects
    # - 500 Internal Error with proper logging
    
    if [ -f "steel-hammer/tests/test-error-handling-e2e.sh" ]; then
        if bash steel-hammer/tests/test-error-handling-e2e.sh &> "$LOG_DIR/error-handling-test.log"; then
            log_test_pass "Error handling consistent E2E"
        else
            log_test_fail "Error handling E2E inconsistent" \
                "MEDIUM" \
                "Error responses may not be consistent across services"
        fi
    else
        log_test_fail "Error handling E2E test NOT found" \
            "MEDIUM" \
            "Create test: steel-hammer/tests/test-error-handling-e2e.sh to validate: 401, 403, 404, 500 responses"
    fi
}

test_rate_limiting_e2e() {
    log_test_start "Rate Limiting Enforced End-to-End"
    
    # Test that rate limits are enforced per tenant
    if [ -f "steel-hammer/tests/test-rate-limiting-e2e.sh" ]; then
        if bash steel-hammer/tests/test-rate-limiting-e2e.sh &> "$LOG_DIR/rate-limiting-test.log"; then
            log_test_pass "Rate limiting works E2E"
        else
            log_test_fail "Rate limiting NOT enforced" \
                "MEDIUM" \
                "Rate limits may not be working properly"
        fi
    else
        log_test_fail "Rate limiting E2E test NOT found" \
            "MEDIUM" \
            "Create test: steel-hammer/tests/test-rate-limiting-e2e.sh to validate per-tenant rate limits"
    fi
}

test_data_consistency_e2e() {
    log_test_start "Data Consistency Across Services"
    
    # Test that data is consistent:
    # - PostgreSQL metadata matches MinIO objects
    # - Audit logs match actual operations
    # - Policy cache is updated when policies change
    
    if [ -f "steel-hammer/tests/test-data-consistency-e2e.sh" ]; then
        if bash steel-hammer/tests/test-data-consistency-e2e.sh &> "$LOG_DIR/data-consistency-test.log"; then
            log_test_pass "Data consistency maintained E2E"
        else
            log_test_fail "Data consistency issues detected" \
                "MEDIUM" \
                "Data may be inconsistent between services"
        fi
    else
        log_test_fail "Data consistency E2E test NOT found" \
            "MEDIUM" \
            "Create test: steel-hammer/tests/test-data-consistency-e2e.sh to validate: DB vs MinIO vs audit logs"
    fi
}

################################################################################
# P2 MEDIUM Priority Performance Tests
################################################################################

test_latency_targets_met() {
    log_test_start "Latency Targets Met (p99 < 500ms)"
    
    # Test that latency is acceptable under normal load
    if [ -f "steel-hammer/tests/test-latency-targets.sh" ]; then
        if bash steel-hammer/tests/test-latency-targets.sh &> "$LOG_DIR/latency-test.log"; then
            log_test_pass "Latency targets met (p99 < 500ms)"
        else
            log_test_fail "Latency targets NOT met" \
                "MEDIUM" \
                "p99 latency exceeds 500ms target"
        fi
    else
        log_test_fail "Latency target test NOT found" \
            "MEDIUM" \
            "Create test: steel-hammer/tests/test-latency-targets.sh to measure p50, p95, p99 latency"
    fi
}

test_throughput_targets_met() {
    log_test_start "Throughput Targets Met (1000 req/s)"
    
    # Test that system can handle target throughput
    if [ -f "steel-hammer/tests/test-throughput-targets.sh" ]; then
        if bash steel-hammer/tests/test-throughput-targets.sh &> "$LOG_DIR/throughput-test.log"; then
            log_test_pass "Throughput targets met (≥1000 req/s)"
        else
            log_test_fail "Throughput targets NOT met" \
                "MEDIUM" \
                "System cannot handle 1000 req/s"
        fi
    else
        log_test_fail "Throughput target test NOT found" \
            "MEDIUM" \
            "Create test: steel-hammer/tests/test-throughput-targets.sh using hey or ab tool"
    fi
}

################################################################################
# P3 LOW Priority Resilience Tests
################################################################################

test_service_restart_resilience() {
    log_test_start "System Resilient to Service Restarts"
    
    # Test that system handles service restarts gracefully
    if [ -f "steel-hammer/tests/test-service-restart-resilience.sh" ]; then
        if bash steel-hammer/tests/test-service-restart-resilience.sh &> "$LOG_DIR/restart-resilience-test.log"; then
            log_test_pass "System handles service restarts gracefully"
        else
            log_test_fail "Service restart causes issues" \
                "LOW" \
                "System may have problems during service restarts"
        fi
    else
        log_test_fail "Service restart resilience test NOT found" \
            "LOW" \
            "Create test: steel-hammer/tests/test-service-restart-resilience.sh"
    fi
}

test_database_connection_resilience() {
    log_test_start "System Resilient to DB Connection Issues"
    
    # Test that system handles database connection issues
    if [ -f "steel-hammer/tests/test-db-resilience.sh" ]; then
        if bash steel-hammer/tests/test-db-resilience.sh &> "$LOG_DIR/db-resilience-test.log"; then
            log_test_pass "System handles DB connection issues"
        else
            log_test_fail "DB connection issues cause failures" \
                "LOW" \
                "System may crash during DB issues"
        fi
    else
        log_test_fail "DB resilience test NOT found" \
            "LOW" \
            "Create test: steel-hammer/tests/test-db-resilience.sh to test connection pool exhaustion, timeouts"
    fi
}

################################################################################
# Main Execution
################################################################################

main() {
    echo "════════════════════════════════════════════════════════════════"
    echo "  IronBucket Integration Requirements Test Suite"
    echo "  Status: RED (Tests expected to FAIL for missing features)"
    echo "  Priority: P1 HIGH"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    
    # P1 HIGH Tests
    echo -e "${YELLOW}═══ P1 HIGH Priority Integration Tests ═══${NC}"
    test_alice_bob_e2e_complete
    test_multi_tenant_isolation
    test_policy_enforcement_e2e
    test_audit_trail_complete
    test_service_discovery_integration
    echo ""
    
    # P2 MEDIUM Tests
    echo -e "${BLUE}═══ P2 MEDIUM Priority Integration Tests ═══${NC}"
    test_jwt_propagation_e2e
    test_error_handling_e2e
    test_rate_limiting_e2e
    test_data_consistency_e2e
    echo ""
    
    # P2 MEDIUM Performance Tests
    echo -e "${BLUE}═══ P2 MEDIUM Priority Performance Tests ═══${NC}"
    test_latency_targets_met
    test_throughput_targets_met
    echo ""
    
    # P3 LOW Resilience Tests
    echo -e "${BLUE}═══ P3 LOW Priority Resilience Tests ═══${NC}"
    test_service_restart_resilience
    test_database_connection_resilience
    echo ""
    
    # Summary
    echo "════════════════════════════════════════════════════════════════"
    echo "  Test Results Summary"
    echo "════════════════════════════════════════════════════════════════"
    echo "Total Tests:      $TOTAL_TESTS"
    echo -e "${GREEN}Passed:           $PASSED_TESTS${NC}"
    echo -e "${RED}Failed:           $FAILED_TESTS${NC}"
    echo -e "${YELLOW}HIGH Issues:      $HIGH_FAILURES${NC}"
    echo ""
    
    if [ $TOTAL_TESTS -gt 0 ]; then
        local pass_rate=$((100 * PASSED_TESTS / TOTAL_TESTS))
        echo "Pass Rate:        ${pass_rate}%"
    fi
    
    echo ""
    echo "════════════════════════════════════════════════════════════════"
    
    # Integration readiness verdict
    if [ $HIGH_FAILURES -gt 0 ]; then
        echo -e "${YELLOW}⚠️  INTEGRATION READINESS: INCOMPLETE${NC}"
        echo -e "${YELLOW}   $HIGH_FAILURES HIGH priority integration issues${NC}"
        echo ""
        echo "Next Steps:"
        echo "  1. Create missing E2E tests for tenant isolation"
        echo "  2. Create policy enforcement E2E test"
        echo "  3. Create audit trail validation test"
        echo "  4. Verify service discovery integration"
        echo ""
        return 1
    elif [ $FAILED_TESTS -gt 0 ]; then
        echo -e "${BLUE}ℹ️  INTEGRATION READINESS: BASIC${NC}"
        echo -e "${BLUE}   $FAILED_TESTS non-critical improvements needed${NC}"
        return 0
    else
        echo -e "${GREEN}✅ INTEGRATION READINESS: EXCELLENT${NC}"
        return 0
    fi
}

main
exit_code=$?

# Generate report
if [ -d "test-results/reports" ]; then
    cat > "test-results/reports/integration-requirements-$(date +%Y%m%d-%H%M%S).txt" << EOF
Integration Requirements Test Report
Generated: $(date)

Total Tests: $TOTAL_TESTS
Passed: $PASSED_TESTS
Failed: $FAILED_TESTS
HIGH Failures: $HIGH_FAILURES

Detailed Results:
$(printf '%s\n' "${TEST_RESULTS[@]}")
EOF
fi

exit $exit_code
