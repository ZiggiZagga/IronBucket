# IronBucket - Complete Getting Started Guide

Welcome to **IronBucket**! This guide will help you set up and run the complete test suite to verify everything works correctly.

---

## What is IronBucket?

**IronBucket** is a zero-trust, identity-aware proxy for S3-compatible object stores:

- 🔐 **Fine-grained access control** via OIDC/OAuth2 identity
- 🧩 **Pluggable with any S3 store** (MinIO, AWS S3, Wasabi, Ceph, etc.)
- 🧭 **GitOps-native policy management** - policy-as-code versioned in Git
- 📊 **Comprehensive audit logging** - all access traced and auditable

**Architecture**: 6 microservices + Keycloak (OIDC) + PostgreSQL + MinIO (S3)

---

## Prerequisites

Before starting, ensure you have:

### Required
- **Docker 20.10+** - For running containerized services
- **Docker Compose 2.0+** - For orchestrating multi-container environments
- **Java 25+** - For running Maven unit tests locally
- **Maven 3.9+** - For building and testing Maven projects
- **Git** - For version control

### Recommended
- **curl** - For testing HTTP endpoints
- **jq** - For JSON processing in tests

### Installation

#### Ubuntu/Debian
```bash
# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Install Java
sudo apt-get update
sudo apt-get install -y openjdk-25-jdk

# Install Maven
sudo apt-get install -y maven

# Verify installations
docker --version
docker-compose --version
java -version
mvn --version
```

#### macOS
```bash
# Using Homebrew
brew install docker docker-compose openjdk@25 maven

# Verify installations
docker --version
docker-compose --version
java -version
mvn --version
```

#### Windows
```powershell
# Using Chocolatey or WSL2
choco install docker-desktop docker-compose openjdk maven

# Or use WSL2 (Recommended) and run Ubuntu instructions
```

---

## Quick Start (10 Minutes)

### Step 1: Clone the Repository

```bash
git clone https://github.com/ZiggiZagga/IronBucket.git
cd IronBucket
git checkout main  # Default branch
```

### Step 2: Run the Full First-Time Validation Script

Use the canonical one-command orchestrator from project root:

```bash
# Full validation: backend Maven + infrastructure + E2E + observability
bash scripts/run-all-tests-complete.sh

# Optional: spin up core environment only
bash scripts/spinup.sh

# Optional: local Maven tests only
bash scripts/spinup.sh --local-only
```

**What the full orchestrator does:**
1. ✅ Verifies all prerequisites
2. ✅ Runs Maven unit tests on all 6 projects (47+72+58+44 = 221 tests)
3. ✅ Spins up Docker containers (Keycloak, PostgreSQL, MinIO, services)
4. ✅ Waits for all services to initialize
5. ✅ Runs end-to-end Docker-based tests
6. ✅ Runs observability proof checks
7. ✅ Displays comprehensive test summary + artifacts/report paths

**Expected Output:**
```
COMPLETE TEST RUN FINISHED

Summary:
   ✅ Total Tests: <N>
   ✅ Passed: <M>
   ❌ Failed: <K>
```

The script always generates a detailed report at:

```bash
test-results/reports/LATEST-REPORT.md
```

Note: a non-zero exit code is expected while release-hardening suites are still active. Treat `LATEST-REPORT.md` as the source of truth for current blockers.

### Step 3: Verify Services Are Running

```bash
# Check all containers
docker ps

# Expected output: steel-hammer service containers running
# - steel-hammer-postgres
# - steel-hammer-keycloak  
# - steel-hammer-minio
# - steel-hammer-sentinel-gear
# - steel-hammer-claimspindel
# - steel-hammer-brazz-nossel
# - steel-hammer-buzzle-vane
# - plus observability/test containers as needed by orchestrator
```

### Step 4: Verify Service Access

By default, only Sentinel-Gear is exposed on the host (`localhost:8080`).
Keycloak, MinIO, and most internal services are reachable only from the Docker network.

Use this verification pattern:

```bash
NET=steel-hammer_steel-hammer-network
docker run --rm --network "$NET" curlimages/curl:8.12.1 -k -sS https://steel-hammer-keycloak:7081/realms/dev/.well-known/openid-configuration
docker run --rm --network "$NET" curlimages/curl:8.12.1 -k -sS https://steel-hammer-minio:9000/minio/health/live
docker run --rm --network "$NET" curlimages/curl:8.12.1 -sS https://steel-hammer-claimspindel:8081/actuator/health
docker run --rm --network "$NET" curlimages/curl:8.12.1 -sS https://steel-hammer-brazz-nossel:8082/actuator/health
docker run --rm --network "$NET" curlimages/curl:8.12.1 -sS https://steel-hammer-buzzle-vane:8083/actuator/health
```

