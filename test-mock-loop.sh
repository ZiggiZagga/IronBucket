#!/bin/bash
# Test if the loop continues with mock Maven

source scripts/.env.defaults
source scripts/lib/common.sh

# Mock Maven that returns immediately
mock_maven() {
    echo "Mock Maven for module: $1"
    sleep 0.5
    echo "Tests run: 10, Failures: 0, Errors: 0, Skipped: 0"
    echo "BUILD SUCCESS"
    return 0
}

echo "Testing loop with mock Maven..."

MAVEN_TOTAL_TESTS=0
MAVEN_TOTAL_PASSED=0
MAVEN_TOTAL_FAILED=0
MAVEN_FOUND_COUNT=0
declare -a MAVEN_SUMMARY=()

projects=(services/Brazz-Nossel services/Claimspindel services/Buzzle-Vane services/Sentinel-Gear tools/Storage-Conductor tools/Vault-Smith tools/graphite-admin-shell)

cd /workspaces/IronBucket

for module in "${projects[@]}"; do
    if [[ ! -d "$module" ]]; then
        echo "SKIP: Module not found: $module"
        continue
    fi
    
    MAVEN_FOUND_COUNT=$((MAVEN_FOUND_COUNT + 1))
    echo ""
    echo "=========================================="
    echo "Testing module $MAVEN_FOUND_COUNT/${#projects[@]}: $module"
    echo "=========================================="
    
    safe_name=${module//\//-}
    maven_log="/tmp/test-mock-${safe_name}.log"
    
    # Mock Maven execution
    set +e
    timeout 10 bash -c "mock_maven '$module'" > "$maven_log" 2>&1
    mvn_exit=$?
    set -e
    
    echo "Exit code: $mvn_exit"
    
    if [ $mvn_exit -eq 0 ]; then
        echo "✅ SUCCESS: $module"
        MAVEN_SUMMARY+=("✅ ${module}: mock test")
        MAVEN_TOTAL_TESTS=$((MAVEN_TOTAL_TESTS + 10))
        MAVEN_TOTAL_PASSED=$((MAVEN_TOTAL_PASSED + 10))
    else
        echo "❌ ERROR: $module (exit: $mvn_exit)"
        MAVEN_SUMMARY+=("❌ ${module}: mock failed")
        MAVEN_TOTAL_FAILED=$((MAVEN_TOTAL_FAILED + 1))
    fi
    
    echo "DEBUG: Finished $module, continuing..."
done

echo ""
echo "=========================================="
echo "LOOP COMPLETED"
echo "=========================================="
echo "Modules tested: $MAVEN_FOUND_COUNT / ${#projects[@]}"
echo "Total tests: $MAVEN_TOTAL_TESTS"
echo "Summary:"
for line in "${MAVEN_SUMMARY[@]}"; do
    echo "  $line"
done
