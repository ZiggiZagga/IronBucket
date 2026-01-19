#!/bin/bash
# Single Maven Process Test - All 7 modules in ONE invocation
# This solves the Maven deadlock by avoiding process accumulation

cd /workspaces/IronBucket

echo "=== Testing Single Maven Process Approach ==="
echo "Command: mvn -pl (project list) to test all 7 modules in ONE JVM"
echo ""

# Build the -pl argument with all 7 modules
MODULES="services/Brazz-Nossel,services/Claimspindel,services/Buzzle-Vane,services/Sentinel-Gear,tools/Storage-Conductor,tools/Vault-Smith,tools/graphite-admin-shell"

echo "Modules: $MODULES"
echo ""
echo "Starting Maven (single process, all 7 modules)..."
echo "Time: $(date +%H:%M:%S)"
echo ""

mvn clean test -pl "$MODULES" -q 2>&1 | tail -50

echo ""
echo "Completed at: $(date +%H:%M:%S)"
echo "✅ If this completes, we solved the deadlock!"
