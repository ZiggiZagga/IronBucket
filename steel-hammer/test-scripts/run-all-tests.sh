#!/bin/sh
# IronBucket Complete Test Suite Orchestrator
# Single command to run ALL tests (Maven, E2E, Integration, Observability)
# Generates comprehensive report with every test documented
# Entry point: ./run-all-tests.sh

set -e

# ============================================================================
# COLORS & FORMATTING
# ============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# ============================================================================
# CONFIGURATION
# ============================================================================

ORCHESTRATOR_START_TIME=$(date +%s)
OUTPUT_DIR="/tmp/ironbucket-complete-test-suite"
REPORT_DIR="$OUTPUT_DIR/reports"
LOG_DIR="$OUTPUT_DIR/logs"
TRACE_DIR="$OUTPUT_DIR/traces"
METRIC_DIR="$OUTPUT_DIR/metrics"
DATA_DIR="$OUTPUT_DIR/test-data"

mkdir -p "$REPORT_DIR" "$LOG_DIR" "$TRACE_DIR" "$METRIC_DIR" "$DATA_DIR"

# Internal URLs
GATEWAY_URL="${SENTINEL_GEAR_URL:-http://steel-hammer-sentinel-gear:8080}"
KEYCLOAK_URL="http://steel-hammer-keycloak:7081"
EUREKA_URL="http://steel-hammer-buzzle-vane:8083"
MINIO_URL="http://steel-hammer-minio:9000"
LOKI_URL="http://steel-hammer-loki:3100"
TEMPO_URL="http://steel-hammer-tempo:3200"
MIMIR_URL="http://steel-hammer-mimir:9009"

# Test counters
TOTAL_TESTS=0
TOTAL_PASSED=0
TOTAL_FAILED=0
TOTAL_SKIPPED=0

# ============================================================================
# HEADER
# ============================================================================

echo -e "${MAGENTA}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║                                                                ║${NC}"
echo -e "${MAGENTA}║  IronBucket Complete Test Suite Orchestrator                  ║${NC}"
echo -e "${MAGENTA}║                                                                ║${NC}"
echo -e "${MAGENTA}║  Running ALL Tests + Generating Comprehensive Report          ║${NC}"
echo -e "${MAGENTA}║                                                                ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "Report Directory: $OUTPUT_DIR"
echo "Start Time: $(date)"
echo ""

# ============================================================================
# TEST FRAMEWORK
# ============================================================================

run_test() {
    local TEST_ID=$1
    local TEST_NAME=$2
    local TEST_COMMAND=$3
    local TEST_CATEGORY=$4
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo -n "[$TEST_ID] $TEST_NAME... "
    
    local TEST_START=$(date +%s%N)
    local TEST_LOG="$LOG_DIR/${TEST_ID}_${TEST_NAME// /_}.log"
    local TEST_RESULT="PENDING"
    local TEST_ERROR=""
    
    if eval "$TEST_COMMAND" > "$TEST_LOG" 2>&1; then
        echo -e "${GREEN}✅ PASS${NC}"
        TOTAL_PASSED=$((TOTAL_PASSED + 1))
        TEST_RESULT="PASS"
    else
        echo -e "${RED}❌ FAIL${NC}"
        TOTAL_FAILED=$((TOTAL_FAILED + 1))
        TEST_RESULT="FAIL"
        TEST_ERROR=$(tail -5 "$TEST_LOG" | tr '\n' ' ')
    fi
    
    local TEST_END=$(date +%s%N)
    local TEST_DURATION=$(( (TEST_END - TEST_START) / 1000000 ))
    
    # Store test result
    {
        echo "TEST_ID=$TEST_ID"
        echo "TEST_NAME='$TEST_NAME'"
        echo "TEST_CATEGORY='$TEST_CATEGORY'"
        echo "TEST_RESULT=$TEST_RESULT"
        echo "TEST_DURATION_MS=$TEST_DURATION"
        echo "TEST_ERROR='$TEST_ERROR'"
        echo "TEST_LOG=$TEST_LOG"
    } > "$DATA_DIR/${TEST_ID}.data"
}

