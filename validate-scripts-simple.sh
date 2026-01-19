#!/bin/bash
################################################################################
# IRONBUCKET SCRIPT VALIDATION - SIMPLE VERSION
################################################################################

PROJECT_ROOT="/workspaces/IronBucket"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║ IRONBUCKET SCRIPT VALIDATION RESULTS                          ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

CHECKS_PASS=0
CHECKS_FAIL=0

# Check 1: Foundation files
echo "✅ FOUNDATION FILES:"
for file in ".env.defaults" "lib/common.sh" "SCRIPT_STANDARDIZATION.md" "SCRIPT_MIGRATION_CHECKLIST.md"; do
    if [[ -f "$PROJECT_ROOT/scripts/$file" ]] || [[ -f "$PROJECT_ROOT/$file" ]]; then
        echo "   ✓ $file exists"
        ((CHECKS_PASS++))
    else
        echo "   ✗ $file MISSING"
        ((CHECKS_FAIL++))
    fi
done
echo ""

# Check 2: Required functions in common.sh
echo "✅ COMMON.SH FUNCTIONS:"
COMMON_FILE="$PROJECT_ROOT/scripts/lib/common.sh"
if [[ -f "$COMMON_FILE" ]]; then
    for func in "log_info" "log_success" "log_error" "error_exit" "check_service_health" "register_error_trap"; do
        if grep -q "^${func}()" "$COMMON_FILE"; then
            echo "   ✓ $func() defined"
            ((CHECKS_PASS++))
        else
            echo "   ✗ $func() NOT FOUND"
            ((CHECKS_FAIL++))
        fi
    done
fi
echo ""

# Check 3: .env.defaults variables
echo "✅ ENV VARIABLES (.env.defaults):"
ENV_FILE="$PROJECT_ROOT/scripts/.env.defaults"
if [[ -f "$ENV_FILE" ]]; then
    for var in "PROJECT_ROOT" "TEMP_DIR" "LOG_DIR" "KEYCLOAK_URL" "IS_CONTAINER" "MINIO_URL"; do
        if grep -q "$var" "$ENV_FILE"; then
            echo "   ✓ $var defined or configured"
            ((CHECKS_PASS++))
        else
            echo "   ✗ $var NOT FOUND"
            ((CHECKS_FAIL++))
        fi
    done
fi
echo ""

# Check 4: Migrated scripts source the env
echo "✅ SCRIPT MIGRATIONS:"
for script in "scripts/spinup.sh" "scripts/run-all-tests-complete.sh" "scripts/comprehensive-test-reporter.sh"; do
    SCRIPT_PATH="$PROJECT_ROOT/$script"
    if [[ -f "$SCRIPT_PATH" ]]; then
        if grep -q "source.*\.env.defaults" "$SCRIPT_PATH" && grep -q "source.*lib/common.sh" "$SCRIPT_PATH"; then
            echo "   ✓ $(basename $script) migrated"
            ((CHECKS_PASS++))
        else
            echo "   ✗ $(basename $script) NOT FULLY MIGRATED"
            ((CHECKS_FAIL++))
        fi
    else
        echo "   ? $(basename $script) not found"
    fi
done
echo ""

# Check 5: Critical bugs fixed
echo "✅ BUG FIXES:"

# Check for RUN_ROADMAP=true fix
if grep -q "RUN_ROADMAP=true" "$PROJECT_ROOT/scripts/comprehensive-test-reporter.sh"; then
    echo "   ✓ comprehensive-test-reporter.sh: RUN_ROADMAP typo FIXED"
    ((CHECKS_PASS++))
else
    echo "   ✗ comprehensive-test-reporter.sh: RUN_ROADMAP typo NOT FIXED"
    ((CHECKS_FAIL++))
fi

# Check for verify-test-pathway.py environment usage
if grep -q "os.environ" "$PROJECT_ROOT/scripts/verify-test-pathway.py"; then
    echo "   ✓ verify-test-pathway.py: Uses environment variables"
    ((CHECKS_PASS++))
else
    echo "   ✗ verify-test-pathway.py: Does NOT use environment variables"
    ((CHECKS_FAIL++))
fi

# Check for sys.exit in verify-test-pathway.py
if grep -q "sys.exit" "$PROJECT_ROOT/scripts/verify-test-pathway.py"; then
    echo "   ✓ verify-test-pathway.py: Has proper exit codes"
    ((CHECKS_PASS++))
else
    echo "   ✗ verify-test-pathway.py: Missing exit code handling"
    ((CHECKS_FAIL++))
fi

echo ""

# SUMMARY
echo "════════════════════════════════════════════════════════════════"
echo "VALIDATION SUMMARY:"
echo "  Passed: $CHECKS_PASS"
echo "  Failed: $CHECKS_FAIL"
echo ""

if [[ $CHECKS_FAIL -eq 0 ]]; then
    echo "✅ ALL CHECKS PASSED!"
    echo "════════════════════════════════════════════════════════════════"
    exit 0
else
    echo "❌ SOME CHECKS FAILED - Review above for details"
    echo "════════════════════════════════════════════════════════════════"
    exit 1
fi
