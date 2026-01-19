#!/bin/bash
# Storage-Conductor: Containerized Test Runner
# Orchestrates complete test environment with identity gateway and S3 backend

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         STORAGE-CONDUCTOR: CONTAINERIZED TEST RUNNER           ║"
echo "║       Integration Testing Through Sentinel-Gear Identity      ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Configuration
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-tests.yml"
NETWORK_NAME="storage-test-network"
TEST_REPORTS_DIR="$SCRIPT_DIR/test-reports"

# Create reports directory
mkdir -p "$TEST_REPORTS_DIR"

# Parse command line arguments
COMMAND="${1:-up}"
case "$COMMAND" in
    up)
        echo "[INFO] Starting test environment..."
        echo "  Building Vault-Smith container..."
        docker-compose -f "$COMPOSE_FILE" build vault-smith
        
        echo "  Building Storage-Conductor test container..."
        docker-compose -f "$COMPOSE_FILE" build storage-conductor-tests
        
        echo "  Starting services..."
        docker-compose -f "$COMPOSE_FILE" up -d --wait
        
        echo "[INFO] Services started. Waiting for stability..."
        sleep 5
        
        echo "[INFO] Running tests..."
        docker-compose -f "$COMPOSE_FILE" run --rm storage-conductor-tests
        TEST_RESULT=$?
        
        if [ $TEST_RESULT -eq 0 ]; then
            echo ""
            echo "╔════════════════════════════════════════════════════════════════╗"
            echo "║                    ✓ ALL TESTS PASSED                        ║"
            echo "╚════════════════════════════════════════════════════════════════╝"
        else
            echo ""
            echo "╔════════════════════════════════════════════════════════════════╗"
            echo "║                    ✗ TESTS FAILED                            ║"
            echo "╚════════════════════════════════════════════════════════════════╝"
        fi
        
        # Show logs
        echo ""
        echo "[INFO] Test Report Available At:"
        echo "  $TEST_REPORTS_DIR/storage-conductor-test-report.log"
        
        if [ -f "$TEST_REPORTS_DIR/storage-conductor-test-report.log" ]; then
            echo ""
            echo "════════════════════════════════════════════════════════════════"
            echo "TEST REPORT EXCERPT:"
            echo "════════════════════════════════════════════════════════════════"
            tail -50 "$TEST_REPORTS_DIR/storage-conductor-test-report.log"
            echo "════════════════════════════════════════════════════════════════"
        fi
        
        exit $TEST_RESULT
        ;;
        
    down)
        echo "[INFO] Stopping test environment..."
        docker-compose -f "$COMPOSE_FILE" down -v
        echo "[OK] Test environment stopped"
        ;;
        
    logs)
        echo "[INFO] Displaying test logs..."
        SERVICE="${2:-storage-conductor-tests}"
        docker-compose -f "$COMPOSE_FILE" logs -f "$SERVICE"
        ;;
        
    report)
        REPORT_FILE="$TEST_REPORTS_DIR/storage-conductor-test-report.log"
        if [ -f "$REPORT_FILE" ]; then
            echo "[INFO] Displaying test report..."
            cat "$REPORT_FILE"
        else
            echo "[ERROR] Report not found: $REPORT_FILE"
            echo "[INFO] Run tests first: $0 up"
            exit 1
        fi
        ;;
        
    status)
        echo "[INFO] Test environment status:"
        docker-compose -f "$COMPOSE_FILE" ps
        ;;
        
    rebuild)
        echo "[INFO] Rebuilding containers..."
        docker-compose -f "$COMPOSE_FILE" build --no-cache
        ;;
        
    *)
        echo "Usage: $0 <command>"
        echo ""
        echo "Commands:"
        echo "  up         - Start test environment and run tests"
        echo "  down       - Stop and remove test environment"
        echo "  logs       - Display service logs (default: storage-conductor-tests)"
        echo "  report     - Display latest test report"
        echo "  status     - Show status of all services"
        echo "  rebuild    - Rebuild containers without cache"
        echo ""
        echo "Examples:"
        echo "  $0 up                    # Run full test suite"
        echo "  $0 logs vault-smith      # Show Vault-Smith logs"
        echo "  $0 report                # Display test report"
        exit 1
        ;;
esac
