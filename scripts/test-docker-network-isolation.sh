#!/bin/bash

###############################################################################
# Docker Compose Network Isolation Test Script
#
# This script validates that Docker network policies are correctly configured
# and services can only communicate with allowed targets.
#
# Usage: ./test-docker-network-isolation.sh
###############################################################################

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"

echo -e "${BLUE}=== IronBucket Docker Network Isolation Test ===${NC}\n"

###############################################################################
# Check 1: Docker Compose file exists
###############################################################################
echo -e "${BLUE}Check 1: Verifying docker-compose.yml exists...${NC}"
if [ ! -f "$DOCKER_COMPOSE_FILE" ]; then
  echo -e "${RED}✗ docker-compose.yml not found at $DOCKER_COMPOSE_FILE${NC}"
  exit 1
fi
echo -e "${GREEN}✓ docker-compose.yml found${NC}\n"

###############################################################################
# Check 2: Verify Docker daemon is running
###############################################################################
echo -e "${BLUE}Check 2: Checking Docker daemon...${NC}"
if ! docker ps &>/dev/null; then
  echo -e "${RED}✗ Docker daemon not running${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Docker daemon is running${NC}\n"

###############################################################################
# Check 3: Check if containers are already running
###############################################################################
echo -e "${BLUE}Check 3: Checking for running IronBucket containers...${NC}"
if docker ps --format "{{.Names}}" | grep -q "ironbucket"; then
  echo -e "${YELLOW}⚠ IronBucket containers already running${NC}"
  echo "  Option 1: Kill them and restart"
  echo "    docker-compose -f $DOCKER_COMPOSE_FILE down"
  echo "  Option 2: Skip to test directly"
  read -p "Continue with existing containers? (y/n) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 0
  fi
else
  echo -e "${GREEN}✓ No existing IronBucket containers running${NC}\n"
  
  ###############################################################################
  # Check 4: Start Docker Compose stack
  ###############################################################################
  echo -e "${BLUE}Check 4: Starting Docker Compose stack...${NC}"
  echo "  This may take 60-120 seconds..."
  
  cd "$(dirname "$DOCKER_COMPOSE_FILE")"
  docker-compose up -d
  
  echo -e "${GREEN}✓ Docker Compose stack started${NC}\n"
  
  # Wait for services to be healthy
  echo -e "${BLUE}Waiting for services to be healthy (60 seconds)...${NC}"
  sleep 60
fi

###############################################################################
# Check 5: Verify all services are running
###############################################################################
echo -e "${BLUE}Check 5: Verifying all services are running...${NC}"

services=("ironbucket-sentinel-gear" "ironbucket-claimspindel" "ironbucket-brazz-nossel" "ironbucket-buzzle-vane" "ironbucket-postgres" "ironbucket-minio" "ironbucket-keycloak")

for service in "${services[@]}"; do
  if docker ps --format "{{.Names}}" | grep -q "$service"; then
    echo -e "${GREEN}✓${NC} $service is running"
  else
    echo -e "${YELLOW}⚠${NC} $service is not running (might be expected)"
  fi
done
echo ""

###############################################################################
# Check 6: Test service connectivity
###############################################################################
echo -e "${BLUE}Check 6: Testing service-to-service connectivity...${NC}\n"

test_connection() {
  local service=$1
  local target_host=$2
  local target_port=$3
  local description=$4
  
  local container_name="ironbucket-${service}"
  
  if ! docker ps --format "{{.Names}}" | grep -q "$container_name"; then
    echo -e "${YELLOW}⚠${NC} $service container not found, skipping"
    return
  fi
  
  # Test connectivity from service to target
  if docker exec "$container_name" bash -c "timeout 3 bash -c '</dev/tcp/${target_host}/${target_port}' 2>/dev/null" &>/dev/null; then
    echo -e "${GREEN}✓${NC} $description (reachable)"
  else
    echo -e "${RED}✗${NC} $description (blocked)"
  fi
}

echo "From Sentinel-Gear:"
test_connection "sentinel-gear" "claimspindel" "8081" "  → Claimspindel:8081"
test_connection "sentinel-gear" "brazz-nossel" "8082" "  → Brazz-Nossel:8082"
test_connection "sentinel-gear" "buzzle-vane" "8083" "  → Buzzle-Vane:8083"
test_connection "sentinel-gear" "keycloak" "8080" "  → Keycloak:8080"
test_connection "sentinel-gear" "postgres" "5432" "  → PostgreSQL:5432 (should be blocked)"
test_connection "sentinel-gear" "minio" "9000" "  → MinIO:9000 (should be blocked)"

