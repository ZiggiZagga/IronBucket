# 🛡️ IronBucket

**IronBucket** is a zero-trust, identity-aware proxy that wraps any S3-compatible object store with Git-managed, policy-as-code access control. Enforce fine-grained permissions using OIDC/OAuth2 identity, attribute-based rules, and GitOps-style auditability.

> 🔐 Secure by default.  
> 🧩 Pluggable with any object store.  
> 🧭 Governed by Git.

---

## 🧩 What's Strong About IronBucket ✨

| **Aspect** | **Why it matters** |
|-----------|--------------------|
| **GitOps-native policy store** | Treating access rules like code—branches, PRs, rollbacks—pulls security into the engineering workflow. No more brittle IAM JSON or scattered ACLs. |
| **Zero-trust, identity-aware proxy** | By terminating OIDC/OAuth at the gateway, IronBucket decouples identity from the store’s own ACL model and provides a unified RBAC/ABAC layer across S3, MinIO, Ceph, etc. |
| **Drop-in S3 API compatibility** | Keeping the wire protocol untouched means zero rewrites. It works just like Envoy's service mesh adoption playbook. |
| **Clear inspiration from proven OSS projects** | IronBucket borrows branching from Project Nessie and tag-based enforcement from Polaris—standing on solid OSS shoulders rather than inventing in a vacuum. |

---

## 🧠 Open Engineering Challenges (Join the Mission)

🚧 This project is completely new almost no code is written yet. If you’ve ever wanted to help shape something from day zero, **now’s your chance**.

Here are some of the toughest, most rewarding problems waiting for builders like you:

| **Challenge** | **Why it matters & how you could help** |
|---------------|-----------------------------------------|
| ⚙️ **Performance tuning** | Every object fetch includes auth + policy evaluation. Help benchmark latency, optimize in-memory caching, and explore async audit strategies. |
| 🏗️ **High availability (HA)** | A gateway outage shouldn't mean a data outage. Design resilient HA topologies using stateless pods, load balancers, or sidecar approaches. |
| 🛡️ **Policy engine evolution** | Should we use YAML? Rego? Cedar? Help shape the policy language, embed a battle-tested engine, and unlock tooling support. |
| 🏷️ **Metadata & tag enforcement** | ABAC requires clean, immutable tags. Build CLI tools or upload hooks to guarantee metadata consistency and prevent drift. |
| 🏢 **Multi-tenant isolation** | Secure SaaS-style deployments need namespacing, quotas, and noisy-neighbor controls. Architect strong tenant boundaries from the ground up. |

> 💡 Want to leave your mark on open-source data governance? **IronBucket is your forge.**

---

## 🛠️ Quick Wins to Tighten the Story

- 📘 **Publish a threat model**: Diagram trust boundaries (client → gateway → policy engine → store) and highlight mitigations.
- 🧪 **Policy dry-run mode**: Let teams simulate access before merging changes to `main`.
- 💻 **CLI for dev laptops**: A lightweight dev proxy to test policies locally without prod risk.
- 🧩 **Pluggable storage adapters**: Start with S3/MinIO, but leave room for Wasabi, Backblaze, or future targets.
- 🧱 **Roadmapped integrations**: Explicitly support Keycloak/Auth0, GitHub/GitLab, and CI/CD hooks so users know what’s stable.

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
cd ironbucket/steel-hammer
docker compose -f docker-compose-steel-hammer.xml up --build
```
---

### 🔗 Gateway Service

The gateway handles OIDC authentication, token parsing, and identity-aware routing. You can explore its code here: 👉 [**Sentinel-Gear: Gateway Repository**](https://github.com/ZiggiZagga/Sentinel-Gear)

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

## **Goto Production fast strategy**

### 🧬 From **[Project Nessie](https://github.com/projectnessie/nessie)**
**Git-style branching & commit semantics**  
- ✳️ _Inspiration_: Nessie’s Git-like model for data  
- ✅ _How we use it_: Apply similar mechanics to version **access control policies**—supporting policy branches (`dev`, `stage`, `prod`), commit logs, and rollback

**Commit metadata & audit**  
- ⛏️ _Inspiration_: Nessie tracks user actions across branches  
- ✅ _IronBucket fit_: Record "who edited what" in policy changes for traceability

---

### 🛡️ From **[Apache Polaris](https://github.com/apache/polaris)**
**Fine-grained permissions + federation model**  
- ✳️ _Inspiration_: Polaris has multi-tenant RBAC and federated query layers  
- ✅ _IronBucket fit_: Adapt its role definition granularity and hierarchical access controls for **multi-project S3 namespaces**

**Attribute-tag-aware enforcement**  
- ⛏️ _Inspiration_: Data tags + policy enforcement at query time  
- ✅ _IronBucket fit_: We apply tags to objects and enforce ABAC rules at access time (e.g., `user.region == object.tag.region`)

---

### 🧭 From **[Unity Catalog](https://github.com/unitycatalog/unitycatalog)** (conceptual since it’s closed-source)
**Centralized governance with identity awareness**  
- ✳️ _Inspiration_: Global identity-based permissions across workspaces  
- ✅ _IronBucket fit_: Global RBAC/ABAC rule definitions, synced across services via Git, linked to real identity provider attributes

**Lineage-as-a-feature**  
- While full lineage isn’t core to us, we _could_ log object usage and produce lineage-style metadata for access—for audit or downstream triggers

---

### 🚀 From **[Gravitino](https://github.com/apache/gravitino)**
**Schema-level metadata & governance layer**  
- ✳️ _Inspiration_: Gravitino abstracts metadata across catalogs  
- ✅ _IronBucket fit_: Although we’re staying out of table land, **Gravitino’s catalog of governance objects** could inspire our **policy object model**—defining storage buckets, roles, and constraints in a unified structure

---

### 🧩 Synthesis for IronBucket
If we align all that into our domain, here's what we'd implement:
- Git-backed policy store with Nessie-like branches and commits
- Attribute-driven access control via Polaris-style tags + ABAC
- Unified identity layer inspired by Unity Catalog, aware of OIDC roles, groups, entitlements
- Governance object graph modeled a bit like Gravitino, but constrained to buckets, prefixes, and roles—not tables or schemas

---

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
