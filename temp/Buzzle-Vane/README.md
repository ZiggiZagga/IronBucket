# 🧭 Buzzle-Vane

**Buzzle-Vane** (*codename: Brumm-Schaufel*) is IronBucket’s Eureka-backed discovery client—a lightweight, identity-aware registry companion that keeps services visible, reachable, and humming in sync.

---

## 🚦 Core Responsibilities

- Register IronBucket components with Eureka
- Resolve live service instances for routing and policy enforcement
- Propagate identity context for downstream consumers
- Emit structured logs for discovery events and health checks

---

## 🧰 Tech Stack

| Layer             | Tooling                      |
|------------------|------------------------------|
| Discovery Client | Spring Cloud Eureka Client   |
| Identity Context | OIDC via Sentinel-Gear       |
| Config           | GitOps-style YAML via Steel-Hammer |
| Logging          | Structured JSON via SLF4J    |

---

## 🧪 Dev Environment

To run Buzzle-Vane locally:

```bash
cd buzzle-vane
./gradlew bootRun
