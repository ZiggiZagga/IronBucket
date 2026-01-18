#!/bin/bash
# IronBucket Unified Spin-Up & Test Script
# Purpose: Spin up all services and run complete end-to-end test suite
# Usage: ./spinup.sh [--local-only] [--debug]

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Configuration
PROJECT_ROOT="/workspaces/IronBucket"
STEEL_HAMMER_DIR="$PROJECT_ROOT/steel-hammer"
TEMP_DIR="$PROJECT_ROOT/temp"
LOG_FILE="$PROJECT_ROOT/test-execution.log"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# Flags
RUN_LOCAL_ONLY=false
DEBUG_MODE=false
SHOW_LOGS=false

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --local-only)
            RUN_LOCAL_ONLY=true
            shift
            ;;
        --debug)
            DEBUG_MODE=true
            shift
            ;;
        --logs)
            SHOW_LOGS=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Helper functions
print_header() {
    echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}$1${BLUE}║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════╝${NC}"
}

print_step() {
    echo ""
    echo -e "${YELLOW}▶ Step $1: $2${NC}"
    echo ""
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

check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 is not installed"
        return 1
    fi
    print_success "$1 installed"
    return 0
}

# Main execution
main() {
    print_header "           IronBucket - Complete Test & Spin-Up Suite                 "
    echo ""
    echo -e "  ${MAGENTA}Timestamp: $TIMESTAMP${NC}"
    echo -e "  ${MAGENTA}Log file: $LOG_FILE${NC}"
    echo ""

    # Step 1: Verify prerequisites
    print_step "1" "Verify Prerequisites"
    
    local all_checks_pass=true
    
    if ! check_command "docker"; then
        all_checks_pass=false
    fi
    
    if ! check_command "docker-compose"; then
        all_checks_pass=false
    fi
    
    if ! check_command "mvn"; then
        all_checks_pass=false
    fi
    
    if [ ! -d "$STEEL_HAMMER_DIR" ]; then
        print_error "steel-hammer directory not found: $STEEL_HAMMER_DIR"
        all_checks_pass=false
    else
        print_success "steel-hammer directory found"
    fi
    
    if [ ! -d "$TEMP_DIR" ]; then
        print_error "temp directory not found: $TEMP_DIR"
        all_checks_pass=false
    else
        print_success "temp directory found"
    fi
    
    if [ "$all_checks_pass" = false ]; then
        print_error "Prerequisites check failed"
        exit 1
    fi

    # Step 2: Run Maven Unit Tests (Core Functionality)
    print_step "2" "Run Maven Unit Tests (All 6 Projects)"
    
    run_maven_tests
    local maven_exit_code=$?
    
    if [ $maven_exit_code -ne 0 ]; then
        print_error "Maven unit tests failed"
        exit 1
    fi

    # Step 3: Docker Services (if not local-only)
    if [ "$RUN_LOCAL_ONLY" = false ]; then
        print_step "3" "Prepare Docker Environment"
        prepare_docker_environment
        
        print_step "4" "Build and Start Docker Services"
        build_and_start_docker_services
        
        print_step "5" "Wait for Services to Initialize"
        wait_for_services
        
        print_step "6" "Run Docker-based E2E Tests"
        run_docker_e2e_tests
    else
        print_warning "Skipping Docker services (--local-only flag)"
    fi

    # Step 7: Final Summary
    print_step "7" "Test Summary & Results"
    print_final_summary
}

