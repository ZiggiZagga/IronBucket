#!/bin/bash
set -uo pipefail

projects=(services/Brazz-Nossel services/Claimspindel)

for module in "${projects[@]}"; do
    echo ""
    echo "========== START: $module =========="
    echo "Time: $(date +%H:%M:%S)"
    
    cd /workspaces/IronBucket/$module
    timeout 30 mvn clean test -q 2>&1 | tail -3
    EXIT_CODE=$?
    
    echo "Exit code: $EXIT_CODE"
    echo "Time: $(date +%H:%M:%S)"
    echo "========== DONE: $module =========="
    
    sleep 1
done

echo ""
echo "LOOP COMPLETED"
