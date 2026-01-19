# IronBucket Scripts Architecture Guide

**For:** Future script developers and maintainers  
**Updated:** 2026-01-19  
**Scope:** Bash and Python automation scripts for testing, deployment, and CI/CD

---

## Overview

IronBucket scripts follow a **unified helper library pattern** to eliminate duplication and ensure consistency. All scripts source shared libraries for colors, logging, Docker operations, and Maven automation.

```
┌─────────────────────────────────────────────────────────┐
│  User Scripts (spinup.sh, run-all-tests-complete.sh)   │
└──────────────────┬──────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
   ┌────▼────┐          ┌────▼──────┐
   │   Bash  │          │  Python   │
   └────┬────┘          └────┬──────┘
        │                     │
    Sourced:              Imported:
 • .env.defaults        • python_utils.py
 • lib/common.sh        
        │                     │
        └──────────┬──────────┘
                   │
        ┌──────────▼──────────┐
        │  Shared Utilities   │
        │ (Colors, Logging,   │
        │  Docker, Maven)     │
        └─────────────────────┘
```

---

## Bash Scripts Architecture

### Entry Point: `.env.defaults`

**Every bash script must source this first:**

```bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/.env.defaults"
```

**Provides:**
- Color codes: `$RED`, `$GREEN`, `$YELLOW`, `$BLUE`, `$MAGENTA`, `$CYAN`, `$GRAY`, `$BOLD`, `$NC`
- Path variables: `$PROJECT_ROOT`, `$TEMP_DIR`, `$LOG_DIR`, `$REPORTS_DIR`, `$STEEL_HAMMER_DIR`, etc.
- Service URLs (container-aware): `$KEYCLOAK_URL`, `$MINIO_URL`, `$POSTGRES_HOST`, etc.
- Service credentials: `$MINIO_ACCESS_KEY`, `$KEYCLOAK_REALM`, etc.
- Logging config: `$LOG_LEVEL`, `$LOG_FILE`, `$TIMESTAMP`, `$TIMESTAMP_SHORT`

### Secondary: `lib/common.sh`

**After `.env.defaults`, source common helpers:**

```bash
source "$SCRIPT_DIR/lib/common.sh"
register_error_trap  # Optional: enable error handling
```

**Common helpers are organized by category:**

#### Print Helpers (colors already available from .env.defaults)
```bash
print_header "My Script Title"      # Blue box with centered title
print_section "Setup Phase"          # Magenta section separator
print_step "Checking prerequisites"  # Yellow step marker
print_success "Operation completed"  # Green checkmark
print_warning "Be careful"           # Yellow warning symbol
print_error "Operation failed"       # Red error marker
```

#### Logging Helpers
```bash
log_info "Informational message"    # Blue [INFO] if LOG_LEVEL=DEBUG or INFO
log_debug "Debug message"            # Cyan [DEBUG] if LOG_LEVEL=DEBUG
log_success "Operation OK"           # Green ✅ [SUCCESS]
log_warn "Watch out"                 # Yellow ⚠️  [WARN]
log_error "Operation failed"         # Red ❌ [ERROR] (sends to stderr)
log_section "Major section"          # Magenta box separator + logs to file
```

#### Directory/File Checks
```bash
check_command "docker"               # Returns 0 if available, 1 if not
check_file "/path/to/file"           # Returns 0 if exists, 1 if not
check_directory "$TEMP_DIR"          # Returns 0 if exists, 1 if not
require_file "$LOG_FILE"             # Exits with error if not found
require_directory "$PROJECT_ROOT"    # Exits with error if not found
```

#### Docker Operations
```bash
require_docker                       # Verify docker + docker-compose + daemon running
                                    # Exits if SKIP_DOCKER_CHECK != "true"
```

#### Service Readiness
```bash
wait_for_service "$KEYCLOAK_URL/health" "Keycloak" 120 5
# Arguments:
#   1. URL endpoint (required)
#   2. Service name for logging (optional, default "Service")
#   3. Timeout in seconds (optional, default 120)
#   4. Check interval in seconds (optional, default 5)
# Returns: 0 on success, 1 on timeout
```

#### Maven Automation
```bash
run_maven_modules "services/Brazz-Nossel" "services/Sentinel-Gear"
# Sets these globals after execution:
#   MAVEN_TOTAL_TESTS      - Total test count across all modules
#   MAVEN_TOTAL_PASSED     - Count of passing tests
#   MAVEN_TOTAL_FAILED     - Count of failing tests
#   MAVEN_FOUND_COUNT      - Number of modules actually processed
#   MAVEN_EXPECTED_COUNT   - Number of modules provided
#   MAVEN_SUMMARY[@]       - Array of per-module summaries (✅/❌ strings)

# Optional --fail-fast mode:
run_maven_modules --fail-fast "services/Sentinel-Gear"
# Exits with code 1 on first failure instead of continuing
```