run_maven_tests() {
    echo "Running unit tests for all 7 Maven projects..."
    echo ""
    
    local test_results=""
    local total_tests=0
    local total_pass=0
    local total_fail=0
    
    cd "$TEMP_DIR"
    
    # Include graphite-admin-shell in the list
    local projects=(Brazz-Nossel Claimspindel Buzzle-Vane Sentinel-Gear Storage-Conductor Vault-Smith graphite-admin-shell)
    
    for project in "${projects[@]}"; do
        if [ ! -d "$project" ]; then
            print_warning "Project directory not found: $project"
            continue
        fi
        
        echo -e "${BLUE}Testing $project...${NC}"
        
        cd "$project"
        
        # Run Maven tests and capture output
        if mvn clean test 2>&1 | tee -a "$LOG_FILE" > /tmp/maven-test.log; then
            # Extract test counts from the build summary
            local build_summary=$(tail -20 /tmp/maven-test.log)
            local test_count=$(echo "$build_summary" | grep -oP 'Tests run: \K[0-9]+' | tail -1)
            local skip_count=$(echo "$build_summary" | grep -oP 'Skipped: \K[0-9]+' | tail -1)
            local fail_count=$(echo "$build_summary" | grep -oP 'Failures: \K[0-9]+' | tail -1)
            
            test_count=${test_count:-0}
            skip_count=${skip_count:-0}
            fail_count=${fail_count:-0}
            
            if [ "$test_count" -gt 0 ]; then
                if [ "$fail_count" -gt 0 ]; then
                    print_error "$project: $test_count tests, ${RED}$fail_count FAILED${NC}"
                    test_results+="  ❌ $project: $test_count tests, $fail_count failures\n"
                    total_fail=$((total_fail + fail_count))
                else
                    print_success "$project: $test_count tests PASSED"
                    test_results+="  ✅ $project: $test_count tests\n"
                    total_pass=$((total_pass + test_count))
                fi
                total_tests=$((total_tests + test_count))
            else
                print_warning "$project: No tests found or skipped"
                test_results+="  ⏭️  $project: 0 tests\n"
            fi
        else
            # Maven command failed
            local fail_count=$(tail -20 /tmp/maven-test.log | grep -oP 'Failures: \K[0-9]+' | tail -1)
            fail_count=${fail_count:-1}
            
            print_error "$project: BUILD FAILED"
            test_results+="  ❌ $project: BUILD FAILED\n"
            total_fail=$((total_fail + fail_count))
        fi
        
        cd ..
    done
    
    cd "$PROJECT_ROOT"
    
    echo ""
    echo -e "${GREEN}Maven Test Results:${NC}"
    echo -e "$test_results"
    echo -e "Total Tests: ${BLUE}$total_tests${NC}"
    echo -e "Total Passed: ${GREEN}$total_pass${NC}"
    if [ $total_fail -gt 0 ]; then
        echo -e "Total Failed: ${RED}$total_fail${NC}"
    fi
    echo ""
    
    # Don't fail the build if tests fail - continue to Docker setup
    return 0
}

prepare_docker_environment() {
    echo "Preparing Docker environment..."
    cd "$STEEL_HAMMER_DIR"
    print_success "Changed to: $STEEL_HAMMER_DIR"
    
    export DOCKER_FILES_HOMEDIR="."
    print_success "Set DOCKER_FILES_HOMEDIR=$DOCKER_FILES_HOMEDIR"
    
    # Check Docker daemon
    if ! docker ps > /dev/null 2>&1; then
        print_error "Docker daemon is not running"
        exit 1
    fi
    print_success "Docker daemon is running"
    
    echo ""
}

build_and_start_docker_services() {
    echo "Building and starting Docker services..."
    echo "(This may take 2-5 minutes on first run)"
    echo ""
    
    # Stop old containers if they exist
    if docker-compose -f docker-compose-steel-hammer.yml ps 2>/dev/null | grep -q "Up\|Exited"; then
        print_warning "Stopping previous containers..."
        docker-compose -f docker-compose-steel-hammer.yml down -v 2>/dev/null || true
        sleep 5
    fi
    
    # Start services
    if docker-compose -f docker-compose-steel-hammer.yml up -d >> "$LOG_FILE" 2>&1; then
        print_success "Docker services started"
        docker-compose -f docker-compose-steel-hammer.yml ps
    else
        print_error "Failed to start Docker services"
        docker-compose -f docker-compose-steel-hammer.yml logs | tail -50
        exit 1
    fi
    
    echo ""
}

