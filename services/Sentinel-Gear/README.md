# 🛡️ Sentinel-Gear Gateway

**Sentinel-Gear-Gateway** is a Spring Cloud Gateway–based service hardened for zero-trust environments. Acting as the secure entrypoint to the *IronBucket* ecosystem, it enforces identity-aware policies, integrates with GitOps workflows, and transforms your object access layer into a governed, auditable flow.

- 🚀 **Built on Spring Cloud Gateway**
- 🔐 **OIDC-ready, zero-trust by default**
- ⚙️ **Policy-driven traffic control for S3-compatible storage**

---

## 🚀 Why Sentinel-Gear?

| Feature                     | Description                                                                 |
|----------------------------|-----------------------------------------------------------------------------|
| 🛡️ Spring-Powered Gateway   | Leverages Spring Cloud Gateway for reactive, scalable request routing       |
| 📜 Git-Managed Policies     | Authorize requests using declarative rules managed in Git                  |
| 🔐 OIDC Identity Enforcement | Supports any compliant provider (e.g., Keycloak, Auth0)                    |
| 🔄 Plug-and-Play with IronBucket | Routes cleanly into object storage setups via IronBucket             |
| 📊 Built-In Telemetry Hooks | Metrics and logs designed for observability stacks like Prometheus + Loki  |

---

## Presigned Request Security Configuration

Sentinel-Gear enforces presigned request security at runtime using nonce replay protection, TTL validation, signed-header checks, and HMAC verification.

### Environment Variables

- `IRONBUCKET_SECURITY_PRESIGNED_ENABLED` (default: `true`)
- `IRONBUCKET_SECURITY_PRESIGNED_SECRET` (required when enabled)
- `IRONBUCKET_SECURITY_PRESIGNED_NONCE_TTL` (default: `PT5M`)

### Fail-Fast Startup Rules

- Startup fails if `IRONBUCKET_SECURITY_PRESIGNED_ENABLED=true` and `IRONBUCKET_SECURITY_PRESIGNED_SECRET` is missing/blank.
- Startup fails if `IRONBUCKET_SECURITY_PRESIGNED_NONCE_TTL` is zero/negative.

### Signed Request Headers Expected by Gateway

- `X-IronBucket-Presigned-Signature`
- `X-IronBucket-Presigned-Nonce`
- `X-IronBucket-Presigned-Expires`
- `X-IronBucket-Presigned-SignedHeaders`

### Example (Docker/CI)

```bash
export IRONBUCKET_SECURITY_PRESIGNED_ENABLED=true
export IRONBUCKET_SECURITY_PRESIGNED_SECRET="replace-with-strong-32-byte-secret"
export IRONBUCKET_SECURITY_PRESIGNED_NONCE_TTL=PT5M
```