#### Temporary Files
```bash
temp_file=$(create_temp_file "myprefix")  # Creates in $TEMP_DIR, not /tmp
temp_dir=$(create_temp_dir "myprefix")    # Creates in $TEMP_DIR, not /tmp
# Use for intermediate files; cleaned up manually
```

#### Timing Utilities
```bash
timer_start                                # Starts a timer
elapsed=$(timer_elapsed)                   # Seconds since start
formatted=$(timer_elapsed_formatted)       # HH:MM:SS format
```

### Script Template

**New bash script template using shared helpers:**

```bash
#!/bin/bash
# My IronBucket Script
# Purpose: Description here

set -euo pipefail

# Load environment and common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/.env.defaults"
source "$SCRIPT_DIR/lib/common.sh"

# Optional: Register error trap for automatic error messages
register_error_trap

# Your script code here using shared helpers
main() {
    print_header "My Script Title"
    
    print_step "1" "Checking prerequisites"
    require_docker
    
    print_step "2" "Running Maven tests"
    run_maven_modules "services/MyService"
    
    print_success "Script completed!"
}

main "$@"
```

---

## Python Scripts Architecture

### Entry Point: `lib/python_utils.py`

**Import utilities at script start:**

```python
#!/usr/bin/env python3
import sys
from pathlib import Path

# Add lib to path
sys.path.insert(0, str(Path(__file__).parent / 'scripts' / 'lib'))
from python_utils import Logger, EnvResolver, JSONReporter, main_with_error_handling
```

### Core Classes

#### `Colors` – ANSI Palette
```python
from python_utils import Colors

print(f"{Colors.GREEN}Success!{Colors.NC}")
print(f"{Colors.RED}Error!{Colors.NC}")
# Available: RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, GRAY, BOLD, NC
```

#### `Logger` – Unified Logging
```python
logger = Logger(
    log_file="path/to/logfile.log",  # Optional; logs to file + console if provided
    verbose=True                       # If False, info() is silent
)

# Logging methods (all include timestamp and color)
logger.info("Informational message")      # Blue [INFO]
logger.success("Operation completed")     # Green ✅ SUCCESS
logger.warn("Warning message")            # Yellow ⚠️  WARN
logger.error("Error message")             # Red ❌ ERROR

# Formatted output
logger.header("Page Title")                # Blue box with title
logger.section("Section Title")            # Magenta separator + title
logger.step("Step description")            # Yellow marker
```

#### `EnvResolver` – Environment Resolution
```python
from python_utils import EnvResolver

env = EnvResolver()

# Get paths (respects env vars, falls back to defaults)
project_root = env.get_project_root()    # $PROJECT_ROOT or auto-discover
temp_dir = env.get_temp_dir()            # $TEMP_DIR or PROJECT_ROOT/build/temp
log_dir = env.get_log_dir()              # $LOG_DIR or PROJECT_ROOT/test-results/logs
report_dir = env.get_report_dir()        # $REPORTS_DIR or PROJECT_ROOT/test-results/reports

# Utility methods
is_container = env.is_container()        # Check if running in Docker
timestamp = env.get_timestamp()          # ISO 8601 format
timestamp_short = env.get_timestamp_short()  # YYYYMMDD_HHMMSS format
```

#### `JSONReporter` – JSON I/O
```python
from python_utils import JSONReporter

# Write JSON to file
data = {"name": "test", "status": "pass"}
output_path = JSONReporter.write(
    data, 
    "path/to/report.json", 
    pretty=True  # Pretty-print with indentation
)

# Read JSON from file
data = JSONReporter.read("path/to/report.json")
```

#### `@main_with_error_handling` – Decorator
```python
from python_utils import main_with_error_handling

@main_with_error_handling
def main():
    logger = Logger()
    logger.header("My Script")
    # ... your code ...
    return True

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
    
# Handles:
# - Ctrl+C (KeyboardInterrupt) → exit 130
# - Exceptions → traceback + exit 1
# - Success → exit 0 (if main() returns True)
```

### Script Template

**New Python script template using shared utilities:**

```python
#!/usr/bin/env python3
"""
My IronBucket Script
Purpose: Description here
"""

import sys
from pathlib import Path

# Add lib to path
sys.path.insert(0, str(Path(__file__).parent / 'scripts' / 'lib'))
from python_utils import Logger, EnvResolver, JSONReporter, main_with_error_handling

@main_with_error_handling
def main():
    env = EnvResolver()
    logger = Logger(
        log_file=str(Path(env.get_log_dir()) / 'my-script.log'),
        verbose=True
    )
    
    logger.header("         My Script Title         ")
    logger.section("STEP 1: Do something")
    
    logger.info(f"Project root: {env.get_project_root()}")
    logger.success("Operation completed!")
    
    return True

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
```

---

## Adding New Scripts

### Bash Script Checklist

