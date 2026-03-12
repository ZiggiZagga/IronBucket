#!/usr/bin/env bash

################################################################################
# IronBucket Observability Requirements Test Suite
#
# Purpose: Validate observability stack is deployed and configured
# Status: RED - These tests MUST FAIL initially, then we implement
# Priority: P1 HIGH - Required for production operations
#
# Based on: docs/PRODUCTION-READINESS-ROADMAP.md
# Marathon Mindset: Tests define what "observable" means
################################################################################

set -euo pipefail

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
# P1 HIGH Priority Observability Tests
################################################################################

test_lgtm_stack_deployed() {
    log_test_start "LGTM Stack Deployed"
    
    # Check if LGTM services are running
    if command -v kubectl &> /dev/null; then
        local lgtm_pods=$(kubectl get pods -n observability 2>/dev/null || echo "")
        
        if echo "$lgtm_pods" | grep -q "loki\|grafana\|tempo\|mimir"; then
            log_test_pass "LGTM stack pods found in observability namespace"
        else
            log_test_fail "LGTM stack NOT deployed" \
                "HIGH" \
                "Deploy LGTM stack: docker-compose -f steel-hammer/docker-compose-lgtm.yml up -d"
        fi
    else
        # Check Docker Compose
        if docker ps 2>/dev/null | grep -q "loki\|grafana\|tempo\|mimir"; then
            log_test_pass "LGTM stack running in Docker"
        else
            log_test_fail "LGTM stack NOT deployed" \
                "HIGH" \
                "Deploy: docker-compose -f steel-hammer/docker-compose-lgtm.yml up -d"
        fi
    fi
}

test_grafana_accessible() {
    log_test_start "Grafana Accessible"
    
    if curl -s --max-time 5 http://localhost:3000 &> /dev/null; then
        log_test_pass "Grafana is accessible on port 3000"
    else
        log_test_fail "Grafana NOT accessible" \
            "HIGH" \
            "Grafana should be accessible at http://localhost:3000"
    fi
}

test_loki_accessible() {
    log_test_start "Loki (Logging) Accessible"
    
    if curl -s --max-time 5 http://localhost:3100/ready &> /dev/null; then
        log_test_pass "Loki is accessible on port 3100"
    else
        log_test_fail "Loki NOT accessible" \
            "HIGH" \
            "Loki should be accessible at http://localhost:3100"
    fi
}

test_tempo_accessible() {
    log_test_start "Tempo (Tracing) Accessible"
    
    if curl -s --max-time 5 http://localhost:3200/ready &> /dev/null; then
        log_test_pass "Tempo is accessible on port 3200"
    else
        log_test_fail "Tempo NOT accessible" \
            "HIGH" \
            "Tempo should be accessible at http://localhost:3200"
    fi
}

test_mimir_accessible() {
    log_test_start "Mimir (Metrics) Accessible"
    
    if curl -s --max-time 5 http://localhost:9009/ready &> /dev/null; then
        log_test_pass "Mimir is accessible on port 9009"
    else
        log_test_fail "Mimir NOT accessible" \
            "HIGH" \
            "Mimir should be accessible at http://localhost:9009"
    fi
}

################################################################################
# P2 MEDIUM Priority Dashboard Tests
################################################################################

test_grafana_dashboards_exist() {
    log_test_start "Grafana Dashboards Created"
    
    # Check if dashboards directory exists
    if [ -d "steel-hammer/grafana/dashboards" ]; then
        local dashboard_count=$(find steel-hammer/grafana/dashboards -name "*.json" | wc -l)
        
        if [ "$dashboard_count" -ge 3 ]; then
            log_test_pass "Found $dashboard_count Grafana dashboards"
        else
            log_test_fail "Insufficient Grafana dashboards" \
                "MEDIUM" \
                "Only $dashboard_count dashboards found. Need: Security, Performance, Audit dashboards."
        fi
    else
        log_test_fail "Grafana dashboards NOT created" \
            "MEDIUM" \
            "Create dashboards in steel-hammer/grafana/dashboards/"
    fi
}

test_security_dashboard() {
    log_test_start "Security Dashboard Exists"
    
    if [ -f "steel-hammer/grafana/dashboards/security-dashboard.json" ]; then
        log_test_pass "Security dashboard exists"
    else
        log_test_fail "Security dashboard NOT found" \
            "MEDIUM" \
            "Create security dashboard showing: failed auth, policy denials, audit events"
    fi
}

