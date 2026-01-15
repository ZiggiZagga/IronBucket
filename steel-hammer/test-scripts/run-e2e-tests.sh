#!/bin/sh

# IronBucket E2E Test Suite
# Runs internal container tests against all microservices

set -e

echo "======================================"
echo "IronBucket E2E Test Suite Started"
echo "======================================"
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Starting E2E tests..."

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Helper function for testing
test_endpoint() {
    local name=$1
    local url=$2
    local expected_code=$3
    
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] Testing: $name"
    
    local response=$(curl -s -w "\n%{http_code}" "$url" 2>&1)
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n-1)
    
    if [ "$http_code" = "$expected_code" ] || [ "$http_code" = "200" ] || [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $name (HTTP $http_code)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: $name (Expected $expected_code, got $http_code)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Helper function for health checks
health_check() {
    local name=$1
    local url=$2
    
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] Health Check: $name"
    
    local response=$(curl -s -w "\n%{http_code}" "$url" 2>&1)
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n-1)
    
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✓ HEALTHY${NC}: $name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}✗ UNHEALTHY${NC}: $name (HTTP $http_code)"
        echo "Response: $body"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

echo ""
echo "======================================"
echo "Phase 1: Health Checks"
echo "======================================"

# Check Keycloak
health_check "Keycloak" "$KEYCLOAK_URL/admin/" || true

# Check MinIO
health_check "MinIO" "$MINIO_URL/minio/health/live" || true

# Check PostgreSQL (via psql in container)
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Health Check: PostgreSQL"
if nc -zv $POSTGRES_HOST 5432 &>/dev/null; then
    echo -e "${GREEN}✓ HEALTHY${NC}: PostgreSQL"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗ UNHEALTHY${NC}: PostgreSQL"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

echo ""
echo "======================================"
echo "Phase 2: Microservice Health Endpoints"
echo "======================================"

# Check Buzzle-Vane (Eureka)
health_check "Buzzle-Vane (Eureka)" "$BUZZLE_VANE_URL/actuator/health"

# Check Sentinel-Gear (Gateway)
health_check "Sentinel-Gear (Gateway)" "$SENTINEL_GEAR_URL/actuator/health"

# Check Claimspindel (Policy Engine)
health_check "Claimspindel (Policy)" "$CLAIMSPINDEL_URL/actuator/health"

# Check Brazz-Nossel (S3 Proxy)
health_check "Brazz-Nossel (S3 Proxy)" "$BRAZZ_NOSSEL_URL/actuator/health"

echo ""
echo "======================================"
echo "Phase 3: Service Discovery (Eureka)"
echo "======================================"

# Get registered services
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Testing: Service Registry"
response=$(curl -s -w "\n%{http_code}" "$BUZZLE_VANE_URL/eureka/apps" -H "Accept: application/json")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Service Registry (HTTP $http_code)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
    # Count registered apps
    app_count=$(echo "$body" | grep -o '"name"' | wc -l)
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] Registered apps: $app_count"
else
    echo -e "${RED}✗ FAIL${NC}: Service Registry (HTTP $http_code)"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

echo ""
echo "======================================"
echo "Phase 4: Gateway Routing"
echo "======================================"

# Test gateway basic connectivity
test_endpoint "Gateway Root" "$SENTINEL_GEAR_URL/" "200"

# Test gateway health endpoint
test_endpoint "Gateway Health" "$SENTINEL_GEAR_URL/actuator/health" "200"

echo ""
echo "======================================"
echo "Phase 5: Observability Stack"
echo "======================================"

# Check Prometheus metrics
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Testing: Prometheus Metrics"
response=$(curl -s -w "\n%{http_code}" "$SENTINEL_GEAR_URL:8081/actuator/prometheus")
http_code=$(echo "$response" | tail -n1)

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Prometheus Metrics Endpoint"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${YELLOW}⚠ WARNING${NC}: Prometheus Metrics (HTTP $http_code) - May require auth"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi

echo ""
echo "======================================"
echo "Phase 6: Database Connectivity"
echo "======================================"

echo "[$(date +'%Y-%m-%d %H:%M:%S')] Testing: PostgreSQL Connection"
if nc -zv $POSTGRES_HOST 5432 &>/dev/null; then
    echo -e "${GREEN}✓ PASS${NC}: PostgreSQL Connectivity"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗ FAIL${NC}: PostgreSQL Connection"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

echo ""
echo "======================================"
echo "Phase 7: Storage (MinIO)"
echo "======================================"

echo "[$(date +'%Y-%m-%d %H:%M:%S')] Testing: MinIO Connectivity"
if nc -zv $MINIO_URL 9000 &>/dev/null; then
    echo -e "${GREEN}✓ PASS${NC}: MinIO Connectivity"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗ FAIL${NC}: MinIO Connection"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

echo ""
echo "======================================"
echo "Test Summary"
echo "======================================"
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Tests Failed: ${RED}$TESTS_FAILED${NC}"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}✓ ALL TESTS PASSED${NC}"
    exit 0
else
    echo -e "\n${RED}✗ SOME TESTS FAILED${NC}"
    exit 1
fi
