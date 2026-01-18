# 🚀 IronBucket Test Reporting - Quick Reference

**5-Minute Guide** | Keep this handy!

---

## Run Tests

```bash
# All tests
bash scripts/comprehensive-test-reporter.sh --all

# Specific types
bash scripts/comprehensive-test-reporter.sh --backend   # Maven only
bash scripts/comprehensive-test-reporter.sh --e2e       # E2E only
bash scripts/comprehensive-test-reporter.sh --security  # Security only

# With details
bash scripts/comprehensive-test-reporter.sh --all --verbose
```

---

## View Results

```bash
# Quick summary (30 seconds)
cat test-results/reports/LATEST-SUMMARY.md

# Action items
cat test-results/reports/test-report-*-todos.md

# Full report
cat test-results/reports/test-report-*.md

# Logs
ls test-results/logs/
```

---

## Priority Levels

| Symbol | Priority | Deadline | When |
|--------|----------|----------|------|
| 🔴 | CRITICAL | Same Day | Security modules, NetworkPolicy, credentials |
| 🟠 | HIGH | 1-2 Days | Gateway, E2E, security bypass |
| 🟡 | MEDIUM | 3 Days | Service discovery, non-critical |
| 🟢 | LOW | 1 Week | Documentation, optional |

---

## Common Tasks

### Fix Critical Issues

```bash
# 1. Deploy NetworkPolicies
kubectl create namespace ironbucket
kubectl apply -f docs/k8s-network-policies.yaml

# 2. Verify
kubectl get networkpolicies -n ironbucket

# 3. Rerun tests
bash scripts/comprehensive-test-reporter.sh --security
```

### Update Test Scripts

```bash
# Change from direct MinIO access
endpoint_url='http://steel-hammer-minio:9000'  # ❌ BAD

# To Brazz-Nossel gateway
endpoint_url='http://steel-hammer-brazz-nossel:8082'  # ✅ GOOD
```

---

## Report Files

```
test-results/
├── reports/
│   ├── LATEST-SUMMARY.md          ← Start here (30s read)
│   ├── test-report-*.md           ← Full details (5min)
│   └── test-report-*-todos.md     ← Copy to tasks
└── logs/
    ├── backend-*.log              ← Maven logs
    ├── e2e-*.log                  ← E2E logs
    └── security-*.log             ← Security logs
```

---

## Troubleshooting

### No tests run

```bash
# Make executable
chmod +x scripts/comprehensive-test-reporter.sh

# Check Maven
mvn --version

# Check modules
ls temp/*/pom.xml
```

### Permission denied

```bash
chmod +x scripts/comprehensive-test-reporter.sh
```

### Security tests fail

Expected in development! Fix for production:
- NetworkPolicies: `kubectl apply -f docs/k8s-network-policies.yaml`
- Credentials: Implement Vault (see roadmap)
- Test bypass: Update scripts to use Brazz-Nossel

---

## CI/CD Integration

### GitHub Actions

```yaml
- name: Run tests
  run: bash scripts/comprehensive-test-reporter.sh --all

- name: Upload reports
  uses: actions/upload-artifact@v3
  with:
    name: test-reports
    path: test-results/reports/
```

---

## Quick Commands Cheat Sheet

```bash
# Full test suite
bash scripts/comprehensive-test-reporter.sh --all

# Quick check
cat test-results/reports/LATEST-SUMMARY.md

# See todos
cat test-results/reports/test-report-*-todos.md | grep "🔴\|🟠"

# View logs
tail -100 test-results/logs/backend-*.log
```

---

## Need Help?

📖 **Full Documentation**: [TEST-REPORTING-SYSTEM.md](TEST-REPORTING-SYSTEM.md)  
🏗️ **Architecture**: [ARCHITECTURE-ASSESSMENT-2026.md](ARCHITECTURE-ASSESSMENT-2026.md)  
🗺️ **Roadmap**: [PRODUCTION-READINESS-ROADMAP.md](PRODUCTION-READINESS-ROADMAP.md)  
🔐 **Security**: [security/MINIO-ISOLATION-AUDIT.md](security/MINIO-ISOLATION-AUDIT.md)

---

**Version**: 1.0 | **Updated**: January 18, 2026