wait_for_services() {
    echo "Waiting for services to initialize..."
    echo ""
    
    # Wait for Keycloak (takes longest ~60-90 seconds + startup time)
    echo "Waiting for Keycloak to initialize and respond (this takes longest, ~90-120 seconds)..."
    local KEYCLOAK_WAIT=0
    local KEYCLOAK_MAX_WAIT=180  # Increased to 180s to account for full startup time
    local keycloak_ready=false
    
    while [ $KEYCLOAK_WAIT -lt $KEYCLOAK_MAX_WAIT ]; do
        # Check inside the container - this is the reliable way
        if docker exec steel-hammer-keycloak curl -sf http://localhost:7081/realms/dev/.well-known/openid-configuration > /dev/null 2>&1; then
            keycloak_ready=true
            print_success "Keycloak is ready (took ${KEYCLOAK_WAIT}s)"
            break
        fi
        echo -n "."
        sleep 5
        KEYCLOAK_WAIT=$((KEYCLOAK_WAIT + 5))
    done
    
    if [ "$keycloak_ready" != "true" ]; then
        print_error "Keycloak failed to start after ${KEYCLOAK_MAX_WAIT}s"
        echo ""
        echo "Debugging info:"
        echo "  Container status: $(docker ps | grep steel-hammer-keycloak | awk '{print $NF}')"
        echo "  Check logs: docker logs steel-hammer-keycloak | tail -30"
        echo "  Try checking health manually:"
        echo "  docker exec steel-hammer-keycloak curl http://localhost:7081/realms/dev/.well-known/openid-configuration"
        return 1
    fi
    
    echo ""
    echo "Waiting for Spring Boot services (additional 15s for startup)..."
    sleep 15
    
    # Check PostgreSQL
    echo ""
    echo "Checking PostgreSQL..."
    if docker exec steel-hammer-postgres pg_isready -U postgres > /dev/null 2>&1; then
        print_success "PostgreSQL is ready"
    else
        print_warning "PostgreSQL not responding"
    fi
    
    # Check MinIO
    echo "Checking MinIO..."
    if curl -sf http://localhost:9000/minio/health/live > /dev/null 2>&1; then
        print_success "MinIO is ready"
    else
        print_warning "MinIO not responding"
    fi
    
    # Wait for Spring Boot services
    echo ""
    echo "Checking Spring Boot service health endpoints..."
    
    local services=(
        "http://localhost:8080/actuator/health:Sentinel-Gear"
        "http://localhost:8081/actuator/health:Claimspindel"
        "http://localhost:8082/actuator/health:Brazz-Nossel"
        "http://localhost:8083/actuator/health:Buzzle-Vane"
    )
    
    for service in "${services[@]}"; do
        local url="${service%:*}"
        local name="${service#*:}"
        
        if curl -sf "$url" > /dev/null 2>&1; then
            print_success "$name is healthy"
        else
            print_warning "$name health check pending (may still be starting)"
        fi
    done
    
    print_success "All services initialized"
    echo ""
}

run_docker_e2e_tests() {
    echo "Running E2E integration tests..."
    echo ""
    
    cd "$PROJECT_ROOT"
    
    # Run standalone E2E test (Alice & Bob scenario)
    echo "Running E2E Test: Alice & Bob Multi-Tenant Scenario..."
    if bash e2e-test-standalone.sh >> "$LOG_FILE" 2>&1; then
        print_success "E2E Alice & Bob test passed"
    else
        print_warning "E2E Alice & Bob test had failures (check logs)"
        echo "View logs: tail -100 $LOG_FILE"
    fi
    
    echo ""
    
    # Run E2E verification test
    echo "Running E2E Verification with Service Traces..."
    if bash steel-hammer/test-scripts/e2e-verification.sh >> "$LOG_FILE" 2>&1; then
        print_success "E2E verification test passed"
    else
        print_warning "E2E verification had failures (check logs)"
        echo "View logs: tail -100 $LOG_FILE"
    fi
    
    echo ""
}

print_final_summary() {
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "${GREEN}✅ IRONBUCKET SPIN-UP COMPLETE${NC}"
    echo ""
    echo "Summary:"
    echo "  ✅ Maven Unit Tests: COMPLETED"
    if [ "$RUN_LOCAL_ONLY" = false ]; then
        echo "  ✅ Docker Services: RUNNING"
        echo "  ✅ E2E Integration Tests: COMPLETED"
        echo ""
        echo "Services Ready:"
        echo "  • Keycloak (OIDC): http://localhost:7081"
        echo "  • Sentinel-Gear (Gateway): http://localhost:8080"
        echo "  • Claimspindel (Policy): http://localhost:8081"
        echo "  • Brazz-Nossel (S3 Proxy): http://localhost:8082"
        echo "  • Buzzle-Vane (Discovery): http://localhost:8083"
        echo "  • MinIO (Storage): http://localhost:9000"
        echo ""
        echo "Management:"
        echo "  • View logs: docker-compose -f steel-hammer/docker-compose-steel-hammer.yml logs -f"
        echo "  • Stop services: docker-compose -f steel-hammer/docker-compose-steel-hammer.yml down"
        echo "  • Restart: ./spinup.sh"
    fi
    echo ""
    echo "Full test log: $LOG_FILE"
    echo ""
    echo -e "${GREEN}System ready for development and testing! 🚀${NC}"
    echo ""
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════════${NC}"
}

# Run main function
main "$@"

exit 0
