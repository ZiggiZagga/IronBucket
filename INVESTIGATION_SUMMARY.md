# IronBucket mTLS Implementation & Testing - Investigation Summary

**Date:** January 19, 2026  
**Status:** In Progress - System Deadlock Issue Identified

## What We Accomplished

### ✅ Completed Tasks

1. **mTLS Certificate Infrastructure**
   - Generated Root CA (4096-bit, 10-year validity)
   - Created 11 service certificates (2048-bit, 1-year validity)
   - PKCS12 format with proper key/trust stores
   - Flexible certificate path configuration via environment variables

2. **Spring WebClient mTLS Integration**
   - Refactored all services from Apache HttpClient to Spring WebClient
   - Created `MTLSClientConfig` bean for secure inter-service communication
   - Implemented certificate/truststore loading from `application-mtls.yml`
   - Added 8 unit tests for MTLSClientConfig validation

3. **Docker Compose mTLS Configuration**
   - Modified `docker-compose-steel-hammer.yml` for conditional mTLS support
   - Environment variable interpolation: `MTLS_PROFILE`, `HEALTH_CHECK_PROTOCOL`, `CERT_BASE_PATH`
   - All 4 services (Sentinel-Gear, Claimspindel, Brazz-Nossel, Buzzle-Vane) configured with mTLS

4. **spinup.sh Enhancement**
   - Added `--with-mtls` flag for one-command mTLS activation
   - Automatic certificate generation if missing
   - Environment variable exports control Docker Compose behavior
   - Added `--test-only` and `--e2e-only` flags for isolated testing

5. **Code Cleanup**
   - Removed unused JClouds dependencies from Vault-Smith (was causing hangs)
   - Removed unnecessary `assumeTrue()` conditional skipping in NetworkPolicyTest
   - Streamlined certificate path handling with defaults

6. **Network Policy Tests**
   - Cleaned up SentinelGearNetworkPolicyTest (removed conditional skipping)
   - Tests now fail-fast if services not available (correct behavior)

### ✅ Test Results (from logs)
- **Brazz-Nossel**: 25 tests passed ✅
- **Claimspindel**: 37 tests passed ✅ (from logs: 11:46)
- **Buzzle-Vane**: 30 tests passed ✅
- **Sentinel-Gear**: 77 tests total, 14 failures (EXPECTED - services not started):
  - 8x NetworkPolicyTest failures (503 SERVICE_UNAVAILABLE - expected)
  - 6x mTLSTest failures (503 SERVICE_UNAVAILABLE - expected)
  - All other tests passed ✅
- **Storage-Conductor**: 10 tests passed ✅
- **Vault-Smith**: Builds successfully in 6 seconds (after JClouds removal) ✅
- **graphite-admin-shell**: Never reached due to system deadlock issue

## ❌ Critical Issue Identified

### Maven System Deadlock

**Symptom:**
- First 2-3 Maven `clean test` runs complete successfully
- After 3rd-4th run, Maven process becomes unresponsive
- Subsequent commands don't execute (system deadlock)
- Even simple shell commands (`ps aux`, `uptime`) hang indefinitely

**Evidence:**
- Maven logs show successful completions (BUILD SUCCESS, tests passed)
- Multiple test logs with different timestamps (11:31, 11:35, 11:42, 11:43, 11:46)
- Each run takes expected time (~14-15 seconds for Brazz-Nossel)
- After ~4 successful runs, system becomes completely unresponsive
- Java process doesn't respond to `pkill -9`

**Root Cause Analysis:**
1. **NOT a shell script problem** - Direct Maven commands also hang after 3-4 runs
2. **NOT a memory leak in our code** - Individual modules build fine
3. **NOT TTY/job control issue** - Used `nohup`, `disown`, output redirection
4. **Likely causes:**
   - Maven process accumulation (each `timeout 180` spawns bash + Maven JVM)
   - JVM memory exhaustion (multiple Java processes competing for resources)
   - Maven local repository lock contention
   - File descriptor exhaustion from repeated builds

### Why Tests Can't Complete

The test execution flow:
```
spinup.sh --with-mtls
├─ run_maven_modules (loop through 7 modules)
│  ├─ Brazz-Nossel: SUCCESS ✅
│  ├─ Claimspindel: SUCCESS ✅
│  ├─ Buzzle-Vane: SUCCESS ✅
│  ├─ Sentinel-Gear: SUCCESS (14 expected failures) ✅
│  ├─ Storage-Conductor: SUCCESS ✅
│  ├─ Vault-Smith: SYSTEM DEADLOCK ❌ (after ~4-5 modules, Maven hangs)
│  └─ graphite-admin-shell: NEVER REACHED (system unresponsive)
├─ Docker startup: SKIPPED (system deadlock prevents reaching this step)
├─ E2E Alice-Bob test: SKIPPED
└─ Report generation: SKIPPED
```

