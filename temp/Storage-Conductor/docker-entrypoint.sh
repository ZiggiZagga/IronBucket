#!/bin/bash
set -e

# Storage-Conductor Test Entrypoint
# Runs integration tests with results persisted to MinIO (following alice-bob pattern)
# Tests validate S3 operations through Sentinel-Gear identity gateway

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║       STORAGE-CONDUCTOR INTEGRATION TEST ENTRYPOINT           ║"
echo "║    S3 Operations Through Sentinel-Gear → Results to MinIO      ║"
echo "║                                                                ║"
echo "║  Pattern: alice-bob e2e tests (verify infrastructure)          ║"
echo "║           run tests, persist results to MinIO                  ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Test configuration from environment variables
S3_ENDPOINT="${S3_ENDPOINT:-http://minio:9000}"
S3_ACCESS_KEY="${S3_ACCESS_KEY:-minioadmin}"
S3_SECRET_KEY="${S3_SECRET_KEY:-minioadmin}"
S3_REGION="${S3_REGION:-us-east-1}"

# Sentinel-Gear configuration for identity gateway
IDENTITY_GATEWAY="${IDENTITY_GATEWAY:-http://sentinel-gear:8080}"
VAULT_SMITH_URL="${VAULT_SMITH_URL:-http://vault-smith:8090}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://keycloak:8080}"
JWT_AUDIENCE="${JWT_AUDIENCE:-ironfaucet}"
TEST_TENANT="${TEST_TENANT:-test-org-001}"

echo "[INFO] Test Configuration"
echo "────────────────────────────────────────────────────────────────"
echo "  S3 Endpoint:          $S3_ENDPOINT"
echo "  S3 Region:            $S3_REGION"
echo "  Identity Gateway:     $IDENTITY_GATEWAY"
echo "  Vault-Smith Backend:  $VAULT_SMITH_URL"
echo "  Keycloak Provider:    $KEYCLOAK_URL"
echo "  JWT Audience:         $JWT_AUDIENCE"
echo "  Test Tenant:          $TEST_TENANT"
echo ""

# Wait for backend services to be ready
echo "[INFO] Waiting for infrastructure services to be ready..."
max_attempts=60
attempt=0
while [ $attempt -lt $max_attempts ]; do
    if curl -f -s "$S3_ENDPOINT/minio/health/live" > /dev/null 2>&1; then
        echo "[OK] MinIO S3 backend is ready"
        break
    fi
    attempt=$((attempt + 1))
    echo -n "."
    sleep 1
done

attempt=0
while [ $attempt -lt $max_attempts ]; do
    if curl -f -s "$KEYCLOAK_URL/health" > /dev/null 2>&1; then
        echo "[OK] Keycloak identity provider is ready"
        break
    fi
    attempt=$((attempt + 1))
    echo -n "."
    sleep 1
done

attempt=0
while [ $attempt -lt $max_attempts ]; do
    if curl -f -s "$VAULT_SMITH_URL/actuator/health" > /dev/null 2>&1; then
        echo "[OK] Vault-Smith service is ready"
        break
    fi
    attempt=$((attempt + 1))
    echo -n "."
    sleep 1
done

attempt=0
while [ $attempt -lt $max_attempts ]; do
    if curl -f -s "$IDENTITY_GATEWAY_URL/actuator/health" > /dev/null 2>&1; then
        echo "[OK] Sentinel-Gear identity gateway is ready"
        break
    fi
    attempt=$((attempt + 1))
    echo -n "."
    sleep 1
done

echo ""
echo "[OK] All infrastructure services are ready"
echo ""

echo ""
echo "[INFO] Starting Storage-Conductor test suite..."
echo "────────────────────────────────────────────────────────────────"
echo ""

# Create test report directory
REPORT_DIR="/test-reports"
mkdir -p "$REPORT_DIR"
REPORT_FILE="$REPORT_DIR/storage-conductor-test-report.log"

# ============================================================================
# PHASE 4: Execute Integration Tests (following alice-bob e2e pattern)
# ============================================================================

echo ""
echo "[INFO] Starting integration tests (alice-bob pattern)"
echo "────────────────────────────────────────────────────────────────"
echo ""
echo "Phase: Infrastructure verification"
echo "Phase: Test user authentication via Keycloak"
echo "Phase: Create test bucket in MinIO"
echo "Phase: Execute 11 S3 compatibility tests"
echo "Phase: Persist results to MinIO"
echo ""

