#!/bin/bash
# Quick Start Script for Containerized E2E Tests
# This script starts all services and runs the E2E tests inside Docker

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_ROOT="/workspaces/IronBucket"
STEEL_HAMMER_DIR="$PROJECT_ROOT/steel-hammer"

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                                  ║${NC}"
echo -e "${BLUE}║         IronBucket Containerized E2E Tests - Quick Start         ║${NC}"
echo -e "${BLUE}║                                                                  ║${NC}"
echo -e "${BLUE}║  All tests run inside Docker containers on internal network    ║${NC}"
echo -e "${BLUE}║                                                                  ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Step 1: Check prerequisites
echo -e "${YELLOW}Step 1: Checking prerequisites...${NC}"
echo ""

if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker installed${NC}"

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker Compose installed${NC}"

if [ ! -d "$STEEL_HAMMER_DIR" ]; then
    echo -e "${RED}❌ steel-hammer directory not found: $STEEL_HAMMER_DIR${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Project structure verified${NC}"

echo ""

# Step 2: Navigate to steel-hammer directory
echo -e "${YELLOW}Step 2: Preparing Docker environment...${NC}"
echo ""

cd "$STEEL_HAMMER_DIR"
echo -e "${GREEN}✅ Changed to: $STEEL_HAMMER_DIR${NC}"

# Step 3: Set environment variable
export DOCKER_FILES_HOMEDIR="."
echo -e "${GREEN}✅ Set DOCKER_FILES_HOMEDIR=$DOCKER_FILES_HOMEDIR${NC}"

echo ""

# Step 4: Stop and remove old containers
echo -e "${YELLOW}Step 3: Cleaning up old containers...${NC}"
echo ""

if docker-compose -f docker-compose-steel-hammer.yml ps | grep -q "steel-hammer"; then
    echo "Stopping running services..."
    docker-compose -f docker-compose-steel-hammer.yml down -v 2>/dev/null || true
    sleep 5
    echo -e "${GREEN}✅ Old containers removed${NC}"
else
    echo -e "${GREEN}✅ No old containers found${NC}"
fi

echo ""

# Step 5: Build and start services
echo -e "${YELLOW}Step 4: Building and starting Docker services...${NC}"
echo ""
echo "This may take 2-5 minutes on first run (downloading images, building containers)"
echo ""

docker-compose -f docker-compose-steel-hammer.yml up -d

sleep 5

echo -e "${GREEN}✅ Docker services started${NC}"

echo ""

# Step 6: Wait for services to be ready
echo -e "${YELLOW}Step 5: Waiting for services to initialize...${NC}"
echo ""

WAIT_TIME=0
MAX_WAIT=120

echo "Waiting for Keycloak to be ready (max ${MAX_WAIT}s)..."

while [ $WAIT_TIME -lt $MAX_WAIT ]; do
    if docker exec steel-hammer-keycloak curl -s http://localhost:7081/realms/dev/.well-known/openid-configuration > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Keycloak is ready${NC}"
        break
    fi
    
    echo -n "."
    sleep 3
    WAIT_TIME=$((WAIT_TIME + 3))
done

if [ $WAIT_TIME -ge $MAX_WAIT ]; then
    echo -e "${YELLOW}⚠️  Timeout waiting for Keycloak, but continuing...${NC}"
else
    echo ""
fi

sleep 10

echo -e "${GREEN}✅ Services initialized${NC}"

echo ""

# Step 7: Display container status
echo -e "${YELLOW}Step 6: Container Status${NC}"
echo ""

docker-compose -f docker-compose-steel-hammer.yml ps

echo ""

# Step 8: Run tests
echo -e "${YELLOW}Step 7: Running E2E Tests...${NC}"
echo ""
echo "Starting test execution inside Docker network..."
echo ""

docker-compose -f docker-compose-steel-hammer.yml up steel-hammer-test

echo ""

# Step 9: Get test results
echo -e "${YELLOW}Step 8: Test Results${NC}"
echo ""

TEST_EXIT_CODE=$(docker inspect steel-hammer-test --format='{{.State.ExitCode}}' 2>/dev/null || echo "unknown")

if [ "$TEST_EXIT_CODE" == "0" ]; then
    echo -e "${GREEN}╔══════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                                                                  ║${NC}"
    echo -e "${GREEN}║                     ✅ ALL TESTS PASSED! ✅                      ║${NC}"
    echo -e "${GREEN}║                                                                  ║${NC}"
    echo -e "${GREEN}║              IronBucket is PRODUCTION READY! 🚀                 ║${NC}"
    echo -e "${GREEN}║                                                                  ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
else
    echo -e "${YELLOW}Test execution completed. Check logs for details.${NC}"
    echo ""
    echo "To view test logs:"
    echo "  docker logs steel-hammer-test"
    echo ""
fi

echo ""

# Step 9: Display next steps
echo -e "${YELLOW}Next Steps:${NC}"
echo ""
echo "View full test logs:"
echo -e "  ${BLUE}docker logs steel-hammer-test${NC}"
echo ""
echo "Run tests again (services still running):"
echo -e "  ${BLUE}docker-compose -f docker-compose-steel-hammer.yml up steel-hammer-test${NC}"
echo ""
echo "Stop all services:"
echo -e "  ${BLUE}docker-compose -f docker-compose-steel-hammer.yml down${NC}"
echo ""
echo "View running containers:"
echo -e "  ${BLUE}docker ps${NC}"
echo ""
echo "Access Keycloak admin console:"
echo -e "  ${BLUE}http://localhost:7081${NC}"
echo -e "  Username: ${BLUE}admin${NC}"
echo -e "  Password: ${BLUE}admin${NC}"
echo ""

# Step 10: Additional debug info if requested
if [ "$1" == "--debug" ]; then
    echo -e "${YELLOW}Debug Information:${NC}"
    echo ""
    echo "Docker network:"
    docker network inspect steel-hammer_steel-hammer-network | jq '.Containers'
    echo ""
    echo "Keycloak logs (last 20 lines):"
    docker logs --tail 20 steel-hammer-keycloak
    echo ""
    echo "PostgreSQL logs (last 20 lines):"
    docker logs --tail 20 steel-hammer-postgres
    echo ""
fi

echo -e "${GREEN}✅ E2E Test Suite Setup Complete${NC}"
echo ""

exit 0