test_performance_dashboard() {
    log_test_start "Performance Dashboard Exists"
    
    if [ -f "steel-hammer/grafana/dashboards/performance-dashboard.json" ]; then
        log_test_pass "Performance dashboard exists"
    else
        log_test_fail "Performance dashboard NOT found" \
            "MEDIUM" \
            "Create performance dashboard showing: latency, throughput, error rates"
    fi
}

test_audit_dashboard() {
    log_test_start "Audit Dashboard Exists"
    
    if [ -f "steel-hammer/grafana/dashboards/audit-dashboard.json" ]; then
        log_test_pass "Audit dashboard exists"
    else
        log_test_fail "Audit dashboard NOT found" \
            "MEDIUM" \
            "Create audit dashboard showing: access logs, compliance events, data changes"
    fi
}

################################################################################
# P2 MEDIUM Priority Alert Tests
################################################################################

test_alert_rules_exist() {
    log_test_start "Alert Rules Configured"
    
    if [ -f "steel-hammer/alerts/alert-rules.yml" ] || [ -f "steel-hammer/prometheus/alerts.yml" ]; then
        log_test_pass "Alert rules configuration found"
    else
        log_test_fail "Alert rules NOT configured" \
            "MEDIUM" \
            "Create alert rules for: high error rate, slow response, failed auth"
    fi
}

