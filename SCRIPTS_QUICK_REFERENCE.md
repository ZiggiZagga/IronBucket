# IronBucket Scripts - Quick Reference Card

## Bash: Essential Helpers

### Print Formatting
```bash
print_header "Title Here"        # Blue box with centered text
print_section "Section Title"    # Magenta separator + title
print_step "Check something"     # Yellow step marker with message
print_success "Done!"            # Green checkmark + message
print_warning "Watch out"        # Yellow warning symbol + message
print_error "Failed!"            # Red X + message
```

### Logging (logs to file + console)
```bash
log_info "Message"              # [INFO] with timestamp (if DEBUG/INFO log level)
log_debug "Debug info"          # [DEBUG] with timestamp (if DEBUG log level)
log_success "Operation OK"      # ✅ [SUCCESS] with timestamp
log_warn "Warning message"      # ⚠️ [WARN] with timestamp
log_error "Error occurred"      # ❌ [ERROR] to stderr with timestamp
log_section "Section Name"      # Box separator + logs to file
```

### Prerequisites & Checks
```bash
check_command "docker"          # Returns 0 if installed, 1 if not
check_file "/path/to/file"      # Returns 0 if exists, 1 if not
check_directory "$TEMP_DIR"     # Returns 0 if exists, 1 if not
require_file "$LOG_FILE"        # Exits if not found
require_directory "$PROJECT_ROOT"  # Exits if not found
require_docker                  # Verify docker + daemon ready (exits if fails)
```

### Docker & Services
```bash
# Wait for HTTP endpoint (blocks until ready or timeout)
wait_for_service "http://localhost:7081/health" "Keycloak" 120 5
# Args: URL, service_name (for log), timeout_seconds, check_interval_seconds
# Returns: 0 on success, 1 on timeout
```

### Maven Automation
```bash
run_maven_modules "services/ServiceA" "services/ServiceB"
# Sets after execution:
#   $MAVEN_TOTAL_TESTS      - Total tests across all
#   $MAVEN_TOTAL_PASSED     - Count passed
#   $MAVEN_TOTAL_FAILED     - Count failed
#   $MAVEN_FOUND_COUNT      - Modules processed
#   $MAVEN_EXPECTED_COUNT   - Modules provided
#   ${MAVEN_SUMMARY[@]}     - Per-module summaries (array)

# Fail on first error:
run_maven_modules --fail-fast "services/ServiceA"
```

### Timing
```bash
timer_start                     # Start timer
elapsed=$(timer_elapsed)        # Get seconds since start
formatted=$(timer_elapsed_formatted)  # Get HH:MM:SS format
```

### Temp Files
```bash
temp_file=$(create_temp_file "prefix")  # Creates in $TEMP_DIR
temp_dir=$(create_temp_dir "prefix")    # Creates in $TEMP_DIR (not /tmp)
```

---

## Bash: Environment Variables (from .env.defaults)

### Colors
```bash
$RED, $GREEN, $YELLOW, $BLUE, $MAGENTA, $CYAN, $GRAY, $BOLD, $NC
```

### Paths
```bash
$PROJECT_ROOT       # /workspaces/IronBucket
$TEMP_DIR          # $PROJECT_ROOT/build/temp
$LOG_DIR           # $PROJECT_ROOT/test-results/logs
$ARTIFACT_DIR      # $PROJECT_ROOT/test-results/artifacts
$REPORTS_DIR       # $PROJECT_ROOT/test-results/reports
$STEEL_HAMMER_DIR  # $PROJECT_ROOT/steel-hammer
$SERVICES_DIR      # $PROJECT_ROOT/services
```

### Service URLs (container-aware)
```bash
$KEYCLOAK_URL          # http://steel-hammer-keycloak:7081 (in container)
                       # http://localhost:7081 (on host)
$MINIO_URL             # http://steel-hammer-minio:9000 (in container)
                       # http://localhost:9000 (on host)
$POSTGRES_HOST         # steel-hammer-postgres (in container)
                       # localhost (on host)
$SENTINEL_GEAR_URL     # http://steel-hammer-sentinel-gear:8080
$BRAZZ_NOSSEL_URL      # http://steel-hammer-brazz-nossel:8082
$BUZZLE_VANE_URL       # http://steel-hammer-buzzle-vane:8083
```

### Logging
```bash
$LOG_LEVEL          # DEBUG, INFO, WARN, ERROR
$LOG_FILE           # Path to active log file
$TIMESTAMP          # Full timestamp in log entries
$TIMESTAMP_SHORT    # YYYYMMDD_HHMMSS for filenames
```

---

## Python: Essential Classes

