# 🗺️ IronBucket Documentation Quick Navigator

**Use this page to find exactly what you need in 30 seconds**

---

## ❓ What Do You Need?

### 🚀 "I want to get started immediately"
**→ Read**: [START.md](START.md) (10 min)
```bash
docker-compose -f steel-hammer/docker-compose-steel-hammer.yml up -d
curl http://localhost:8080/actuator/health
```

---

### 🔧 "Something is broken, help!"
**→ Read**: [TROUBLESHOOTING.md](TROUBLESHOOTING.md) (5-15 min)
- Services won't start? [→ See here](TROUBLESHOOTING.md#startup-issues)
- Policy error? [→ See here](TROUBLESHOOTING.md#policy-errors)
- S3 timeout? [→ See here](TROUBLESHOOTING.md#s3-connection-issues)
- Auth failed? [→ See here](TROUBLESHOOTING.md#authentication-issues)

---

### 🏗️ "I want to understand the architecture"
**→ Read**: [ARCHITECTURE.md](ARCHITECTURE.md) (30 min)
- **Quick version** (5 min): [Sections](#architecture-quick-summary)
  - [Layered Architecture](ARCHITECTURE.md#-layered-architecture)
  - [Request Flow](ARCHITECTURE.md#-request-flow---complete-walkthrough)
  - [Service Dependencies](ARCHITECTURE.md#-service-dependencies)

---

### 💻 "I want to contribute code"
**→ Read**: [CONTRIBUTING.md](CONTRIBUTING.md) (20 min)
- **Setup**: [Installation](CONTRIBUTING.md#step-2-install-development-dependencies)
- **Code Style**: [Guidelines](CONTRIBUTING.md#-code-style--standards)
- **Pull Requests**: [Process](CONTRIBUTING.md#-pull-request-process)
- **Testing**: [Requirements](CONTRIBUTING.md#-testing-requirements)

---

### 📚 "I want to improve documentation"
**→ Read**: [DOCS-AUDIT-AND-IMPROVEMENT-PLAN.md](DOCS-AUDIT-AND-IMPROVEMENT-PLAN.md) (25 min)
- [Improvement Checklist](DOCS-AUDIT-AND-IMPROVEMENT-PLAN.md#-documentation-improvement-checklist)
- [Phase Timeline](DOCS-AUDIT-AND-IMPROVEMENT-PLAN.md#-implementation-timeline)

---

### 🚢 "I want to deploy to production"
**→ Read**: [docs/DEPLOYMENT-GUIDE.md](docs/DEPLOYMENT-GUIDE.md) (30 min)
- Also see: [ARCHITECTURE.md - Security Model](ARCHITECTURE.md#-security-model)

---

### 📈 "I want to see the project roadmap"
**→ Read**: [COMMUNITY-ROADMAP.md](COMMUNITY-ROADMAP.md) (15 min)
- Technical details: [ROADMAP.md](ROADMAP.md)

---

### 🔐 "I want to understand authentication & policies"
**→ Read in this order**:
1. [ARCHITECTURE.md - Security Model](ARCHITECTURE.md#-security-model) (5 min)
2. [docs/identity-model.md](docs/identity-model.md) (15 min)
3. [docs/policy-schema.md](docs/policy-schema.md) (20 min)

---

### 🧪 "I want to understand testing"
**→ Read in this order**:
1. [docs/test-suite-phase2.md](docs/test-suite-phase2.md) - Test design
2. [docs/testing/TESTING-QUICK-START.md](docs/testing/TESTING-QUICK-START.md) - Run tests locally

---

### 📊 "I want to see the current status"
**→ Read**:
- [FINAL-VERIFICATION-REPORT.md](FINAL-VERIFICATION-REPORT.md) - What's done ✅
- [COMMUNITY-ROADMAP.md](COMMUNITY-ROADMAP.md) - What's next

---

## 📑 Complete Documentation Map

### **Foundation** (Start here)
```
START.md                    ← How to get running
README.md                   ← Project overview
DOCS-INDEX.md               ← Full documentation map
```

### **System Design** (Understand the architecture)
```
ARCHITECTURE.md             ← Complete system design
  ├─ docs/identity-model.md
  ├─ docs/identity-flow.md
  ├─ docs/policy-schema.md
  ├─ docs/s3-proxy-contract.md
  └─ docs/gitops-policies.md
```

### **Getting Started by Role** (Pick your path)

#### 👤 **Users & Operators**
```
START.md
  ↓
TROUBLESHOOTING.md
  ↓
docs/DEPLOYMENT-GUIDE.md
  ↓
steel-hammer/LGTM-SETUP-GUIDE.md (observability)
```

#### 🏗️ **Architects & Designers**
```
ARCHITECTURE.md
  ↓
docs/policy-schema.md
  ↓
docs/identity-model.md
  ↓
COMMUNITY-ROADMAP.md
```

#### 💻 **Developers & Contributors**
```
ARCHITECTURE.md
  ↓
CONTRIBUTING.md
  ↓
docs/test-suite-phase2.md
  ↓
TROUBLESHOOTING.md
```

#### 🎯 **Project Leaders & Maintainers**
```
COMMUNITY-ROADMAP.md
  ↓
DOCS-AUDIT-AND-IMPROVEMENT-PLAN.md
  ↓
FINAL-VERIFICATION-REPORT.md
```

---

## 🔍 Search by Topic

### Authentication & Identity
- [docs/identity-model.md](docs/identity-model.md) - JWT & claims
- [docs/identity-flow.md](docs/identity-flow.md) - Auth flow
- [CONTRIBUTING.md - Auth Issues](CONTRIBUTING.md#authentication-issues)
- [TROUBLESHOOTING.md - Auth Issues](TROUBLESHOOTING.md#-authentication-issues)

### Policies & Access Control
- [docs/policy-schema.md](docs/policy-schema.md) - Policy language
- [ARCHITECTURE.md - Security Model](ARCHITECTURE.md#-security-model)
- [TROUBLESHOOTING.md - Policy Errors](TROUBLESHOOTING.md#-policy-errors)

### S3 & Storage
- [docs/s3-proxy-contract.md](docs/s3-proxy-contract.md) - API spec
- [ARCHITECTURE.md - S3 Proxy Layer](ARCHITECTURE.md#3a-s3-proxy)
- [TROUBLESHOOTING.md - S3 Issues](TROUBLESHOOTING.md#-s3-connection-issues)

### Testing & Quality
- [docs/test-suite-phase2.md](docs/test-suite-phase2.md) - Test design
- [docs/testing/TESTING-QUICK-START.md](docs/testing/TESTING-QUICK-START.md) - Run tests
- [CONTRIBUTING.md - Testing Requirements](CONTRIBUTING.md#-testing-requirements)

### Deployment & Operations
- [docs/DEPLOYMENT-GUIDE.md](docs/DEPLOYMENT-GUIDE.md) - Production deploy
- [steel-hammer/LGTM-SETUP-GUIDE.md](steel-hammer/LGTM-SETUP-GUIDE.md) - Monitoring
- [TROUBLESHOOTING.md - Startup](TROUBLESHOOTING.md#-startup-issues)

### Monitoring & Observability
- [steel-hammer/LGTM-SETUP-GUIDE.md](steel-hammer/LGTM-SETUP-GUIDE.md) - Full setup
- [ARCHITECTURE.md - Observability](ARCHITECTURE.md#-observability-architecture)

### Development & Contributing
- [CONTRIBUTING.md](CONTRIBUTING.md) - Full guide
- [ARCHITECTURE.md](ARCHITECTURE.md) - System design
- [TROUBLESHOOTING.md - Debugging](TROUBLESHOOTING.md#-diagnostic-commands)

### Roadmap & Planning
- [COMMUNITY-ROADMAP.md](COMMUNITY-ROADMAP.md) - Community vision
- [ROADMAP.md](ROADMAP.md) - Technical roadmap
- [DOCS-AUDIT-AND-IMPROVEMENT-PLAN.md](DOCS-AUDIT-AND-IMPROVEMENT-PLAN.md) - Docs roadmap

---

## ⏱️ Reading Time Guide

### Quick Reads (5-10 min)
- [START.md](START.md)
- [README.md](README.md)
- [COMMUNITY-ROADMAP.md](COMMUNITY-ROADMAP.md) - Summary sections

### Medium Reads (15-20 min)
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Problem sections
- [CONTRIBUTING.md](CONTRIBUTING.md) - Code style section
- [docs/identity-model.md](docs/identity-model.md)

### Long Reads (25-30+ min)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [CONTRIBUTING.md](CONTRIBUTING.md) - Full guide
- [docs/DEPLOYMENT-GUIDE.md](docs/DEPLOYMENT-GUIDE.md)
- [DOCS-AUDIT-AND-IMPROVEMENT-PLAN.md](DOCS-AUDIT-AND-IMPROVEMENT-PLAN.md)
- [docs/test-suite-phase2.md](docs/test-suite-phase2.md)

---

## 🎯 Quick Checklists

### Setting Up Development Environment
```bash
1. Fork/clone repo
2. Read: CONTRIBUTING.md - Setup section
3. Run: cd steel-hammer && docker-compose up -d
4. Verify: curl http://localhost:8080/actuator/health
5. Start coding!
```

### Deploying to Production
```bash
1. Read: docs/DEPLOYMENT-GUIDE.md
2. Check: ARCHITECTURE.md - Security Model
3. Plan: docs/test-suite-phase2.md
4. Setup: steel-hammer/LGTM-SETUP-GUIDE.md
5. Monitor: Create Grafana dashboards
```

### Fixing a Problem
```bash
1. Describe: What error did you see?
2. Find: TROUBLESHOOTING.md section
3. Diagnose: Run diagnostic commands
4. Fix: Follow resolution steps
5. Verify: Check health endpoints
```

### Writing Good Code
```bash
1. Read: CONTRIBUTING.md - Code Style
2. Implement: Feature/fix
3. Test: npm test && mvn test
4. Format: Follow style guide
5. Submit: PR with description
```

---

## 🔗 Cross-Reference Index

### Files Frequently Referenced Together

| Topic | Documents to Read Together |
|-------|---|
| **Authentication** | identity-model.md + identity-flow.md + TROUBLESHOOTING.md |
| **Policies** | policy-schema.md + ARCHITECTURE.md + TROUBLESHOOTING.md |
| **Deployment** | DEPLOYMENT-GUIDE.md + ARCHITECTURE.md + LGTM-SETUP-GUIDE.md |
| **Development** | CONTRIBUTING.md + ARCHITECTURE.md + test-suite-phase2.md |
| **Operations** | ARCHITECTURE.md + DEPLOYMENT-GUIDE.md + TROUBLESHOOTING.md |
| **Testing** | test-suite-phase2.md + CONTRIBUTING.md + TESTING-QUICK-START.md |

---

## 💡 Pro Tips

### 🎯 Finding Information Fast
1. Use **Cmd+F (Ctrl+F)** to search within documents
2. Check DOCS-INDEX.md categories
3. Use **"Related Documents"** sections at bottom of pages
4. Check **Table of Contents** headings

### 📖 Reading Strategy
1. **Skim the title & audience** (1 min)
2. **Read overview/TL;DR** (2 min)
3. **Deep dive if needed** (5-20 min)
4. **Reference links** for details

### 🔄 Staying Updated
- Check [COMMUNITY-ROADMAP.md](COMMUNITY-ROADMAP.md) monthly
- Star the repo on GitHub
- Watch for documentation updates
- Submit feedback via GitHub Issues

---

## ❓ Still Can't Find What You Need?

### Last Resort Options
1. **Search GitHub Issues**: Might be answered before
2. **Ask on GitHub Discussions**: Community can help
3. **Check Service README files**: [Brazz-Nossel](Brazz-Nossel/README.md), [Buzzle-Vane](Buzzle-Vane/README.md), [Claimspindel](Claimspindel/README.md), [Sentinel-Gear](Sentinel-Gear/README.md)
4. **Look at code examples**: In [docs/testing/](docs/testing/) folder

---

## 📋 Document Maintenance

| Document | Owner | Review Cycle |
|----------|-------|---|
| START.md | @ZiggiZagga | Monthly |
| ARCHITECTURE.md | @ZiggiZagga | Quarterly |
| CONTRIBUTING.md | @ZiggiZagga | Quarterly |
| TROUBLESHOOTING.md | Community | As-needed |
| DOCS-INDEX.md | Documentation Team | Monthly |

---

**Last Updated**: January 15, 2026  
**Status**: ✅ NAVIGATION COMPLETE

**Next**: Bookmark this page for quick access! 🔖