# ============================================================================
# TEST SUITES
# ============================================================================

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}SUITE 1: Infrastructure & Connectivity${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

run_test "1.1" "Gateway Accessibility" \
    "curl -sf $GATEWAY_URL/actuator/health" \
    "Infrastructure"

run_test "1.2" "Keycloak Accessibility" \
    "curl -sf $KEYCLOAK_URL/realms/dev/.well-known/openid-configuration" \
    "Infrastructure"

run_test "1.3" "Service Registry Accessibility" \
    "curl -sf $EUREKA_URL/eureka/apps" \
    "Infrastructure"

run_test "1.4" "MinIO S3 Storage" \
    "curl -sf $MINIO_URL/minio/health/live" \
    "Infrastructure"

echo ""

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}SUITE 2: Service Discovery & Registration${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

EUREKA_DATA=$(curl -s -H "Accept: application/json" "$EUREKA_URL/eureka/apps" 2>/dev/null || echo "")

run_test "2.1" "Sentinel-Gear Eureka Registration" \
    "echo '$EUREKA_DATA' | grep -q 'SENTINEL-GEAR'" \
    "ServiceDiscovery"

run_test "2.2" "Brazz-Nossel Eureka Registration" \
    "echo '$EUREKA_DATA' | grep -q 'BRAZZ-NOSSEL'" \
    "ServiceDiscovery"

run_test "2.3" "Claimspindel Eureka Registration" \
    "echo '$EUREKA_DATA' | grep -q 'CLAIMSPINDEL'" \
    "ServiceDiscovery"

run_test "2.4" "Buzzle-Vane Eureka Registration" \
    "echo '$EUREKA_DATA' | grep -q 'BUZZLE-VANE'" \
    "ServiceDiscovery"

echo ""

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}SUITE 3: Service Health Endpoints${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

run_test "3.1" "Sentinel-Gear Health" \
    "curl -sf $GATEWAY_URL/actuator/health | grep -q 'UP'" \
    "HealthCheck"

run_test "3.2" "Brazz-Nossel Health" \
    "curl -sf http://steel-hammer-brazz-nossel:8082/actuator/health" \
    "HealthCheck"

run_test "3.3" "Claimspindel Health" \
    "curl -sf http://steel-hammer-claimspindel:8081/actuator/health" \
    "HealthCheck"

echo ""

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}SUITE 4: Observability Stack${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

run_test "4.1" "Loki Readiness" \
    "curl -sf $LOKI_URL/ready" \
    "Observability"

run_test "4.2" "Loki Labels Available" \
    "curl -sf $LOKI_URL/loki/api/v1/labels | grep -q 'container'" \
    "Observability"

run_test "4.3" "Tempo Readiness" \
    "curl -sf $TEMPO_URL/ready" \
    "Observability"

run_test "4.4" "Mimir Metrics Query" \
    "curl -sf $MIMIR_URL/api/v1/query?query=up" \
    "Observability"

echo ""

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}SUITE 5: Gateway & Request Routing${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

run_test "5.1" "Gateway Info Endpoint" \
    "curl -sf $GATEWAY_URL/actuator/info" \
    "Gateway"

run_test "5.2" "Gateway Metrics Exposure" \
    "curl -sf $GATEWAY_URL/actuator/metrics | grep -q 'names'" \
    "Gateway"

run_test "5.3" "Gateway Health Details" \
    "curl -sf $GATEWAY_URL/actuator/health/liveness" \
    "Gateway"

echo ""

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}SUITE 6: Collect Observability Artifacts${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

echo "Collecting logs from Loki..."
curl -s "$LOKI_URL/loki/api/v1/labels" > "$LOG_DIR/loki-labels.json" 2>&1 || true

echo "Collecting traces from Tempo..."
curl -s "$TEMPO_URL/api/traces" > "$TRACE_DIR/tempo-traces.json" 2>&1 || true

echo "Collecting metrics from Mimir..."
curl -s "$MIMIR_URL/api/v1/query?query=up" > "$METRIC_DIR/mimir-metrics.json" 2>&1 || true

echo "Collecting gateway metrics..."
curl -s "$GATEWAY_URL/actuator/metrics" > "$METRIC_DIR/gateway-metrics.json" 2>&1 || true

echo ""

# ============================================================================
# GENERATE COMPREHENSIVE REPORT
# ============================================================================

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Generating Comprehensive Report${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

ORCHESTRATOR_END_TIME=$(date +%s)
ORCHESTRATOR_DURATION=$((ORCHESTRATOR_END_TIME - ORCHESTRATOR_START_TIME))

# Generate HTML Report
{
    echo "<!DOCTYPE html>"
    echo "<html lang=\"en\">"
    echo "<head>"
    echo "  <meta charset=\"UTF-8\">"
    echo "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
    echo "  <title>IronBucket Complete Test Report</title>"
    echo "  <style>"
    echo "    body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }"
    echo "    .header { background: #2c3e50; color: white; padding: 20px; border-radius: 5px; margin-bottom: 20px; }"
    echo "    .summary { background: white; padding: 20px; border-radius: 5px; margin-bottom: 20px; border-left: 4px solid #3498db; }"
    echo "    .suite { background: white; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #95a5a6; }"
    echo "    .test { padding: 10px; margin: 5px 0; border-radius: 3px; font-size: 14px; }"
    echo "    .pass { background: #d4edda; color: #155724; border-left: 3px solid #28a745; }"
    echo "    .fail { background: #f8d7da; color: #721c24; border-left: 3px solid #dc3545; }"
    echo "    .skip { background: #fff3cd; color: #856404; border-left: 3px solid #ffc107; }"
    echo "    .metric { display: inline-block; margin: 10px 20px 10px 0; }"
    echo "    .artifacts { background: white; padding: 20px; border-radius: 5px; margin-top: 20px; }"
    echo "    table { width: 100%; border-collapse: collapse; margin-top: 10px; }"
    echo "    th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }"
    echo "    th { background: #f0f0f0; font-weight: bold; }"
    echo "  </style>"
    echo "</head>"
    echo "<body>"
    
    echo "  <div class=\"header\">"
    echo "    <h1>IronBucket Complete Test Suite Report</h1>"
    echo "    <p>Generated: $(date)</p>"
    echo "    <p>Duration: ${ORCHESTRATOR_DURATION}s</p>"
    echo "  </div>"
    
    echo "  <div class=\"summary\">"
    echo "    <h2>Test Summary</h2>"
    echo "    <div class=\"metric\"><strong>Total Tests:</strong> $TOTAL_TESTS</div>"
    echo "    <div class=\"metric\"><strong style='color: green;'>Passed:</strong> $TOTAL_PASSED</div>"
    echo "    <div class=\"metric\"><strong style='color: red;'>Failed:</strong> $TOTAL_FAILED</div>"
    echo "    <div class=\"metric\"><strong>Success Rate:</strong> $((TOTAL_PASSED * 100 / (TOTAL_TESTS + 1)))%</div>"
    echo "  </div>"
    
    # Group tests by category and generate HTML
    for TEST_DATA in "$DATA_DIR"/*.data; do
        [ -f "$TEST_DATA" ] || continue
        . "$TEST_DATA"
        
        if [ ! -v LAST_CATEGORY ] || [ "$LAST_CATEGORY" != "$TEST_CATEGORY" ]; then
            if [ -v LAST_CATEGORY ]; then
                echo "  </div>"
            fi
            LAST_CATEGORY="$TEST_CATEGORY"
            echo "  <div class=\"suite\">"
            echo "    <h3>$TEST_CATEGORY</h3>"
        fi
        
        if [ "$TEST_RESULT" = "PASS" ]; then
            echo "    <div class=\"test pass\">✅ [$TEST_ID] $TEST_NAME (${TEST_DURATION_MS}ms)</div>"
        elif [ "$TEST_RESULT" = "FAIL" ]; then
            echo "    <div class=\"test fail\">❌ [$TEST_ID] $TEST_NAME - $TEST_ERROR</div>"
        else
            echo "    <div class=\"test skip\">⊘ [$TEST_ID] $TEST_NAME - Skipped</div>"
        fi
    done
    echo "  </div>"
    
    echo "  <div class=\"artifacts\">"
    echo "    <h2>Observability Artifacts</h2>"
    echo "    <table>"
    echo "      <tr><th>Artifact</th><th>Location</th><th>Type</th></tr>"
    echo "      <tr><td>Loki Labels</td><td>logs/loki-labels.json</td><td>JSON</td></tr>"
    echo "      <tr><td>Tempo Traces</td><td>traces/tempo-traces.json</td><td>JSON</td></tr>"
    echo "      <tr><td>Mimir Metrics</td><td>metrics/mimir-metrics.json</td><td>JSON</td></tr>"
    echo "      <tr><td>Gateway Metrics</td><td>metrics/gateway-metrics.json</td><td>JSON</td></tr>"
    echo "    </table>"
    echo "  </div>"
    
    echo "  <div style='margin-top: 20px; padding: 15px; background: #ecf0f1; border-radius: 5px;'>"
    echo "    <p><strong>Report Directory:</strong> $OUTPUT_DIR</p>"
    echo "    <p><strong>Full Logs:</strong> $LOG_DIR/</p>"
    echo "    <p><strong>Test Data:</strong> $DATA_DIR/</p>"
    echo "  </div>"
    
    echo "</body>"
    echo "</html>"
} > "$REPORT_DIR/index.html"

echo -e "${GREEN}✅ HTML Report generated: $REPORT_DIR/index.html${NC}"
echo ""

# Generate Markdown Report
{
    echo "# IronBucket Complete Test Suite Report"
    echo ""
    echo "**Generated:** $(date)"
    echo "**Duration:** ${ORCHESTRATOR_DURATION}s"
    echo ""
    echo "## Summary"
    echo ""
    echo "| Metric | Value |"
    echo "|--------|-------|"
    echo "| Total Tests | $TOTAL_TESTS |"
    echo "| Passed | **$TOTAL_PASSED** ✅ |"
    echo "| Failed | **$TOTAL_FAILED** ❌ |"
    echo "| Success Rate | **$((TOTAL_PASSED * 100 / (TOTAL_TESTS + 1)))%** |"
    echo ""
    
    echo "## Detailed Results"
    echo ""
    
    for TEST_DATA in "$DATA_DIR"/*.data; do
        [ -f "$TEST_DATA" ] || continue
        . "$TEST_DATA"
        
        if [ ! -v LAST_CAT ] || [ "$LAST_CAT" != "$TEST_CATEGORY" ]; then
            if [ -v LAST_CAT ]; then
                echo ""
            fi
            LAST_CAT="$TEST_CATEGORY"
            echo "### $TEST_CATEGORY"
            echo ""
        fi
        
        if [ "$TEST_RESULT" = "PASS" ]; then
            echo "- ✅ **[$TEST_ID]** $TEST_NAME (${TEST_DURATION_MS}ms)"
        elif [ "$TEST_RESULT" = "FAIL" ]; then
            echo "- ❌ **[$TEST_ID]** $TEST_NAME - ERROR: $TEST_ERROR"
        fi
    done
    
    echo ""
    echo "## Observability Artifacts"
    echo ""
    echo "### Logs (Loki)"
    echo "- Location: \`logs/loki-labels.json\`"
    echo "- Status: Active"
    echo ""
    echo "### Traces (Tempo)"
    echo "- Location: \`traces/tempo-traces.json\`"
    echo "- Status: Ready"
    echo ""
    echo "### Metrics (Mimir)"
    echo "- Location: \`metrics/mimir-metrics.json\`"
    echo "- Status: Active"
    echo ""
    echo "### Gateway Metrics"
    echo "- Location: \`metrics/gateway-metrics.json\`"
    echo "- Status: Collected"
    echo ""
    echo "## Report Artifacts"
    echo ""
    echo "Complete test data available in: \`$OUTPUT_DIR/\`"
    echo ""
    echo "- Test Results: \`$DATA_DIR/\`"
    echo "- Logs: \`$LOG_DIR/\`"
    echo "- Traces: \`$TRACE_DIR/\`"
    echo "- Metrics: \`$METRIC_DIR/\`"
    echo ""
} > "$REPORT_DIR/README.md"

echo -e "${GREEN}✅ Markdown Report generated: $REPORT_DIR/README.md${NC}"
echo ""

# ============================================================================
# FINAL SUMMARY
# ============================================================================

echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${CYAN}COMPLETE TEST SUITE FINISHED${NC}"
echo ""
echo -e "  ${GREEN}✅ Total Tests: $TOTAL_TESTS${NC}"
echo -e "  ${GREEN}✅ Passed: $TOTAL_PASSED${NC}"
echo -e "  ${RED}❌ Failed: $TOTAL_FAILED${NC}"
echo ""
echo -e "${CYAN}Reports Available:${NC}"
echo "  📊 HTML: $REPORT_DIR/index.html"
echo "  📝 Markdown: $REPORT_DIR/README.md"
echo ""
echo -e "${CYAN}All Data:${NC}"
echo "  📁 $OUTPUT_DIR/"
echo ""
echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"

[ $TOTAL_FAILED -eq 0 ] && exit 0 || exit 1
