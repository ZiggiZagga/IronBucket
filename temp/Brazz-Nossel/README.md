# 🔩 Brazz-Nossel

**Brazz-Nossel** is [IronBucket's](https://github.com/ZiggiZagga/IronBucket) precision-forged **S3-compatible proxy service**, built with Spring Cloud Gateway. It channels object storage traffic with identity awareness, policy-governed enforcement, and full audit traceability—one stream at a time.

> _Yes, we know it’s spelled “nozzle.” But this one’s special._

---

## 🚦 Core Responsibilities

- **Receive S3-compatible requests** (`GET /bucket/key`, `PUT`, `DELETE`, etc.)
- **Extract identity context** from [Sentinel-Gear](https://github.com/ZiggiZagga/Sentinel-Gear) via OIDC/JWT claims
- **Enforce Git-governed policies** by consulting the Policy Engine
- **Route valid requests** to S3 backends like MinIO, Ceph, or AWS S3
- **Emit structured audit logs** for every access decision

---

## 🧰 Tech Stack

| Layer        | Tooling                          |
|--------------|----------------------------------|
| Proxy Core   | Spring Cloud Gateway (WebFlux)   |
| Auth Context | OIDC tokens passed from upstream |
| Policy Calls | REST to `policy-engine` module   |
| S3 Routing   | AWS SDK v2 + dynamic targets      |
| Audit Logs   | Structured JSON via SLF4J        |

---

## 🧪 Dev Environment (Coming Soon)

To run Brazz-Nossel locally:

```bash
cd brazz-nossel
./gradlew bootRun
```

**Prerequisites:**
- [Sentinel-Gear](https://github.com/ZiggiZagga/Sentinel-Gear) running (handles OIDC login)
- Policy Engine accessible
- Upstream S3-compatible backend (e.g., MinIO)
- Sample policy repo loaded via Steel-Hammer

---

## 💡 Design Philosophy

**Secure by default.**  
**Observable in every step.**  
**Pluggable beyond buckets.**

Brazz-Nossel may start with S3, but it's designed to govern any object-like data flow—backed by Git, shaped by policy, and enforced by identity.

---

## 🌊 Coming Soon

- Dry-run mode: Simulate policy evaluation without forwarding
- Multi-target support: Dynamically route based on bucket origin
- Native metrics: Per-identity request counters and latencies
- Deny templates: JSON explanations for all rejections

---

Part of the **[IronBucket Project](https://github.com/ZiggiZagga/IronBucket)**:
- 🛠 Infrastructure: `steel-hammer/`
- 🛡 Gateway: [`sentinel-gear/`](https://github.com/ZiggiZagga/Sentinel-Gear)
- 🔩 Proxy: `brazz-nossel/`
- 🧠 Policy Engine: `policy-engine/`


