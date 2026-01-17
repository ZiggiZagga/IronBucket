# GitHub Actions Workflows

This directory contains the CI/CD pipeline workflows for IronBucket.

## 🚀 Workflows Overview

### 1. `build-and-test.yml`
**Purpose:** Continuous Integration - Build and test all modules

**Triggers:**
- Push to `main` or `develop`
- Pull requests to `main` or `develop`

**What it does:**
- Builds Pactum-Scroll (shared contracts)
- Compiles and tests all microservices
- Runs 231 unit tests
- Uploads test results and artifacts

**Expected outcome:** All tests passing ✅

---

### 2. `security-scan.yml`
**Purpose:** Security scanning and code quality

**Triggers:**
- Push to `main` or `develop`
- Pull requests to `main`
- Weekly schedule (Mondays)

**Scans:**
- **OWASP Dependency Check** - CVE scanning
- **SpotBugs** - Static analysis
- **Checkstyle** - Code style
- **TruffleHog** - Secret detection

**Expected outcome:** No critical vulnerabilities ✅

---

### 3. `slsa-provenance.yml`
**Purpose:** Generate SLSA Build Level 3 provenance

**Triggers:**
- Push to `main`
- Tag creation (`v*`)
- Manual dispatch

**What it does:**
- Builds all artifacts
- Generates SHA256 digests
- Creates signed SLSA provenance
- Verifies provenance authenticity

**Expected outcome:** Verifiable supply-chain attestation ✅

---

### 4. `docker-build.yml`
**Purpose:** Build and scan Docker images

**Triggers:**
- Push to `main` or `develop`
- Pull requests to `main`

**What it does:**
- Builds Docker images for all services
- Scans with Trivy and Grype
- Uploads scan results to GitHub Security
- Pushes images to GHCR (main only)

**Expected outcome:** Secure container images ✅

---

### 5. `release.yml`
**Purpose:** Automated release pipeline

**Triggers:**
- Tag creation (`v*.*.*`)
- Manual dispatch with version

**Release process:**
1. ✅ Validate version and tests
2. ✅ Build release artifacts
3. ✅ Generate SLSA provenance
4. ✅ Create GitHub Release
5. ✅ Build and push Docker images

**Expected outcome:** Complete release with provenance ✅

---

## 🔒 Security Features

### SLSA Build Level 3
- Provenance signed with Sigstore
- Tamper-proof build attestations
- Verifiable artifact integrity

### Container Security
- Trivy vulnerability scanning
- Grype vulnerability scanning
- SARIF results in GitHub Security

### Dependency Security
- OWASP Dependency Check
- Weekly automated scans
- Fail on CVSS ≥ 7

### Secret Protection
- TruffleHog scanning
- Git history analysis
- Verified secrets only

---

## 📊 Status Badges

Add these to your README:

```markdown
![Build](https://github.com/ZiggiZagga/IronBucket/workflows/Build%20and%20Test/badge.svg)
![Security](https://github.com/ZiggiZagga/IronBucket/workflows/Security%20Scanning/badge.svg)
![Docker](https://github.com/ZiggiZagga/IronBucket/workflows/Docker%20Build%20and%20Scan/badge.svg)
```

---

## 🔧 Configuration

### Required Secrets
**None!** All workflows use `GITHUB_TOKEN` (auto-provided).

### Permissions
Workflows use least-privilege permissions:
- `contents: read/write`
- `packages: write`
- `id-token: write` (SLSA)
- `security-events: write`

---

## 📚 Documentation

For detailed information, see [CI-CD-PIPELINE.md](../docs/CI-CD-PIPELINE.md).

---

**Status:** ✅ Production-Ready  
**SLSA Level:** 3  
**Last Updated:** January 17, 2026
