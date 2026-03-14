# CI/CD Pipeline Documentation

## Overview

IronBucket implements **production-grade CI/CD pipelines** with comprehensive automation, security scanning, and supply-chain security through SLSA Build Level 3 provenance.

## 🔄 Workflows

### 1. Build and Test (`build-and-test.yml`)

**Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop`

**Actions:**
- ✅ Runs core Maven module tests in Docker containers via `scripts/ci/run-core-module-tests-container.sh`
- ✅ Uses shared containerized Maven runner `scripts/ci/run-maven-in-container.sh`
- ✅ Runs full test suite (231 tests)
- ✅ Exports machine-readable gate-policy evidence via `scripts/ci/export-gate-policy-summary.sh`
- ✅ Uploads gate-policy artifact (`gate-policy-summary`) for audit traceability
- ✅ Verifies roadmap E2E docs/workflow sync via `scripts/ci/verify-e2e-doc-sync.sh`
- ✅ Enforces containerized test execution policy via `scripts/ci/verify-containerized-tests-only.sh`
- ✅ Uses deterministic roadmap E2E proof command: `scripts/e2e/prove-phase1-4-complete.sh`
- ✅ Enforces Sentinel roadmap gate via `scripts/ci/run-sentinel-roadmap-gate.sh` (blocking CI check, containerized)
- ✅ Runs separate Sentinel behavioral integration gate via `scripts/ci/run-sentinel-behavioral-gate.sh` (containerized)
  - strict blocking mode on all configured refs
- ✅ Runs dedicated governance roadmap gate via `scripts/ci/run-governance-roadmap-gate.sh`
  - executes `GovernanceIntegrityResilienceTest` as a blocking contract check
  - exports machine-readable governance evidence summary via `scripts/ci/export-governance-evidence-summary.sh`
- ✅ Runs deterministic governance drift gate via `scripts/ci/run-governance-drift-gate.sh`
  - validates drift/reconciliation fixture outputs and exports evidence summaries
- ✅ Runs credential-backed jclouds provider integration probe gate via `scripts/ci/run-jclouds-provider-integration-probe-gate.sh`
  - selective AWS/GCS/Azure capability integration probes based on available CI secrets
- ✅ Runs credential-backed jclouds provider integration parity gate via `scripts/ci/run-jclouds-provider-integration-parity-gate.sh`
  - selective AWS/GCS/Azure CRUD parity integration checks based on available CI secrets
- ✅ Runs Phase-4 versioning/multipart parity gate via `scripts/ci/run-phase4-versioning-multipart-gate.sh`
  - validates deterministic versioning/delete-marker fixture and provider-neutral multipart/versioning parity contracts
- ✅ Runs governance incident playbook gate via `scripts/ci/run-governance-incident-playbook-gate.sh`
  - validates deterministic policy-bypass, crash-recovery, isolation, and error-matrix fixtures
- ✅ Runs advanced resilience gate via `scripts/ci/run-advanced-resilience-gate.sh`
  - validates deterministic disk-pressure, HA failover, streaming-latency, and partition-reconciliation fixtures
- ✅ Runs adapter upgrade safety gate via `scripts/ci/run-adapter-upgrade-safety-gate.sh`
  - executes `AdapterSchemaUpgradeTest` as a blocking schema/backward-compatibility contract
- ✅ Caches Maven dependencies for faster builds
- ✅ Uploads test results and build artifacts
- ✅ Generates test summary in GitHub UI

**Expected Results:**
- All modules compile successfully
- All 231 tests pass
- E2E workflow (`e2e-complete-suite`) and docs reference the same active proof gate command
- Sentinel roadmap profile runs with minimum executed-test threshold and zero failures
- Sentinel behavioral integration profile is reported independently from roadmap scaffold checks
- JAR artifacts generated and uploaded

---

### 1b. E2E Complete Suite (`e2e-complete-suite.yml`)

This is the canonical first-user experience verification workflow and now triggers all required E2E-adjacent checks:

- Cross-project gate via `scripts/ci/run-all-projects-e2e-gate.sh` (all Java + UI projects)
- First-user experience gate via `scripts/ci/run-first-user-experience-gate.sh` (Phase 1-4 proof)
- Observability infrastructure gate via `scripts/ci/run-observability-infra-gate.sh`
- Deterministic observability proof command: `scripts/e2e/prove-phase2-observability.sh`
- Observability asset validation via `scripts/ci/validate-observability-assets.sh` (dashboards + alert rules)
- Steel-hammer container-runtime suite available for parity diagnostics: `sh steel-hammer/test-scripts/e2e-complete-suite.sh`

Use this workflow as the primary end-to-end release confidence gate.

---

### 1b. End-to-End Complete Suite (`e2e-complete-suite.yml`)

**Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop`