- [ ] Source `.env.defaults` first (PROJECT_ROOT resolution, colors, paths)
- [ ] Source `lib/common.sh` second (helpers for logging, Docker, Maven)
- [ ] Use `print_*()` functions for user-facing output (not raw `echo`)
- [ ] Use `log_*()` functions for diagnostic logging (they handle file + console)
- [ ] Call `require_docker()` if Docker operations needed
- [ ] Call `wait_for_service()` for service readiness checks (not manual loops)
- [ ] Call `run_maven_modules()` for Maven tests (not custom loops)
- [ ] Add header comment explaining purpose
- [ ] Test: `bash -n script.sh` for syntax checking

### Python Script Checklist

- [ ] Import utilities from `scripts/lib/python_utils.py`
- [ ] Create `Logger` instance with optional log file
- [ ] Create `EnvResolver` instance for path resolution
- [ ] Use `logger.header()`, `logger.section()`, `logger.info()`, etc. (not `print()`)
- [ ] Use `EnvResolver.get_*()` for paths (not hardcoded)
- [ ] Use `JSONReporter.write()`/`read()` for JSON (not raw `json.dump()`)
- [ ] Decorate `main()` with `@main_with_error_handling`
- [ ] Add module docstring explaining purpose
- [ ] Test: `python3 -m py_compile script.py` for syntax checking

---

## Common Patterns

### Pattern 1: Print & Log

```bash
# Log for both console and file
log_info "Starting operation"     # Goes to LOG_FILE too

# Print-only (console)
print_step "1" "Checking prerequisites"  # For user-facing output
```

### Pattern 2: Service Readiness

```bash
# Wait for HTTP endpoint with timeout
if ! wait_for_service "$KEYCLOAK_URL/health" "Keycloak" 120 5; then
    log_error "Keycloak failed to start"
    exit 1
fi
```

### Pattern 3: Maven Test Aggregation

```bash
# Run multiple modules and aggregate results
run_maven_modules "services/ServiceA" "services/ServiceB" "services/ServiceC"

# Access results
echo "Total: $MAVEN_TOTAL_TESTS passed: $MAVEN_TOTAL_PASSED failed: $MAVEN_TOTAL_FAILED"

# Display per-module summary
for summary in "${MAVEN_SUMMARY[@]}"; do
    echo "  $summary"
done
```

### Pattern 4: Python Error Handling

```python
@main_with_error_handling
def main():
    try:
        # Your code here
        result = operation_that_might_fail()
        logger.success("Operation succeeded")
        return True
    except SpecificError as e:
        logger.error(f"Known error: {e}")
        return False
```

---

## Best Practices

### 1. **Always Source .env.defaults First**
Ensures colors and paths are available before common.sh.

### 2. **Prefer Shared Helpers Over Custom Code**
Before writing a function, check if common.sh has it.

### 3. **Use Absolute Paths**
Always use `$PROJECT_ROOT`, `$TEMP_DIR`, `$LOG_DIR` from env, not relative paths.

### 4. **Log Everything Important**
Use `log_info()`, `log_success()`, `log_error()` so future debugging is easier.

### 5. **Test Syntax Before Committing**
```bash
bash -n scripts/myscript.sh              # Bash
python3 -m py_compile scripts/myscript.py  # Python
```

### 6. **Document Parameter Requirements**
Add header comments explaining arguments and expected environment.

### 7. **Use Meaningful Names**
Avoid ambiguous variable names; prefer `MAVEN_TOTAL_TESTS` over `test_count`.

---

## Troubleshooting

### Issue: Colors not showing in output
**Cause:** `.env.defaults` not sourced, or piped through `less` (which strips colors)  
**Fix:** Ensure `source "$SCRIPT_DIR/.env.defaults"` is called early

### Issue: `run_maven_modules` not found
**Cause:** `common.sh` not sourced  
**Fix:** Ensure `source "$SCRIPT_DIR/lib/common.sh"` called after `.env.defaults`

### Issue: Python imports fail
**Cause:** Wrong path in `sys.path.insert(0, ...)`  
**Fix:** Use `Path(__file__).parent / 'lib'` relative to script location

### Issue: Log file permissions denied
**Cause:** $LOG_DIR not writable  
**Fix:** Check that `$LOG_DIR` exists and is owned by current user (created by .env.defaults)

---

## Related Documentation

- [DRY Consolidation Summary](DRY_CONSOLIDATION_SUMMARY.md) – What was refactored and why
- [DRY Validation Checklist](DRY_VALIDATION_CHECKLIST.md) – Validation results
- [scripts/lib/common.sh](scripts/lib/common.sh) – Full bash helper reference
- [scripts/lib/python_utils.py](scripts/lib/python_utils.py) – Full Python class reference
- [scripts/.env.defaults](scripts/.env.defaults) – Complete environment configuration

---

**Last Updated:** 2026-01-19  
**Status:** ✅ Stable & Production-Ready
