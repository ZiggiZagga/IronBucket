# 🧠 Claimspindel

**Claimspindel** is a smart, claims-aware gateway that inspects identity tokens at the edge to route, protect, and enrich incoming requests—driven entirely by the user’s JWT.

> _It doesn’t just forward traffic—it interprets identity and orchestrates secure, dynamic request flow._

---

## 🚀 Overview

Claimspindel acts as a **JWT-aware routing hub** in your IronBucket ecosystem. It validates bearer tokens at ingress, makes nuanced routing decisions based on claims (like `tenant`, `region`, or `roles`), and applies pre-routing access policies—ensuring that only entitled calls reach your inner services.

---

## 🧰 Core Responsibilities

### 🔐 OAuth2 Resource Server
- Validates incoming tokens using JWKS or token introspection.
- Enforces token structure, expiration, and signature constraints.

### 🧬 Claims-Based Routing
- Dynamically resolves downstream targets based on:
  - `region: eu-central-1` → `brazz-nossel-eu`
  - `roles: admin` → rewrite path to `/admin/**`
  - `tenant: acme` → tenant-scoped service mesh

### 📎 Smart Resolution via Eureka + Predicates
- No hardcoded paths—routing adapts based on service registration and claim context.

### 🔍 Policy Delegation
- Optional pre-flight checks using dry-run enforcement, tagging, or access simulation.

### 🛡️ Perimeter Defense
- Rejects unauthorized requests *before* they hit downstream stacks.
- Emits structured error models for debugging and compliance.

---

## 📊 Observability & Enrichment

- Adds tags, headers, or labels based on token content.
- Optional tracing with identity context for downstream metrics.

---

## 🌍 Use Cases

- Identity-based traffic segmentation
- Tenant-aware service composition
- Fine-grained access enforcement at edge
- Smart routing in multi-region, multi-role ecosystems

---

## 🧪 Development Mode (Optional)

If enabled, dev builds may expose:
- Token debugger / introspector
- Routing dry-run simulator
- Local claim override UI

---


## 📄 License



---