**Actions:**
- ✅ Runs all Java + UI projects gate (`scripts/ci/run-all-projects-e2e-gate.sh`)
- ✅ Enforces Next.js live UI persistence scenario (`ui-live-upload-persistence.spec.ts`) with real backend round-trip verification
- ✅ Executes UI Playwright E2E in dedicated compose runner container (`steel-hammer-ui-e2e`) with explicit service dependencies
- ✅ Validates gateway `/graphql` path through Sentinel-Gear to Graphite-Forge runtime service
- ✅ Runs Phase 1-4 roadmap E2E proof gate
- ✅ Runs Phase 2 observability infrastructure gate (`scripts/ci/run-observability-infra-gate.sh`)
- ✅ Uses deterministic Phase 2 proof command (`scripts/e2e/prove-phase2-observability.sh`)
- ✅ Enforces Mimir ingestion thresholds for `steel-hammer-keycloak`, `steel-hammer-minio`, and `steel-hammer-postgres-exporter`
- ✅ Uploads phase proof artifacts for debugging and trend analysis

**Expected Results:**
- First-user E2E flow remains green
- Containerized UI E2E gate remains deterministic across local and CI environments
- Infrastructure metrics targets are scrapeable and queryable
- Infra `up` sums in Mimir remain above configured thresholds

---

### 2. Security Scanning (`security-scan.yml`)

**Triggers:**
- Push to `main` or `develop`
- Pull requests to `main`
- Weekly schedule (Mondays at 00:00 UTC)

**Scans:**

#### a) Dependency Vulnerability Scanning (OWASP)
- Scans all dependencies for known CVEs
- Fails build on CVSS score ≥ 7
- Generates HTML reports

#### b) Static Analysis (SpotBugs)
- Identifies potential bugs and code smells
- Analyzes compiled bytecode
- Produces XML reports

#### c) Code Style (Checkstyle)
- Enforces coding standards
- Checks formatting and conventions
- Generates violation reports

#### d) Secret Scanning (TruffleHog)
- Scans for accidentally committed secrets
- Checks entire git history
- Only reports verified secrets

**Outputs:**
- Security scan summary in GitHub UI
- Detailed reports as workflow artifacts
- SARIF files for GitHub Security tab

---

### 2b. Governance + Resilience Periodic (`governance-resilience-periodic.yml`)

**Triggers:**
- Weekly schedule (Mondays at 02:17 UTC)
- Manual workflow dispatch

**Actions:**
- ✅ Runs governance roadmap gate (`scripts/ci/run-governance-roadmap-gate.sh`)
- ✅ Runs governance drift gate (`scripts/ci/run-governance-drift-gate.sh`)
- ✅ Runs governance incident playbook gate (`scripts/ci/run-governance-incident-playbook-gate.sh`)
- ✅ Runs Phase-4 versioning/multipart parity gate (`scripts/ci/run-phase4-versioning-multipart-gate.sh`)
- ✅ Runs advanced resilience gate (`scripts/ci/run-advanced-resilience-gate.sh`)
- ✅ Runs adapter upgrade safety gate (`scripts/ci/run-adapter-upgrade-safety-gate.sh`)
- ✅ Uploads governance/resilience evidence summaries as periodic artifacts

**Expected Results:**
- Governance contract suite remains green in periodic execution
- Drift/reconciliation fixtures remain deterministic
- Incident playbook fixture checks remain deterministic
- Advanced resilience fixture checks remain deterministic
- Partition-reconciliation fixture checks remain deterministic
- Adapter schema upgrade safety contract remains deterministic