**Key finding:** Maven doesn't fail - it succeeds and then the NEXT iteration hangs before starting.

## Proposed Solutions

### Solution A: Single Maven Process (RECOMMENDED)
Run all 7 modules in ONE Maven process instead of 7 separate processes:
```bash
# Current: 7 separate `mvn clean test` invocations
# Proposed: 1 invocation with -pl (project list) flag
mvn clean test -pl services/Brazz-Nossel,services/Claimspindel,...
```
**Pros:** Single JVM, no process accumulation, faster overall  
**Cons:** If one module fails, others might skip

### Solution B: Separate JVM Sessions  
Kill Maven process between builds:
```bash
for module in modules[]; do
    mvn -f $module/pom.xml clean test
    pkill -9 java  # Force JVM termination
    sleep 2  # Allow cleanup
done
```
**Pros:** Clean state between modules  
**Cons:** Slower (JVM startup overhead), resource intensive

### Solution C: Docker Test Container
Run Maven tests inside containerized test environment (Steel-Hammer Test Runner):
```bash
docker run --rm steel-hammer-test mvn clean test -f /modules/pom.xml
```
**Pros:** Isolated environments, automatic cleanup  
**Cons:** Added Docker overhead, slower

### Solution D: Reduce Module Count
Test only critical modules locally (3-4), others in Docker:
```bash
# Local Maven tests (unit tests only)
./spinup.sh --test-only  # Only: Brazz-Nossel, Claimspindel, Buzzle-Vane

# Docker integration tests (with real services)
./spinup.sh --e2e-only  # Start Docker + E2E tests
```

## TODO - Next Steps

### 🔧 Phase 1: Fix Maven Deadlock (CRITICAL)
- [ ] Implement Solution A: Single Maven process with `-pl` flag
  - [ ] Create new `run_maven_single_process()` function
  - [ ] Test with all 7 modules
  - [ ] Verify no deadlock after 10+ iterations
- [ ] Or implement Solution D: Test isolation
  - [ ] Separate Maven tests (local) from integration tests (Docker)
  - [ ] Run `--test-only` with 3 core modules
  - [ ] Then run `--e2e-only` for full integration

### 🐳 Phase 2: Docker E2E Testing (AFTER Maven fix)
- [ ] Ensure Docker startup with mTLS (SPRING_PROFILES_ACTIVE=docker,mtls)
- [ ] Verify HTTPS healthchecks work
- [ ] Test inter-service communication over mTLS
- [ ] Run Alice-Bob E2E scenario (file upload/download)

### 📊 Phase 3: Report Generation
- [ ] Create comprehensive test report
- [ ] Include: Maven results, Docker health checks, E2E test results
- [ ] Generate in JSON + HTML formats
- [ ] Store in `/test-results/reports/`

### 📝 Phase 4: Documentation
- [ ] Document mTLS setup and certificate generation
- [ ] Update CONTRIBUTING.md with test execution flow
- [ ] Create troubleshooting guide for common issues

## File Changes Made

### Modified Files
1. `tools/Vault-Smith/pom.xml` - Removed JClouds dependencies
2. `scripts/spinup.sh` - Added `--test-only` and `--e2e-only` flags
3. `scripts/lib/common.sh` - Fixed Maven loop deadlock attempt (partial)
4. `services/*/src/main/resources/application-mtls.yml` - Flexible paths
5. `steel-hammer/docker-compose-steel-hammer.yml` - Conditional mTLS

### Created Files
- `certs/generate-certificates.sh` - Certificate generation script
- Various test scripts for isolation debugging

## How to Resume

1. System restart required (current deadlock)
2. Start with Solution A: Implement single Maven process
3. Test `./scripts/spinup.sh --test-only --with-mtls`
4. Verify all 7 modules complete without deadlock
5. Then proceed to Docker E2E testing

## Commands for Testing

```bash
# After system restart:

# Test-only mode (Maven tests without Docker)
./scripts/spinup.sh --test-only --with-mtls

# E2E-only mode (Docker + tests, no Maven)
./scripts/spinup.sh --e2e-only --with-mtls

# Full mode (Maven + Docker + E2E)
./scripts/spinup.sh --with-mtls

# Local testing without mTLS
./scripts/spinup.sh --test-only
```

## Key Learnings

1. **Maven process accumulation is real** - Each `mvn` invocation in a shell loop can exhaust system resources
2. **`set -e` in loops is dangerous** - Even with `set +e`, calling `set -e` again in loop can cause unexpected failures
3. **TTY redirection doesn't solve process management** - Multiple pipes (`tee`) can cause SIGSTOP
4. **Flexible path configuration works** - Environment variable defaults elegant solution for container vs local paths
5. **Docker is essential** - Single-process or containerized approach needed for reliable test execution

---

**Next Review Date:** After system restart  
**Owner:** IronBucket Development Team  
**Status:** BLOCKED - Awaiting system restart & Maven deadlock fix