### Logger
```python
from python_utils import Logger

logger = Logger(log_file="path/to/log.txt", verbose=True)
logger.info("Info message")      # Blue [INFO]
logger.success("Done!")          # Green ✅ SUCCESS
logger.warn("Warning")           # Yellow ⚠️ WARN
logger.error("Failed")           # Red ❌ ERROR
logger.header("Title Here")      # Blue box
logger.section("Section Title")  # Magenta separator
logger.step("Step description")  # Yellow marker
```

### EnvResolver
```python
from python_utils import EnvResolver

env = EnvResolver()
project_root = env.get_project_root()      # $PROJECT_ROOT or auto-discover
temp_dir = env.get_temp_dir()              # $TEMP_DIR or PROJECT_ROOT/build/temp
log_dir = env.get_log_dir()                # $LOG_DIR or PROJECT_ROOT/test-results/logs
report_dir = env.get_report_dir()          # $REPORTS_DIR or PROJECT_ROOT/test-results/reports
is_container = env.is_container()          # True if in Docker
timestamp = env.get_timestamp()            # ISO 8601 format
timestamp_short = env.get_timestamp_short()  # YYYYMMDD_HHMMSS
```

### JSONReporter
```python
from python_utils import JSONReporter

# Write JSON
data = {"status": "pass", "count": 5}
path = JSONReporter.write(data, "output.json", pretty=True)

# Read JSON
data = JSONReporter.read("output.json")
```

### Colors
```python
from python_utils import Colors

print(f"{Colors.GREEN}Success!{Colors.NC}")
print(f"{Colors.RED}Error!{Colors.NC}")
# Available: RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, GRAY, BOLD, NC
```

### Error Handler Decorator
```python
from python_utils import main_with_error_handling

@main_with_error_handling
def main():
    # Your code here
    return True  # Success

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
# Handles KeyboardInterrupt, exceptions, prints traceback
```

---

## Script Templates

### Bash Script
```bash
#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/.env.defaults"
source "$SCRIPT_DIR/lib/common.sh"
register_error_trap

main() {
    print_header "My Script Title"
    print_step "1" "Doing something"
    print_success "Done!"
}

main "$@"
```

### Python Script
```python
#!/usr/bin/env python3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / 'scripts' / 'lib'))
from python_utils import Logger, EnvResolver, main_with_error_handling

@main_with_error_handling
def main():
    env = EnvResolver()
    logger = Logger(log_file=str(Path(env.get_log_dir()) / 'script.log'))
    logger.header("My Script Title")
    logger.success("Done!")
    return True

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
```

---

## Common Patterns

### Check prerequisites
```bash
require_docker
check_command "mvn" || error_exit 1 "Maven not installed"
require_directory "$PROJECT_ROOT"
```

### Wait for service
```bash
if ! wait_for_service "$KEYCLOAK_URL/health" "Keycloak" 120 5; then
    log_error "Keycloak failed to start"
    exit 1
fi
```

### Run Maven tests
```bash
run_maven_modules "services/ServiceA" "services/ServiceB"
echo "Passed: $MAVEN_TOTAL_PASSED / $MAVEN_TOTAL_TESTS"
```

### Log and display results
```bash
log_success "Operation completed"
print_success "Operation completed"

for summary in "${MAVEN_SUMMARY[@]}"; do
    echo "  $summary"
done
```

---

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| Colors not showing | `.env.defaults` not sourced | Add: `source "$SCRIPT_DIR/.env.defaults"` |
| `print_header` not found | `common.sh` not sourced | Add: `source "$SCRIPT_DIR/lib/common.sh"` |
| Paths undefined | `.env.defaults` not sourced | Source `.env.defaults` first |
| Python imports fail | Wrong path | Use: `Path(__file__).parent / 'scripts' / 'lib'` |
| Log file empty | `LOG_FILE` not writable | Check `$LOG_DIR` permissions |
| Docker check fails | Docker not running | Start Docker daemon |
| Service wait timeout | Service too slow | Increase timeout (3rd argument) |

---

## File Locations

**Source these files in this order:**
1. `scripts/.env.defaults` – Colors, paths, URLs (REQUIRED)
2. `scripts/lib/common.sh` – Bash helpers (REQUIRED for bash scripts)
3. `scripts/lib/python_utils.py` – Python utilities (import in Python scripts)

**Output locations (automatically created):**
- Logs: `test-results/logs/`
- Reports: `test-results/reports/`
- Artifacts: `test-results/artifacts/`
- Temp files: `build/temp/`

---

**Reference:** See SCRIPTS_ARCHITECTURE_GUIDE.md for detailed documentation  
**Last Updated:** 2026-01-19
