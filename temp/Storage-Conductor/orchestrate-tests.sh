#!/bin/bash
# Storage-Conductor: Complete Test Orchestration
# Starts all services, runs integration tests, persists results to MinIO
# Usage: ./orchestrate-tests.sh [up|down|logs|report]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-tests.yml"

# Color codes
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

case "${1:-up}" in
    up)
        echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${BLUE}║    STORAGE-CONDUCTOR: COMPLETE TEST ORCHESTRATION              ║${NC}"
        echo -e "${BLUE}║  Running Integration Tests with MinIO Results Persistence      ║${NC}"
        echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
        echo ""
        
        echo "Step 1: Starting all services with docker-compose..."
        docker-compose -f "$COMPOSE_FILE" up -d --wait
        
        echo ""
        echo "Step 2: Waiting for services to stabilize..."
        sleep 5
        
        echo ""
        echo "Step 3: Executing integration tests inside container..."
        docker-compose -f "$COMPOSE_FILE" exec -T storage-conductor-tests bash /docker-entrypoint.sh
        
        echo ""
        echo -e "${GREEN}✅ Integration tests complete!${NC}"
        echo ""
        echo "Step 4: Retrieving test results from MinIO..."
        echo ""
        
        # List results in MinIO
        docker-compose -f "$COMPOSE_FILE" exec -T storage-conductor-tests \
            aws s3 ls s3://test-results/ --endpoint-url http://minio:9000 --region us-east-1
        
        echo ""
        echo -e "${GREEN}✅ Test execution complete!${NC}"
        echo ""
        echo "To view test results:"
        echo "  ./orchestrate-tests.sh report"
        echo ""
        echo "To view service logs:"
        echo "  ./orchestrate-tests.sh logs vault-smith"
        echo "  ./orchestrate-tests.sh logs sentinel-gear"
        echo ""
        echo "To stop all services:"
        echo "  ./orchestrate-tests.sh down"
        ;;
        
    down)
        echo "Stopping all services..."
        docker-compose -f "$COMPOSE_FILE" down -v
        echo -e "${GREEN}✅ All services stopped and cleaned up${NC}"
        ;;
        
    logs)
        SERVICE="${2:-storage-conductor-tests}"
        echo "Showing logs for: $SERVICE"
        docker-compose -f "$COMPOSE_FILE" logs -f "$SERVICE"
        ;;
        
    report)
        echo "Retrieving test results from MinIO..."
        
        # Get latest test results
        docker-compose -f "$COMPOSE_FILE" exec -T storage-conductor-tests \
            bash -c 'aws s3 ls s3://test-results/ --endpoint-url http://minio:9000 --region us-east-1 --recursive | tail -5'
        
        echo ""
        echo "To download results, use:"
        echo "  aws s3 cp s3://test-results/ ./test-results --endpoint-url http://minio:9000 --recursive"
        ;;
        
    status)
        echo "Service Status:"
        docker-compose -f "$COMPOSE_FILE" ps
        ;;
        
    *)
        echo "Usage: $0 {up|down|logs|report|status}"
        echo ""
        echo "Commands:"
        echo "  up       - Start all services and run integration tests"
        echo "  down     - Stop all services and clean up"
        echo "  logs     - View service logs (usage: $0 logs <service>)"
        echo "  report   - Show test results from MinIO"
        echo "  status   - Show service status"
        exit 1
        ;;
esac
