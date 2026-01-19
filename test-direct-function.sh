#!/bin/bash
# Direct test of run_maven_modules function

cd /workspaces/IronBucket
source scripts/.env.defaults
source scripts/lib/common.sh

echo "=== Testing run_maven_modules directly ==="
echo "Testing with 2 fast projects..."

run_maven_modules services/Brazz-Nossel services/Claimspindel

echo ""
echo "=== FUNCTION RETURNED ==="
echo "Total tests: ${MAVEN_TOTAL_TESTS}"
echo "Modules found: ${MAVEN_FOUND_COUNT}"
echo ""
echo "Summary:"
for line in "${MAVEN_SUMMARY[@]}"; do
    echo "  $line"
done