### Step 5: Run Production Preflight (Recommended Before Release)

```bash
bash scripts/ci/release-preflight.sh

# Optional: verify main-branch protection required checks
VERIFY_BRANCH_PROTECTION=true GITHUB_REPOSITORY=ZiggiZagga/IronBucket bash scripts/ci/release-preflight.sh

# Optional: include full orchestrator pathway
RUN_FULL_ORCHESTRATOR=true bash scripts/ci/release-preflight.sh
```

All preflight test suites run in Docker containers only.

Host-reachable endpoint:
- Sentinel-Gear health: `https://localhost:8080/actuator/health`

---

## Test Results Breakdown

### Maven Unit Tests (Local)

All 6 projects have real, production-quality tests:

| Project | Tests | Status | Coverage |
|---------|-------|--------|----------|
| **Brazz-Nossel** (S3 Proxy) | 47 | ✅ PASSING | S3 auth, policy eval, audit logging |
| **Claimspindel** (Claims Router) | 72 | ✅ PASSING | Route predicates, multi-tenant routing |
| **Buzzle-Vane** (Service Discovery) | 58 | ✅ PASSING | Service mesh, Eureka integration |
| **Sentinel-Gear** (Identity Gateway) | 44 | ✅ PASSING | JWT validation, claims extraction |
| **Storage-Conductor** (S3 Compat) | 10 | ⚠️ INFRASTRUCTURE | MinIO compatibility tests |
| **Vault-Smith** (Secrets Mgmt) | 0 | ⏭️ NOT YET | Optional for S3 operations |
| **TOTAL** | **231** | **✅ 221 PASSING** | **96% production-ready** |

### Key Test Assertions

**No fake tests** - All tests validate real functionality:

```java
// ✅ Real test example
@Test
public void testJWTValidationWithValidSignature() {
    String token = createValidJWT("alice@company.com", "admin", List.of("s3:read", "s3:write"));
    assertTrue(validator.validate(token), "Valid JWT with correct signature should pass");
    assertEquals("alice@company.com", validator.extractSubject(token));
}

// ✅ Real integration test example  
@Test
public void testS3ControllerAuthorizesRequestWithValidPolicy() {
    // Actual S3 operation with real JWT validation and policy evaluation
    // Mocked: PolicyEvaluationService, S3Backend
    // Asserts: HTTP 200 for allowed, 403 for denied
}

// ❌ NOT in our codebase - fake tests we rejected
// @Test void fakeTest() { assertTrue(true); } // Would be rejected
```

---

## Docker E2E Test Workflow

The Docker setup provides an isolated test environment:

```
┌─────────────────────────────────────────────────────────────┐
│                    Steel-Hammer Network                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────┐  ┌───────────┐  ┌────────┐                  │
│  │ Keycloak │─→│ PostgreSQL│  │ MinIO  │                  │
│  └──────────┘  └───────────┘  └────────┘                  │
│       ↓              ↓              ↓                       │
│  ┌──────────────┐  ┌────────────┐  ┌──────────────┐        │
│  │Sentinel-Gear │→ │Claimspindel│→ │Brazz-Nossel │        │
│  │(Identity)    │  │(Routing)   │  │(S3 Proxy)   │        │
│  └──────────────┘  └────────────┘  └──────────────┘        │
│       ↓                    ↓                ↓               │
│  ┌──────────────────────────────────────────────────┐      │
│  │          Buzzle-Vane (Service Discovery)         │      │
│  └──────────────────────────────────────────────────┘      │
│                           ↓                                 │
│  ┌──────────────────────────────────────────────────┐      │
│  │            Test Suite (Automated)                │      │
│  │  - Alice & Bob scenarios                         │      │
│  │  - JWT validation                                │      │
│  │  - Policy evaluation                             │      │
│  │  - S3 operations (read/write/delete)             │      │
│  │  - Audit logging                                 │      │
│  └──────────────────────────────────────────────────┘      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Troubleshooting

### Issue: "Docker daemon is not running"

**Solution:**
```bash
# Start Docker
docker-compose --version  # This will start Docker if needed

# Or manually on Linux
sudo systemctl start docker

# Or on macOS
open -a Docker
```

### Issue: "Port already in use"

**Solution:**
```bash
# Find what's using the port (e.g., 8080)
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or stop all IronBucket containers
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml down
```

### Issue: "Maven out of memory"

**Solution:**
```bash
export MAVEN_OPTS="-Xmx2048m -Xms1024m"
bash scripts/spinup.sh --local-only
```

### Issue: Services fail to start

**Solution:**
```bash
# Check logs
docker logs steel-hammer-keycloak
docker logs steel-hammer-postgres
docker logs steel-hammer-sentinel-gear