---

### 3. SLSA Provenance (`slsa-provenance.yml`)

**Triggers:**
- Push to `main` branch
- Tag push (v*.*.*)
- Manual workflow dispatch

**Process:**
1. **Build Phase:**
   - Builds all modules with Java 25
   - Collects JAR artifacts
   - Generates SHA256 digests

2. **Provenance Generation:**
   - Uses `slsa-framework/slsa-github-generator@v2.0.0`
   - Generates **SLSA Build Level 3** provenance
   - Creates signed attestations

3. **Verification:**
   - Downloads artifacts and provenance
   - Installs SLSA verifier
   - Verifies provenance authenticity

**Outputs:**
- `ironbucket-provenance.intoto.jsonl` - SLSA attestation
- Verified artifacts with provenance
- Audit trail of build process

---

### 4. Docker Build and Scan (`docker-build.yml`)

**Triggers:**
- Push to `main` or `develop`
- Pull requests to `main`

**Per-Service Actions:**
- Sentinel-Gear
- Claimspindel
- Brazz-Nossel
- Buzzle-Vane

**Process:**
1. Build Maven artifacts
2. Create Docker images with Buildx
3. Scan images with **Trivy** (CRITICAL/HIGH vulnerabilities)
4. Scan images with **Grype** (Anchore)
5. Upload scan results to GitHub Security
6. Push images to GitHub Container Registry (main branch only)

**Image Registry:**
- `ghcr.io/<owner>/ironbucket-sentinel-gear:latest`
- `ghcr.io/<owner>/ironbucket-claimspindel:latest`
- `ghcr.io/<owner>/ironbucket-brazz-nossel:latest`
- `ghcr.io/<owner>/ironbucket-buzzle-vane:latest`

---

### 5. Release (`release.yml`)

**Triggers:**
- Tag push matching `v*.*.*` (e.g., `v1.0.0`)
- Manual workflow dispatch with version input

**Release Process:**

#### Phase 1: Validation
- ✅ Validates semantic version format
- ✅ Checks for clean working tree
- ✅ Verifies repository state
- ✅ Verifies roadmap E2E docs/workflow sync (`scripts/ci/verify-e2e-doc-sync.sh`)
- ✅ Verifies workflow test commands remain containerized (`scripts/ci/verify-containerized-tests-only.sh`)
- ✅ Verifies `main` branch-protection required checks (`scripts/ci/verify-main-branch-protection.sh`)
- ✅ Blocks release if required checks are not green on the release commit (`scripts/ci/verify-required-check-runs.sh`)

To run branch protection verification in strict mode locally:

```bash
GITHUB_TOKEN=<admin_token> \
GITHUB_REPOSITORY=ZiggiZagga/IronBucket \
BRANCH_PROTECTION_STRICT=true \
bash scripts/ci/verify-main-branch-protection.sh
```

If the token lacks admin permission to read branch protection, strict mode fails by design.

#### Phase 2: Testing
- ✅ Runs complete test suite (231 tests)
- ✅ Fails release if any test fails
- ✅ Runs core module suites in Docker containers only (`scripts/ci/run-core-module-tests-container.sh`)
- ✅ Validates test result XML files

#### Phase 3: Build
- ✅ Updates POM versions to release version
- ✅ Builds all modules
- ✅ Generates SHA256 and SHA512 checksums
- ✅ Collects release artifacts

#### Phase 4: Provenance
- ✅ Generates SLSA Build Level 3 provenance
- ✅ Signs attestations
- ✅ Links provenance to artifacts

#### Phase 5: Publish
- ✅ Creates GitHub Release
- ✅ Uploads JAR artifacts
- ✅ Uploads checksums
- ✅ Uploads SLSA provenance
- ✅ Generates release notes with verification instructions

#### Phase 6: Docker Images
- ✅ Builds Docker images for all services
- ✅ Tags images with:
  - Exact version (`v1.0.0`)
  - Major.minor (`1.0`)
  - Major (`1`)
  - `latest`
- ✅ Pushes to GitHub Container Registry

---

## 🔒 Security Features

### Supply-Chain Security (SLSA)

