# 🛡️ IronBucket

**IronBucket** is a zero-trust, identity-aware proxy that wraps any S3-compatible object store with Git-managed, policy-as-code access control. Enforce fine-grained permissions using OIDC/OAuth2 identity, attribute-based rules, and GitOps-style auditability.

> 🔐 Secure by default.  
> 🧩 Pluggable with any object store.  
> 🧭 Governed by Git.

---

## 🚀 Why IronBucket?

| Feature | Description |
|--------|-------------|
| 🔐 **Zero-Trust Identity** | Authenticate with any OIDC-compliant provider ([Keycloak](https://github.com/keycloak/keycloak)) |
| 🎯 **Granular Access Control** | Write policies for paths, prefixes, groups, or object metadata |
| ⚙️ **S3-Compatible Proxy** | Drop-in for S3 APIs—backed by [MinIO](https://github.com/minio/minio), Ceph, AWS S3, and others |
| 🔁 **GitOps-Native Policies** | Declarative access rules live in Git—versioned, auditable, reviewable |
| 🪵 **Full Auditability** | Every request is logged; every decision is explainable |

---

## 🏗️ Architecture Diagram

```
    User[[User / Tool]] --> Gateway[IronBucket Gateway]
    Gateway --> PolicyEngine[Policy Engine]
    Gateway --> Git[Git Policies]
    PolicyEngine --> Proxy[S3 Proxy Layer]
    Proxy --> Store[S3-Compatible Store]
```

---


## 🧪 Quick Start (Local Dev)

### 🔧 Requirements

- Java 17+
- Docker & Docker Compose
- GitHub/GitLab account
- OIDC provider (Okta, Google, Dex, etc.)
  
---  

### ⏱ 60-Second Setup

```bash
git clone https://github.com/ZiggiZagga/ironbucket.git
cd ironbucket
./gradlew bootRun
```

Or use Docker Compose for full stack setup.
See docs/quickstart.md for more details.

---
## 📜 Writing Policies

IronBucket policies live in Git as YAML or JSON files. They define who can do *what* to *which* resource, under *which* conditions.

```yaml
id: read-access-to-project-x
description: Allow devs to read Project X data
subjects:
  - group: dev-team
actions: [GET, HEAD]
resources:
  - bucket: project-x-data
    prefix: /raw/
conditions:
  - attribute: region
    operator: equals
    value: eu-central-1
```

## 📋 Example Policy Templates

#### 🧪 Read-Only Access for QA Team
```yaml
id: qa-read-access
description: Allow QA team to read test data
subjects:
  - group: qa
actions: [GET, HEAD]
resources:
  - bucket: test-data
    prefix: /
```

#### 🔐 Restricted Upload for Developers (EU Only)

```yaml
id: dev-upload-eu
description: Developers can upload only to EU buckets
subjects:
  - group: dev
actions: [PUT, POST]
resources:
  - bucket: app-logs
    prefix: /dev/
conditions:
  - attribute: region
    operator: equals
    value: eu-central-1
```

#### ❌ Block Public Access
```yaml
id: deny-public-access
description: Explicitly deny anonymous access to all resources
subjects:
  - user: anonymous
actions: [*]
resources:
  - bucket: '*'
    prefix: '/'
effect: deny
```



---

✅ Coming soon:

- Policy testing & validation CLI

- Policy dry-run evaluation

---

## 📂 Project Structure

```
ironbucket/
├── gateway-service/     # OIDC auth, token parsing
├── policy-engine/       # Policy evaluation core
├── s3-proxy/            # S3 API proxy layer
├── audit-service/       # Logs, traces, decisions
├── config/              # Shared secrets, certs, constants
├── infra/               # Docker, GitOps templates
└── docs/                # Guides, diagrams, samples
```
---

🤝 Contributing
We love contributors! Check out CONTRIBUTING.md and browse our open issues.

---

📝 License


---

🌐 Community
💬 Discussions

🧵 Slack/Discord – coming soon

---