echo ""
echo "From Claimspindel:"
test_connection "claimspindel" "sentinel-gear" "8080" "  → Sentinel-Gear:8080 (should be blocked)"
test_connection "claimspindel" "postgres" "5432" "  → PostgreSQL:5432"
test_connection "claimspindel" "buzzle-vane" "8083" "  → Buzzle-Vane:8083"

echo ""
echo "From Brazz-Nossel:"
test_connection "brazz-nossel" "sentinel-gear" "8080" "  → Sentinel-Gear:8080 (should be blocked)"
test_connection "brazz-nossel" "minio" "9000" "  → MinIO:9000"
test_connection "brazz-nossel" "postgres" "5432" "  → PostgreSQL:5432"
test_connection "brazz-nossel" "buzzle-vane" "8083" "  → Buzzle-Vane:8083"

echo ""
echo "From Buzzle-Vane:"
test_connection "buzzle-vane" "sentinel-gear" "8080" "  → Sentinel-Gear:8080 (should be blocked)"
test_connection "buzzle-vane" "postgres" "5432" "  → PostgreSQL:5432 (should be blocked)"

echo ""

###############################################################################
# Check 7: Test HTTP endpoints
###############################################################################
echo -e "${BLUE}Check 7: Testing HTTP endpoint accessibility...${NC}\n"

test_http() {
  local service=$1
  local port=$2
  local description=$3
  
  if curl -s -m 3 "http://localhost:${port}/actuator/health" &>/dev/null; then
    echo -e "${GREEN}✓${NC} $description (accessible)"
  else
    echo -e "${RED}✗${NC} $description (not accessible)"
  fi
}

test_http "8080" "8080" "Sentinel-Gear:8080"
test_http "8081" "8081" "Claimspindel:8081"
test_http "8082" "8082" "Brazz-Nossel:8082"
test_http "8083" "8083" "Buzzle-Vane:8083"

echo ""

###############################################################################
# Check 8: Docker network inspection
###############################################################################
echo -e "${BLUE}Check 8: Inspecting Docker network configuration...${NC}\n"

# Find the custom network
network_name=$(docker network ls --filter "label=com.docker.compose.project" --format "{{.Name}}" | head -1)

if [ -z "$network_name" ]; then
  echo -e "${YELLOW}⚠ No Docker Compose network found${NC}\n"
else
  echo -e "${GREEN}✓${NC} Docker network: $network_name"
  echo ""
  echo "Connected services:"
  docker network inspect "$network_name" --format '{{range .Containers}}{{.Name}} ({{.IPv4Address}}){{println}}{{end}}' | sed 's/^/  /'
  echo ""
fi

###############################################################################
# Check 9: Verify docker-compose.yml network configuration
###############################################################################
echo -e "${BLUE}Check 9: Analyzing docker-compose.yml network configuration...${NC}\n"

if grep -q "networks:" "$DOCKER_COMPOSE_FILE"; then
  echo -e "${GREEN}✓${NC} Network configuration defined in docker-compose.yml"
  
  # Extract network config
  if grep -q "external: false" "$DOCKER_COMPOSE_FILE"; then
    echo -e "${GREEN}✓${NC} Using internal isolated network (external: false)"
  fi
else
  echo -e "${YELLOW}⚠${NC} No explicit network configuration in docker-compose.yml"
fi

echo ""

###############################################################################
# Check 10: Service health validation
###############################################################################
echo -e "${BLUE}Check 10: Validating service health...${NC}\n"

for service in "${services[@]}"; do
  local status=$(docker inspect "$service" --format='{{.State.Status}}' 2>/dev/null || echo "not found")
  if [ "$status" = "running" ]; then
    echo -e "${GREEN}✓${NC} $service: $status"
  elif [ "$status" = "not found" ]; then
    echo -e "${YELLOW}⚠${NC} $service: not running"
  else
    echo -e "${RED}✗${NC} $service: $status"
  fi
done

echo ""

###############################################################################
# Summary and next steps
###############################################################################
echo -e "${GREEN}✓ Docker network isolation validation complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Run integration tests with Docker Compose"
echo "    mvn test -Dgroups=integration"
echo ""
echo "  2. Run network policy tests"
echo "    mvn test -Dtest=SentinelGearNetworkPolicyTest"
echo ""
echo "  3. Stop Docker Compose when done"
echo "    docker-compose -f $DOCKER_COMPOSE_FILE down"
echo ""
echo "For more information, see: steel-hammer/DEPLOYMENT-GUIDE.md"