**SLSA Build Level 3 Compliance:**
- ✅ Build from source on hosted CI/CD
- ✅ Provenance generated by trusted builder
- ✅ Tamper-proof provenance signed with Sigstore
- ✅ Provenance includes build parameters and dependencies

**Verification:**
```bash
# Download SLSA verifier
curl -sSL https://github.com/slsa-framework/slsa-verifier/releases/download/v2.5.1/slsa-verifier-linux-amd64 -o slsa-verifier
chmod +x slsa-verifier

# Verify artifact
./slsa-verifier verify-artifact <artifact.jar> \
  --provenance-path ironbucket-provenance.intoto.jsonl \
  --source-uri github.com/ZiggiZagga/IronBucket \
  --source-tag v1.0.0
```

### Container Security

**Image Scanning:**
- **Trivy**: Fast, comprehensive vulnerability scanner
- **Grype**: Anchore's vulnerability scanner
- SARIF results uploaded to GitHub Security tab

**Image Hardening:**
- Eclipse Temurin Java 25 base images
- Multi-stage builds (build + runtime)
- Minimal attack surface
- Regular base image updates

### Dependency Security

**Automated Scanning:**
- OWASP Dependency Check (weekly)
- Fail build on CVSS ≥ 7
- Suppression file for false positives

### Code Quality

**Static Analysis:**
- SpotBugs for bug detection
- Checkstyle for code standards
- Continuous monitoring

---

## 📊 Monitoring & Observability

### GitHub Actions Insights

**Branch Protection:**
- Require passing CI checks
- Require code review
- No direct pushes to `main`

**Status Checks:**
- jclouds MinIO CRUD Gate ✅ (strict on all configured refs)
- Sentinel Roadmap Gate ✅
- Sentinel Behavioral Gate ✅ (strict on all configured refs)
- e2e-complete-suite ✅ (strict on all configured refs)
- Phase 2 observability infra gate ✅ (within e2e-complete-suite)
- Build and Test ✅
- Security Scanning ✅
- Docker Build ✅
- SLSA Provenance ✅

### Branch Protection Required Checks (main)

The following checks are required for merge into `main`:

- `jclouds MinIO CRUD Gate`
- `Sentinel Roadmap Gate`
- `Sentinel Behavioral Gate`
- `e2e-complete-suite`
- `Build and Test All Modules`

No policy exceptions are allowed for release-tag eligible changes.

### Gate Failure Ownership and Response SLA

- **Owner (code gate):** PR author + owning service maintainer
- **Owner (platform gate):** CI/platform maintainer on duty
- **Triage SLA:** same working day
- **Mitigation SLA:** restore green required checks before merge/tag

Minimal runbook sequence:

1. Open failed job logs and identify failing stage.
2. Reproduce locally using the same script/profile used by CI.
3. Apply minimal fix and re-run targeted gate locally.
4. Push fix and confirm required checks are green.

### Artifact Retention

| Artifact Type | Retention | Purpose |
|---------------|-----------|---------|
| Test Results | 30 days | Debugging test failures |
| Build Artifacts | 90 days | Historical builds |
| Security Reports | 30 days | Vulnerability tracking |
| Release Artifacts | Permanent | GitHub Releases |
| Docker Images | Indefinite | GHCR |

---

## 🚀 Usage

### Running Tests Locally

```bash
# Build shared contracts
cd Pactum-Scroll && mvn clean install

# Test individual module
cd Sentinel-Gear && mvn clean test

# Test all modules
for module in Sentinel-Gear Claimspindel Brazz-Nossel Buzzle-Vane; do
  cd $module && mvn clean test && cd ..
done
```

### Creating a Release

```bash
# Run production preflight before tagging
bash scripts/ci/release-preflight.sh

# Tag the release
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0

# CI/CD automatically:
# 1. Runs all tests
# 2. Builds artifacts
# 3. Generates SLSA provenance
# 4. Creates GitHub Release
# 5. Publishes Docker images
```

### Release Preflight (Local Gate)

