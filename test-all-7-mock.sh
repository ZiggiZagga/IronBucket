#!/bin/bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scripts/.env.defaults"
source "$SCRIPT_DIR/scripts/lib/common.sh"

# Mock mvn to make it fast
mvn() {
    echo "[MOCK] Running mvn in $(pwd)"
    echo "[INFO] BUILD SUCCESS"
    echo "[INFO] Tests run: 5, Failures: 0, Errors: 0"
    return 0
}
export -f mvn

echo "Testing ALL 7 modules with mock Maven..."
projects=(services/Brazz-Nossel services/Claimspindel services/Buzzle-Vane services/Sentinel-Gear tools/Storage-Conductor tools/Vault-Smith tools/graphite-admin-shell)

run_maven_modules "${projects[@]}"

echo ""
echo "Result:"
echo "  Modules tested: $MAVEN_FOUND_COUNT / ${#projects[@]}"
echo "  MAVEN_SUMMARY:"
for line in "${MAVEN_SUMMARY[@]}"; do
    echo "    $line"
done