test_critical_alerts_defined() {
    log_test_start "CRITICAL Alerts Defined"
    
    local required_alerts=("HighErrorRate" "ServiceDown" "SecurityBreach")
    local alerts_file="steel-hammer/alerts/alert-rules.yml"
    
    if [ -f "$alerts_file" ]; then
        local missing_alerts=()
        for alert in "${required_alerts[@]}"; do
            if ! grep -q "$alert" "$alerts_file"; then
                missing_alerts+=("$alert")
            fi
        done
        
        if [ ${#missing_alerts[@]} -eq 0 ]; then
            log_test_pass "All CRITICAL alerts defined"
        else
            log_test_fail "Missing CRITICAL alerts" \
                "MEDIUM" \
                "Missing: ${missing_alerts[*]}"
        fi
    else
        log_test_fail "No alert rules file found" \
            "MEDIUM" \
            "Create $alerts_file with CRITICAL alerts"
    fi
}

################################################################################
# P2 MEDIUM Priority Log Aggregation Tests
################################################################################

test_structured_logging_enabled() {
    log_test_start "Structured Logging Enabled"
    
    # Check if services use JSON logging
    local log_config_found=false
    
    if find temp/ -name "logback*.xml" -o -name "log4j*.xml" | xargs grep -q "JsonLayout\|JSONLayout" 2>/dev/null; then
        log_config_found=true
    fi
    
    if [ "$log_config_found" = true ]; then
        log_test_pass "Structured (JSON) logging configured"
    else
        log_test_fail "Structured logging NOT enabled" \
            "MEDIUM" \
            "Configure JSON logging in logback.xml for all services"
    fi
}

test_log_aggregation_working() {
    log_test_start "Log Aggregation to Loki Working"
    
    # Check if logs are being sent to Loki
    if command -v curl &> /dev/null; then
        local loki_labels=$(curl -s http://localhost:3100/loki/api/v1/labels 2>/dev/null || echo "")
        
        if echo "$loki_labels" | grep -q "service\|application"; then
            log_test_pass "Logs are being aggregated in Loki"
        else
            log_test_fail "Log aggregation NOT working" \
                "MEDIUM" \
                "Configure services to send logs to Loki"
        fi
    else
        log_test_fail "Cannot verify log aggregation" \
            "MEDIUM" \
            "curl not available"
    fi
}

################################################################################
# P2 MEDIUM Priority Distributed Tracing Tests
################################################################################

test_distributed_tracing_enabled() {
    log_test_start "Distributed Tracing Enabled"
    
    # Check if OpenTelemetry is configured
    if find temp/ -name "pom.xml" | xargs grep -q "opentelemetry\|spring-cloud-sleuth" 2>/dev/null; then
        log_test_pass "Distributed tracing dependencies found"
    else
        log_test_fail "Distributed tracing NOT enabled" \
            "MEDIUM" \
            "Add OpenTelemetry or Spring Cloud Sleuth dependencies"
    fi
}

test_traces_in_tempo() {
    log_test_start "Traces Being Sent to Tempo"
    
    if command -v curl &> /dev/null; then
        # Check if Tempo has received any traces
        local tempo_metrics=$(curl -s http://localhost:3200/metrics 2>/dev/null || echo "")
        
        if echo "$tempo_metrics" | grep -q "tempo_distributor_spans_received"; then
            log_test_pass "Traces are being sent to Tempo"
        else
            log_test_fail "No traces in Tempo" \
                "MEDIUM" \
                "Configure services to send traces to Tempo (OTLP endpoint)"
        fi
    else
        log_test_fail "Cannot verify trace collection" \
            "MEDIUM" \
            "curl not available"
    fi
}

################################################################################
# P3 LOW Priority Metrics Tests
################################################################################

test_prometheus_metrics_exposed() {
    log_test_start "Prometheus Metrics Exposed"
    
    # Check if services expose /actuator/prometheus
    local service_url="http://sentinel-gear:8081"
    
    if curl -s --max-time 5 "${service_url}/actuator/prometheus" 2>/dev/null | grep -q "^# TYPE"; then
        log_test_pass "Prometheus metrics are exposed"
    else
        log_test_fail "Prometheus metrics NOT exposed" \
            "LOW" \
            "Enable Prometheus metrics in actuator endpoints"
    fi
}

test_custom_metrics_defined() {
    log_test_start "Custom Business Metrics Defined"
    
    # Check for custom metrics in code
    if find temp/ -name "*.java" | xargs grep -q "@Timed\|@Counted\|MeterRegistry" 2>/dev/null; then
        log_test_pass "Custom metrics instrumentation found"
    else
        log_test_fail "Custom metrics NOT defined" \
            "LOW" \
            "Add custom metrics for: auth_failures, policy_denials, s3_operations"
    fi
}

################################################################################
# Main Execution
################################################################################

main() {
    echo "════════════════════════════════════════════════════════════════"
    echo "  IronBucket Observability Requirements Test Suite"
    echo "  Status: RED (Tests expected to FAIL initially)"
    echo "  Priority: P1 HIGH"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    
    # P1 HIGH Tests
    echo -e "${YELLOW}═══ P1 HIGH Priority Observability Tests ═══${NC}"
    test_lgtm_stack_deployed
    test_grafana_accessible
    test_loki_accessible
    test_tempo_accessible
    test_mimir_accessible
    echo ""
    
    # P2 MEDIUM Dashboard Tests
    echo -e "${BLUE}═══ P2 MEDIUM Priority Dashboard Tests ═══${NC}"
    test_grafana_dashboards_exist
    test_security_dashboard
    test_performance_dashboard
    test_audit_dashboard
    echo ""
    
    # P2 MEDIUM Alert Tests
    echo -e "${BLUE}═══ P2 MEDIUM Priority Alert Tests ═══${NC}"
    test_alert_rules_exist
    test_critical_alerts_defined
    echo ""
    
    # P2 MEDIUM Logging Tests
    echo -e "${BLUE}═══ P2 MEDIUM Priority Logging Tests ═══${NC}"
    test_structured_logging_enabled
    test_log_aggregation_working
    echo ""
    
    # P2 MEDIUM Tracing Tests
    echo -e "${BLUE}═══ P2 MEDIUM Priority Tracing Tests ═══${NC}"
    test_distributed_tracing_enabled
    test_traces_in_tempo
    echo ""
    
    # P3 LOW Metrics Tests
    echo -e "${BLUE}═══ P3 LOW Priority Metrics Tests ═══${NC}"
    test_prometheus_metrics_exposed
    test_custom_metrics_defined
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
    
    # Observability readiness verdict
    if [ $HIGH_FAILURES -gt 0 ]; then
        echo -e "${YELLOW}⚠️  OBSERVABILITY READINESS: INCOMPLETE${NC}"
        echo -e "${YELLOW}   $HIGH_FAILURES HIGH priority issues${NC}"
        echo ""
        echo "Next Steps:"
        echo "  1. Deploy LGTM stack: docker-compose -f steel-hammer/docker-compose-lgtm.yml up -d"
        echo "  2. Create Grafana dashboards (Security, Performance, Audit)"
        echo "  3. Configure alert rules"
        echo "  4. Enable structured logging"
        echo ""
        return 1
    elif [ $FAILED_TESTS -gt 0 ]; then
        echo -e "${BLUE}ℹ️  OBSERVABILITY READINESS: BASIC${NC}"
        echo -e "${BLUE}   $FAILED_TESTS non-critical improvements needed${NC}"
        return 0
    else
        echo -e "${GREEN}✅ OBSERVABILITY READINESS: EXCELLENT${NC}"
        return 0
    fi
}

main
exit_code=$?

# Generate report
if [ -d "test-results/reports" ]; then
    cat > "test-results/reports/observability-requirements-$(date +%Y%m%d-%H%M%S).txt" << EOF
Observability Requirements Test Report
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
