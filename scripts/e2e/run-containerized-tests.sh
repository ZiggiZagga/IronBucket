#!/bin/bash
# Quick Start Script for Containerized E2E Tests
# This script starts all services and runs the E2E tests inside Docker

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$ROOT_DIR/scripts/.env.defaults"
source "$ROOT_DIR/scripts/lib/common.sh"
register_error_trap
ensure_cert_artifacts

print_header "        IronBucket Containerized E2E Tests - Quick Start         "

print_step "Step 1: Checking prerequisites..."
require_docker
require_directory "$STEEL_HAMMER_DIR"
print_success "Project structure verified: $STEEL_HAMMER_DIR"

echo ""

# Step 2: Navigate to steel-hammer directory
print_step "Step 2: Preparing Docker environment..."

d="${STEEL_HAMMER_DIR}"
cd "$d"
print_success "Changed to: $d"

# Step 3: Set environment variable
export DOCKER_FILES_HOMEDIR="."
print_success "Set DOCKER_FILES_HOMEDIR=$DOCKER_FILES_HOMEDIR"

echo ""

# Step 4: Stop and remove old containers
print_step "Step 3: Cleaning up old containers..."

docker_ps_output=$(docker-compose -f docker-compose-steel-hammer.yml ps || true)
if echo "$docker_ps_output" | grep -q "steel-hammer"; then
    echo "Stopping running services..."
    docker-compose -f docker-compose-steel-hammer.yml down -v 2>/dev/null || true
    sleep 5
    print_success "Old containers removed"
else
    print_success "No old containers found"
fi

echo ""

# Step 5: Build and start services
print_step "Step 4: Building and starting Docker services..."
echo "This may take 2-5 minutes on first run (downloading images, building containers)"
echo ""

docker-compose -f docker-compose-steel-hammer.yml up -d

sleep 5

print_success "Docker services started"

echo ""

# Step 6: Wait for services to be ready
print_step "Step 5: Waiting for services to initialize..."

timeout=120
if ! wait_for_service "${KEYCLOAK_URL}/realms/dev/.well-known/openid-configuration" "Keycloak" "$timeout" 3; then
    print_warning "Keycloak did not report ready within ${timeout}s (continuing)"
fi

sleep 10

print_success "Services initialized"

echo ""

# Step 7: Display container status
print_step "Step 6: Container Status"

docker-compose -f docker-compose-steel-hammer.yml ps

echo ""

# Step 8: Run tests
print_step "Step 7: Running E2E Tests..."
echo ""
echo "Starting test execution inside Docker network..."
echo ""

docker-compose -f docker-compose-steel-hammer.yml up steel-hammer-test

echo ""

# Step 9: Get test results
print_step "Step 8: Test Results"
echo ""

TEST_EXIT_CODE=$(docker inspect steel-hammer-test --format='{{.State.ExitCode}}' 2>/dev/null || echo "unknown")

if [ "$TEST_EXIT_CODE" == "0" ]; then
    print_success "ALL TESTS PASSED in steel-hammer-test"
else
    print_warning "Test execution completed. Check logs for details."
    echo ""
    echo "To view test logs:"
    echo "  docker logs steel-hammer-test"
    echo ""
fi

echo ""

# Step 9: Display next steps
print_step "Next Steps"
echo ""
echo "View full test logs:"
echo "  docker logs steel-hammer-test"
echo ""
echo "Run tests again (services still running):"
echo "  docker-compose -f docker-compose-steel-hammer.yml up steel-hammer-test"
echo ""
echo "Stop all services:"
echo "  docker-compose -f docker-compose-steel-hammer.yml down"
echo ""
echo "View running containers:"
echo "  docker ps"
echo ""
echo "Access Keycloak admin console:"
echo "  http://localhost:7081"
echo "  Username: admin"
echo "  Password: admin"
echo ""

# Step 10: Additional debug info if requested
if [ "$1" == "--debug" ]; then
    print_step "Debug Information"
    echo ""
    echo "Docker network:"
    docker network inspect steel-hammer_steel-hammer-network | jq '.Containers' || true
    echo ""
    echo "Keycloak logs (last 20 lines):"
    docker logs --tail 20 steel-hammer-keycloak || true
    echo ""
    echo "PostgreSQL logs (last 20 lines):"
    docker logs --tail 20 steel-hammer-postgres || true
    echo ""
fi

print_success "E2E Test Suite Setup Complete"
echo ""

exit 0
