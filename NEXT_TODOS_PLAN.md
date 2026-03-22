# Next TODOs Plan

## Goal
Stable, fully-containerized end-to-end run with green status for GraphQL + UI + Observability.

## Session Summary (2026-03-22)
This session focused on diagnosing and fixing the TLS handshake failure between Sentinel-Gear (Spring
Cloud Gateway) and Graphite-Forge (DGS GraphQL backend).

### Root Cause Found: PKCS12 + Disabled RSA Cipher Suites
The JVM policy (`jdk.tls.disabledAlgorithms`) disables `TLS_RSA_*` cipher suites. Graphite-Forge was
configured with a PKCS12 keystore. When JSSE attempted TLS, it tried RSA key-exchange ciphers (disabled)
and found no common cipher suite → `HANDSHAKE_FAILURE (alert 40)`.

### Fix Applied: PEM-Based TLS for Graphite-Forge
Switched Graphite from PKCS12 to PEM-based SSL. Spring Boot 3.x supports `server.ssl.certificate` +
`server.ssl.certificate-private-key`. PEM mode allows JSSE to use `ECDHE_RSA` ciphers (not disabled).

**Current state of `docker-compose-steel-hammer.yml` for graphite-forge:**
```yaml
- "SERVER_SSL_ENABLED=true"
- "SERVER_SSL_CERTIFICATE=file:/vault-pki-certs/services/graphite-forge/fullchain.crt"
- "SERVER_SSL_CERTIFICATE_PRIVATE_KEY=file:/vault-pki-certs/services/graphite-forge/tls.key"
- "SERVER_SSL_KEYSTORE="            # explicitly cleared
- "SERVER_SSL_KEYSTORE_PASSWORD="   # explicitly cleared
- "SERVER_SSL_KEYSTORE_TYPE="       # explicitly cleared
- "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG"   # TODO: remove after fix confirmed
```

### Also Fixed: PKI Init Script Naming Contract
`steel-hammer/vault/init-vault-pki.sh` patched to also write `intermediate-ca.crt` and `root-ca.crt`
as compatibility copies alongside the existing `issuing-ca.crt` / `ca-chain.crt`.

### Verification: TLS Handshake Now Succeeds
- `No X.509 cert selected for RSA` error gone.
- Direct call from Sentinel `→ Graphite /actuator/health` returns HTTP 200 with valid JWT.
- Direct `POST /graphql` from within the network returns HTTP 200.

### Remaining Issue: Sentinel Proxy → Graphite `/graphql` Returns "Connection Prematurely Closed"
After TLS handshake succeeds, requests proxied via Spring Cloud Gateway to Graphite still fail with:
```
Connection prematurely closed BEFORE response
HTTP 500 from Sentinel
```
Suspected cause: **HTTP/2 vs HTTP/1.1 codec mismatch** between Spring Cloud Gateway (WebClient, which
may negotiate h2 via ALPN) and Graphite-Forge's Netty server (which may not advertise h2 in ALPN).

---

## Priority 1: Resolve Sentinel → Graphite `/graphql` Proxy Failure

### Step 1 — Confirm HTTP/2 negotiation state
Check whether Graphite's Netty server has HTTP/2 enabled and whether Gateway's WebClient is sending ALPN:
```bash
# From inside the compose network, check ALPN on Graphite
docker compose -f steel-hammer/docker-compose-steel-hammer.yml \
  exec steel-hammer-sentinel-gear \
  openssl s_client -alpn h2 -connect steel-hammer-graphite-forge:8084 </dev/null 2>&1 | grep -i alpn
```
Expected: if Graphite does not advertise `h2`, adding `server.http2.enabled=true` to Graphite (or
disabling HTTP/2 on the Gateway WebClient) will resolve the premature close.

### Step 2 — Add HTTP/2 to Graphite OR disable it in Gateway
**Option A (preferred): Enable HTTP/2 on Graphite:**
```yaml
# In docker-compose-steel-hammer.yml, graphite-forge environment:
- "SERVER_HTTP2_ENABLED=true"
```
**Option B: Disable HTTP/2 on Spring Cloud Gateway outbound:**
```yaml
# In sentinel-gear application-docker.yml or compose env:
- "SPRING_CLOUD_GATEWAY_HTTPCLIENT_HTTP2_ENABLED=false"
```

### Step 3 — Check Spring Security logs on Graphite
`LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG` is already active on Graphite.
Trigger one `/graphql` request through Sentinel and check:
```bash
docker logs steel-hammer-graphite-forge --tail 80 2>&1 | grep -Ei 'security|denied|401|403|filter|authorization'
```
Confirm that the `Authorization: Bearer ...` header arrives in the proxied request.

### Step 4 — Cleanup debug flags
After confirming the fix:
- Remove `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG` from Graphite env.
- Verify `JAVA_TOOL_OPTIONS` does not still carry `-Djavax.net.debug=ssl,handshake`.

---

## Priority 2: Remaining Failing Test Suites

### `Vault_Minio_SSE_Encryption`
Review SSE/KMS configuration for MinIO or adapt the test to the available SSE mode.

### `Jclouds_Minio_CRUD_Via_Vault`
Complete TLS truststore configuration for the jclouds integration-test run to eliminate PKIX errors.

### `Observability_Phase2_Proof`
Add missing correlation-header and Graphite negative-path assertions until the report shows
`Conclusion: complete`.

### `tools/Storage-Conductor` Maven Build
Resolve `com.ironbucket:vault-smith:4.0.1` dependency via reactor/BOM/install order.

### Lockfile Drift
`npm ci` is currently not lockfile-consistent and falls back to `npm install`.
Goal: synchronize `package-lock.json` with `package.json` so CI runs strictly with `npm ci`.

---

## Priority 3: Runner Consistency

1. Document canonical roles:
   - `steel-hammer/test-scripts/run-e2e-complete.sh` = canonical containerized E2E gate runner.
   - `scripts/run-all-tests-complete.sh` = superset orchestrator.
2. Keep phase labels and report text permanently in sync.

---

## Verification After Fixes
```bash
bash scripts/run-all-tests-complete.sh
bash steel-hammer/test-scripts/run-e2e-complete.sh
```
Expected: 0 failing suites, green reports in `test-results/`.

## Artifacts
- Reports: `test-results/reports/`
- Logs: `test-results/logs/`
- Observability Evidence: `test-results/phase2-observability/`
- Playwright Artifacts: `test-results/playwright-artifacts/`
