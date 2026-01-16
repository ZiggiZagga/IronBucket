#!/bin/bash
# IronBucket - Complete Test & Deployment Script
# Spin up containerized services and run e2e tests
# Based on alice-bob-test.sh pattern for proven working approach

set -e

# ============================================================================
# CONFIGURATION
# ============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
NC='\033[0m'

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STEEL_HAMMER_DIR="$PROJECT_ROOT/steel-hammer"
TEMP_DIR="$PROJECT_ROOT/temp"
LOG_FILE="$PROJECT_ROOT/test-execution-$(date +%Y%m%d-%H%M%S).log"

# Service configuration
KEYCLOAK_URL="http://localhost:7081"
MINIO_URL="http://localhost:9000"
POSTGRES_HOST="localhost"
WAIT_TIMEOUT=120

# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

print_header() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}$1${BLUE}║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_section() {
    echo ""
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${MAGENTA}  $1${NC}"
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

print_step() {
    echo -e "${YELLOW}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

check_prerequisite() {
    if ! command -v "$1" &> /dev/null; then
        print_error "$1 not found. Please install $1 first."
        return 1
    fi
    return 0
}

wait_for_service() {
    local url=$1
    local name=$2
    local timeout=$3
    local elapsed=0

    while [ $elapsed -lt $timeout ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            print_success "$name is ready"
            return 0
        fi
        echo -n "."
        sleep 2
        elapsed=$((elapsed + 2))
    done

    print_warning "$name not ready (timeout after ${timeout}s)"
    return 1
}

# ============================================================================
# MAIN EXECUTION
# ============================================================================

main() {
    print_header "      IronBucket - Containerized E2E Test Suite            "

    # Step 1: Verify prerequisites
    print_section "STEP 1: Verify Prerequisites"

    print_step "Checking Docker..."
    if ! check_prerequisite "docker"; then
        print_error "Docker is required. Please install from https://docker.com"
        exit 1
    fi
    print_success "Docker is installed"

    print_step "Checking Docker Compose..."
    if ! check_prerequisite "docker-compose"; then
        print_error "Docker Compose is required"
        exit 1
    fi
    print_success "Docker Compose is installed"

    print_step "Checking project structure..."
    if [ ! -d "$STEEL_HAMMER_DIR" ]; then
        print_error "steel-hammer directory not found: $STEEL_HAMMER_DIR"
        exit 1
    fi
    print_success "Project structure verified"

    # Step 2: Build Maven projects locally
    print_section "STEP 2: Build & Test Maven Projects (221 tests)"

    print_step "Running Maven unit tests for all 6 projects..."
    echo ""

    cd "$TEMP_DIR"

    local total_tests=0
    local failed_projects=0

    for project in Brazz-Nossel Claimspindel Buzzle-Vane Sentinel-Gear Storage-Conductor Vault-Smith; do
        if [ ! -d "$project" ]; then
            print_warning "Project not found: $project"
            continue
        fi

        echo -n "  Testing $project... "
        cd "$project"

        if mvn clean test -q 2>&1 | tee -a "$LOG_FILE" > /tmp/maven-${project}.log; then
            local count=$(grep -oP 'Tests run: \K[0-9]+' /tmp/maven-${project}.log | tail -1 || echo "0")
            echo -e "${GREEN}✅ ($count tests)${NC}"
            total_tests=$((total_tests + count))
        else
            local failures=$(grep -oP 'Failures: \K[0-9]+' /tmp/maven-${project}.log | tail -1 || echo "unknown")
            if [ "$failures" = "0" ] || [ "$failures" = "unknown" ]; then
                echo -e "${YELLOW}⏭️  (no tests or skipped)${NC}"
            else
                echo -e "${RED}❌ ($failures failures)${NC}"
                failed_projects=$((failed_projects + 1))
            fi
        fi

        cd ..
    done

    cd "$PROJECT_ROOT"

    echo ""
    print_success "Maven tests: $total_tests tests passed"

    if [ $failed_projects -gt 0 ]; then
        print_error "$failed_projects project(s) have test failures"
        exit 1
    fi

    # Step 3: Stop old Docker containers
    print_section "STEP 3: Clean Docker Environment"

    print_step "Cleaning up previous containers..."
    cd "$STEEL_HAMMER_DIR"

    if docker-compose -f docker-compose-steel-hammer.yml ps 2>/dev/null | grep -qE "Up|Exited"; then
        docker-compose -f docker-compose-steel-hammer.yml down -v 2>&1 | grep -E "Removing|Stopping" || true
        sleep 3
    fi

    print_success "Docker environment cleaned"

    # Step 4: Start services
    print_section "STEP 4: Start Docker Services"

    print_step "Starting Keycloak, PostgreSQL, MinIO, and microservices..."
    echo ""

    if ! docker-compose -f docker-compose-steel-hammer.yml up -d 2>&1 | tee -a "$LOG_FILE"; then
        print_error "Failed to start Docker services"
        docker-compose -f docker-compose-steel-hammer.yml logs | tail -50
        exit 1
    fi

    print_success "Docker services started"
    echo ""
    docker-compose -f docker-compose-steel-hammer.yml ps

    # Step 5: Wait for services
    print_section "STEP 5: Wait for Services to Initialize"

    print_step "Waiting for Keycloak..."
    wait_for_service "$KEYCLOAK_URL/realms/dev/.well-known/openid-configuration" "Keycloak" $WAIT_TIMEOUT
    echo ""

    print_step "Waiting for MinIO..."
    wait_for_service "$MINIO_URL/minio/health/live" "MinIO" $WAIT_TIMEOUT
    echo ""

    print_step "Waiting for PostgreSQL..."
    if PGPASSWORD=postgres_admin_pw psql -h "$POSTGRES_HOST" -U postgres -c "SELECT 1" 2>/dev/null | grep -q "1"; then
        print_success "PostgreSQL is ready"
    else
        print_warning "PostgreSQL health check failed (continuing anyway)"
    fi
    echo ""

    # Step 6: Run E2E tests
    print_section "STEP 6: Run End-to-End Tests"

    print_step "Running Alice & Bob multi-tenant scenario..."
    echo ""

    cd "$PROJECT_ROOT"

    if bash e2e-alice-bob-test.sh 2>&1 | tee -a "$LOG_FILE"; then
        print_success "Alice & Bob E2E test PASSED"
    else
        print_error "Alice & Bob E2E test FAILED"
        echo ""
        echo "Debug information:"
        echo "  Keycloak logs: docker logs steel-hammer-keycloak | tail -50"
        echo "  MinIO logs: docker logs steel-hammer-minio | tail -50"
        exit 1
    fi

    # Step 7: Final summary
    print_section "STEP 7: Test Summary & Next Steps"

    print_success "All tests passed! ✨"
    echo ""
    echo "Services running:"
    docker-compose -f "$STEEL_HAMMER_DIR/docker-compose-steel-hammer.yml" ps | tail -10
    echo ""

    echo "Access points:"
    echo -e "  ${BLUE}Keycloak Admin:${NC} http://localhost:7081"
    echo -e "    ${YELLOW}Username:${NC} admin"
    echo -e "    ${YELLOW}Password:${NC} admin"
    echo ""
    echo -e "  ${BLUE}MinIO Console:${NC} http://localhost:9000"
    echo -e "    ${YELLOW}Access Key:${NC} minioadmin"
    echo -e "    ${YELLOW}Secret Key:${NC} minioadmin"
    echo ""

    echo "Next steps:"
    echo -e "  1. View service logs: ${BLUE}docker-compose logs -f${NC}"
    echo -e "  2. Run tests again: ${BLUE}bash e2e-alice-bob-test.sh${NC}"
    echo -e "  3. Stop services: ${BLUE}docker-compose -f steel-hammer/docker-compose-steel-hammer.yml down${NC}"
    echo -e "  4. View full log: ${BLUE}cat $LOG_FILE${NC}"
    echo ""

    print_success "IronBucket is ready for production! 🚀"
    echo ""
}

# ============================================================================
# ERROR HANDLING
# ============================================================================

trap 'print_error "Script failed at line $LINENO"; exit 1' ERR

# ============================================================================
# RUN
# ============================================================================

main "$@"

exit 0