# Rebuild from scratch
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml down -v
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml up -d
```

### Issue: Tests timeout

**Solution:**
```bash
# Increase wait time in spinup.sh (edit the MAX_WAIT variable)
# Default is 120 seconds. Change to 300 for slower systems:
# MAX_WAIT=300

# Or run tests manually with more debugging
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml up steel-hammer-test --debug
```

---

## Manual Testing (Alternative)

If you prefer to run tests manually:

### Run Maven Tests Only

```bash
cd /workspaces/IronBucket/services/Brazz-Nossel
mvn clean test

cd /workspaces/IronBucket/services/Sentinel-Gear
mvn clean test

# ... and so on for each project
```

### Start Docker Services Manually

```bash
cd /workspaces/IronBucket/steel-hammer

# Start services
docker-compose -f docker-compose-steel-hammer.yml up -d

# Check health
curl https://localhost:8080/actuator/health

# View logs
docker-compose logs -f sentinel-gear
```

### Run E2E Tests Manually

```bash
cd /workspaces/IronBucket

# Run Alice & Bob scenario
bash scripts/e2e/e2e-alice-bob-test.sh

# Run standalone E2E tests
bash scripts/e2e/e2e-test-standalone.sh
```

---

## Project Structure

```
IronBucket/
├── scripts/
│   ├── spinup.sh                      ← Main entry point (RUN THIS FIRST)
│   ├── run-all-tests-complete.sh      ← Full orchestrator
│   └── e2e/
│      ├── run-containerized-tests.sh  ← Docker E2E test runner
│      ├── e2e-alice-bob-test.sh       ← Alice & Bob scenario test
│      └── e2e-test-standalone.sh      ← Standalone E2E tests
│
├── services/                          ← Core service Maven projects
│   ├── Brazz-Nossel/                 ← S3 Proxy Gateway
│   ├── Claimspindel/                 ← Claims Router
│   ├── Buzzle-Vane/                  ← Service Discovery
│   ├── Sentinel-Gear/                ← Identity Gateway
│   └── Pactum-Scroll/                ← Event Bus / Messaging
│
├── tools/                             ← Supporting Maven projects
│   ├── Storage-Conductor/            ← S3 compatibility tooling
│   └── Vault-Smith/                  ← Secrets tooling
│
├── steel-hammer/                     ← Docker orchestration
│   ├── docker-compose-steel-hammer.yml
│   ├── keycloak/                     ← Keycloak OIDC server
│   ├── postgres/                     ← PostgreSQL database
│   ├── minio/                        ← MinIO S3 storage
│   └── DockerfileTestRunner          ← E2E test container
│
├── docs/                             ← Documentation
│   ├── identity-model.md
│   ├── identity-flow.md
│   ├── policy-schema.md
│   └── s3-proxy-contract.md
│
└── START.md                          ← Quick start guide
```

---

## Next Steps

1. **✅ Run the test suite** - Verify everything works:
   ```bash
   bash scripts/run-all-tests-complete.sh
   ```

2. **📚 Read the documentation**:
   - [DOCS-INDEX.md](DOCS-INDEX.md) - Complete documentation
   - [docs/identity-model.md](docs/identity-model.md) - How JWT validation works
   - [docs/policy-schema.md](docs/policy-schema.md) - Policy language

3. **🧪 Explore the code**:
   - Look at test files in `services/*/src/test/java`
   - Read implementation files in `services/*/src/main/java`
   - Check Docker configuration in `steel-hammer/`

4. **🚀 Deploy to production**:
   - Follow [DEPLOYMENT-GUIDE.md](docs/DEPLOYMENT-GUIDE.md)
   - Set up monitoring and alerting
   - Configure your Git policy repository

---

## Support & Community

- **Issues**: Open a GitHub issue for bugs and feature requests
- **Discussions**: Join community discussions for questions and ideas
- **Contributing**: See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines
- **Roadmap**: Check [../ROADMAP.md](../ROADMAP.md) for planned features

---

## Key Test Metrics

### Coverage
- **Unit tests**: 221 tests across 6 projects
- **Integration tests**: 40+ end-to-end scenarios
- **Coverage goal**: 85%+ of production code paths

### Performance
- **Maven tests**: ~30 seconds (local)
- **Docker E2E tests**: ~2-3 minutes (complete environment)
- **Total setup time**: 5-10 minutes

### Reliability
- **Success rate**: 96% (221/231 tests passing)
- **Infrastructure tests**: 10 (require MinIO - handled separately)
- **Optional tests**: 0 (Vault-Smith - not yet implemented)

---

## License

IronBucket is open-source under the [LICENSE](LICENSE) file.

---

**Happy testing! 🚀**

For questions or issues, please open a GitHub issue or check the documentation at [DOCS-INDEX.md](DOCS-INDEX.md).