```bash
# Java baseline + Sentinel roadmap + Sentinel integration
bash scripts/ci/release-preflight.sh

# Include main-branch protection policy verification
VERIFY_BRANCH_PROTECTION=true GITHUB_REPOSITORY=ZiggiZagga/IronBucket bash scripts/ci/release-preflight.sh

# Include full orchestrator if required by release policy
RUN_FULL_ORCHESTRATOR=true bash scripts/ci/release-preflight.sh
```

Expected result:
- Backend modules green
- Sentinel roadmap profile green
- Sentinel behavioral integration profile green
- Optional full orchestrator green when enabled

### Manual Workflow Dispatch

```bash
# Using GitHub CLI
gh workflow run release.yml -f version=v1.0.0

# Or via GitHub UI:
# Actions → Release → Run workflow → Enter version
```

---

## 🔧 Configuration

### Secrets Required

- `GITHUB_TOKEN` is used by default for standard workflow operations.
- `BRANCH_PROTECTION_TOKEN` is used by release validation for `scripts/ci/verify-main-branch-protection.sh` and should have repository administration read permission.

### Permissions

Workflows use **least-privilege permissions**:
- `contents: read` - Read repository
- `contents: write` - Create releases
- `packages: write` - Push Docker images
- `id-token: write` - SLSA provenance signing
- `security-events: write` - Upload SARIF

### Branch Protection (Recommended)

```yaml
main:
  required_status_checks:
    - Build and Test All Modules
    - jclouds MinIO CRUD Gate
    - Sentinel Roadmap Gate
    - Sentinel Behavioral Gate
    - e2e-complete-suite
    - Security Scanning
    - Docker Build
  require_pull_request: true
  required_approving_review_count: 1
  dismiss_stale_reviews: true
```

---

## 📈 Metrics

### Build Times (Approximate)

| Workflow | Duration |
|----------|----------|
| Build and Test | 5-8 minutes |
| Security Scan | 8-12 minutes |
| Docker Build | 10-15 minutes |
| SLSA Provenance | 6-10 minutes |
| Release (full) | 20-30 minutes |

### Resource Usage

- **Compute**: GitHub-hosted runners (ubuntu-latest)
- **Storage**: ~500 MB per workflow run
- **Network**: ~1 GB downloads (Maven dependencies, Docker layers)

---

## 🎯 Best Practices

### 1. Always Test Before Merge
- Run `mvn clean test` locally
- Ensure all 231 tests pass
- Check for new security vulnerabilities

### 2. Semantic Versioning
- Use format: `vMAJOR.MINOR.PATCH`
- Increment appropriately:
  - MAJOR: Breaking changes
  - MINOR: New features
  - PATCH: Bug fixes

### 3. Review Security Reports
- Check GitHub Security tab weekly
- Address HIGH/CRITICAL vulnerabilities
- Update dependencies regularly

### 4. Verify Releases
- Always verify SLSA provenance
- Check artifact checksums
- Test release artifacts before announcing

### 5. Monitor CI/CD Health
- Review failed workflow runs
- Keep workflows updated
- Optimize slow steps

---

## 🐛 Troubleshooting

### Build Failures

**Problem:** Maven build fails
**Solution:**
```bash
# Clear Maven cache
rm -rf ~/.m2/repository
mvn clean install -U
```

### Test Failures

**Problem:** Tests pass locally but fail in CI
**Solution:**
- Check Java version (must be 25)
- Review test logs in GitHub Actions
- Check for environment-specific issues

### SLSA Provenance Issues

**Problem:** Provenance generation fails
**Solution:**
- Ensure `id-token: write` permission
- Check SLSA Generator version compatibility
- Verify artifact digests are correct

### Docker Build Failures

**Problem:** Docker image build fails
**Solution:**
- Ensure JAR was built successfully
- Check Dockerfile syntax
- Verify base image availability

---

## 📚 References

- [SLSA Framework](https://slsa.dev/)
- [SLSA GitHub Generator](https://github.com/slsa-framework/slsa-github-generator)
- [GitHub Actions Security](https://docs.github.com/en/actions/security-guides)
- [Trivy Scanner](https://github.com/aquasecurity/trivy)
- [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/)

---

**Status:** ✅ Production-Ready CI/CD Pipeline  
**Last Updated:** March 13, 2026  
**Maintained By:** IronBucket Team