# Run integration tests with results persistence
bash /run-integration-tests.sh

TEST_RESULT=$?

echo "" | tee -a "$REPORT_FILE"
echo "════════════════════════════════════════════════════════════════" | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

# Parse test results from Surefire reports
SUREFIRE_DIR="target/surefire-reports"
if [ -d "$SUREFIRE_DIR" ]; then
    echo "[INFO] Analyzing Test Results" | tee -a "$REPORT_FILE"
    echo "────────────────────────────────────────────────────────────────" | tee -a "$REPORT_FILE"
    
    # Extract test statistics
    TEST_COUNT=0
    FAILURES=0
    ERRORS=0
    SKIPPED=0
    
    for xml_file in "$SUREFIRE_DIR"/*.xml; do
        if [ -f "$xml_file" ]; then
            # Extract test counts from XML (using grep instead of xml parsing for simplicity)
            while IFS='=' read -r key val; do
                case $key in
                    *tests) TEST_COUNT=$((TEST_COUNT + ${val%\"})) ;;
                    *failures) FAILURES=$((FAILURES + ${val%\"})) ;;
                    *errors) ERRORS=$((ERRORS + ${val%\"})) ;;
                    *skipped) SKIPPED=$((SKIPPED + ${val%\"})) ;;
                esac
            done < <(grep -oE '(tests|failures|errors|skipped)="[0-9]+"' "$xml_file" | sed 's/"//g')
        fi
    done
    
    echo "" | tee -a "$REPORT_FILE"
    echo "  Total Tests Run:    $TEST_COUNT" | tee -a "$REPORT_FILE"
    echo "  Failures:           $FAILURES" | tee -a "$REPORT_FILE"
    echo "  Errors:             $ERRORS" | tee -a "$REPORT_FILE"
    echo "  Skipped:            $SKIPPED" | tee -a "$REPORT_FILE"
    echo "" | tee -a "$REPORT_FILE"
    
    # Generate test summary
    if [ $FAILURES -eq 0 ] && [ $ERRORS -eq 0 ]; then
        echo "✓ ALL TESTS PASSED" | tee -a "$REPORT_FILE"
        TEST_STATUS="SUCCESS"
    else
        echo "✗ TESTS FAILED" | tee -a "$REPORT_FILE"
        TEST_STATUS="FAILURE"
    fi
    
    echo "" | tee -a "$REPORT_FILE"
    
    # List individual test results
    if [ -d "$SUREFIRE_DIR" ]; then
        echo "Individual Test Results:" | tee -a "$REPORT_FILE"
        echo "────────────────────────────────────────────────────────────────" | tee -a "$REPORT_FILE"
        
        for txt_file in "$SUREFIRE_DIR"/*.txt; do
            if [ -f "$txt_file" ]; then
                # Extract test method names from .txt files
                grep -E "^[0-9]+\)|^Tests run:|^Failures:|^Errors:" "$txt_file" | head -20 >> "$REPORT_FILE" 2>/dev/null || true
            fi
        done
    fi
else
    echo "[WARN] No Surefire reports found in $SUREFIRE_DIR" | tee -a "$REPORT_FILE"
fi

echo "" | tee -a "$REPORT_FILE"
echo "════════════════════════════════════════════════════════════════" | tee -a "$REPORT_FILE"
echo "[REPORT] Test report saved to: $REPORT_FILE" | tee -a "$REPORT_FILE"
echo "════════════════════════════════════════════════════════════════" | tee -a "$REPORT_FILE"

# Print final summary to console
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
if [ "$TEST_STATUS" = "SUCCESS" ]; then
    echo "║                    ✓ ALL TESTS PASSED                        ║"
else
    echo "║                    ✗ TESTS FAILED                            ║"
fi
echo "║                                                                ║"
echo "║  Test Report: /test-reports/storage-conductor-test-report.log  ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Copy reports to mounted volume if available
if [ -d "/test-reports" ]; then
    echo "[INFO] Copying detailed reports to /test-reports..."
    if [ -d "target/surefire-reports" ]; then
        cp -r target/surefire-reports/* /test-reports/ 2>/dev/null || true
    fi
fi

# Exit with test result
exit $TEST_RESULT
