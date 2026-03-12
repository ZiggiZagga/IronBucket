#!/bin/bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scripts/.env.defaults"
source "$SCRIPT_DIR/scripts/lib/common.sh"

echo "Testing ONLY graphite-admin-shell..."
run_maven_modules tools/graphite-admin-shell

echo ""
echo "Result:"
echo "  MAVEN_FOUND_COUNT: $MAVEN_FOUND_COUNT"
echo "  MAVEN_TOTAL_TESTS: $MAVEN_TOTAL_TESTS"
echo "  MAVEN_TOTAL_PASSED: $MAVEN_TOTAL_PASSED"
echo "  MAVEN_TOTAL_FAILED: $MAVEN_TOTAL_FAILED"
