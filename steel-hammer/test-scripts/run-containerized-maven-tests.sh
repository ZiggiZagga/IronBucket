#!/bin/bash
# Containerized Maven Test Runner
# Runs inside steel-hammer-test container
# Tests all 6 Maven projects and reports results

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║              Containerized Test Execution                      ║${NC}"
echo -e "${BLUE}║         (Running inside steel-hammer-test container)           ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Verify we're in a container
if [ ! -f /.dockerenv ]; then
    echo -e "${RED}ERROR: This script should only run inside Docker!${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Running inside Docker container${NC}"
echo ""

# Configuration
TEMP_DIR="/workspaces/IronBucket/temp"
RESULTS_DIR="/tmp/ironbucket-test-results"
LOG_FILE="/tmp/test-execution.log"

mkdir -p "$RESULTS_DIR"

echo "Test Configuration:"
echo "  Temp Directory: $TEMP_DIR"
echo "  Results Directory: $RESULTS_DIR"
echo "  Log File: $LOG_FILE"
echo ""

# Verify Maven is available
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ ERROR: Maven is not installed in the container${NC}"
    exit 1
fi

echo "Maven version:"
mvn --version | head -3
echo ""

# ============================================================================
# RUN TESTS FOR ALL 6 PROJECTS
# ============================================================================

echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Running Maven Tests for All 6 Projects${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo ""

TOTAL_TESTS=0
TOTAL_FAILURES=0
PROJECTS_PASSED=0
PROJECTS_FAILED=0

for project in Brazz-Nossel Claimspindel Buzzle-Vane Sentinel-Gear Storage-Conductor Vault-Smith; do
    PROJECT_DIR="$TEMP_DIR/$project"

    if [ ! -d "$PROJECT_DIR" ]; then
        echo -e "${YELLOW}⏭️  $project: Directory not found${NC}"
        continue
    fi

    echo -n "Testing $project... "
    cd "$PROJECT_DIR"

    if mvn clean test -q 2>&1 | tee -a "$LOG_FILE" > /tmp/test-${project}.log; then
        # Extract test count
        TEST_COUNT=$(grep -oP 'Tests run: \K[0-9]+' /tmp/test-${project}.log | tail -1 || echo "0")
        echo -e "${GREEN}✅ ($TEST_COUNT tests passed)${NC}"
        TOTAL_TESTS=$((TOTAL_TESTS + TEST_COUNT))
        PROJECTS_PASSED=$((PROJECTS_PASSED + 1))
    else
        # Check for failures
        FAILURES=$(grep -oP 'Failures: \K[0-9]+' /tmp/test-${project}.log | tail -1 || echo "0")
        if [ "$FAILURES" = "0" ]; then
            echo -e "${YELLOW}⏭️  (no tests or skipped)${NC}"
        else
            echo -e "${RED}❌ ($FAILURES failures)${NC}"
            TOTAL_FAILURES=$((TOTAL_FAILURES + FAILURES))
            PROJECTS_FAILED=$((PROJECTS_FAILED + 1))
        fi
    fi
done

cd /

echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Test Execution Summary${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo ""

echo "Projects Tested:"
echo "  ✅ Passed: $PROJECTS_PASSED"
echo "  ❌ Failed: $PROJECTS_FAILED"
echo ""

echo "Test Results:"
echo "  Total Tests: $TOTAL_TESTS"
echo "  Total Failures: $TOTAL_FAILURES"
echo ""

if [ $PROJECTS_FAILED -eq 0 ] && [ $TOTAL_TESTS -gt 0 ]; then
    echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}  ✅ ALL TESTS PASSED! ✅${NC}"
    echo -e "${GREEN}  IronBucket is PRODUCTION READY! 🚀${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
    exit 0
else
    echo -e "${RED}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${RED}  ❌ SOME TESTS FAILED${NC}"
    echo -e "${RED}════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "View logs:"
    echo "  docker logs steel-hammer-test | tail -100"
    exit 1
fi
