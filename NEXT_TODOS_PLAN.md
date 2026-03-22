# Next TODOs Plan

## Goal
Stable, fully-containerized end-to-end run with green status for GraphQL + UI + Observability.

## Session Summary (2026-03-22)
This session completed the Sentinel -> Graphite GraphQL proxy fix and re-ran the full all-projects E2E gate.

### Additional Runtime Findings (2026-03-22, follow-up)
- Certificate bootstrap is now part of the compose flow via `steel-hammer-cert-bootstrap`.
   Missing bootstrap certs in `certs/` no longer require a manual `generate-certificates.sh` pre-step.
- Graphite-Forge had a second runtime blocker unrelated to TLS:
   duplicate DGS registrations from `GovernanceDataFetcher` overlapped the dedicated fetchers and
   caused `Duplicate data fetchers registered for Query.getPolicyStatistics` during startup.
- Graphite-Forge also required PEM mode to be internally consistent at startup:
   `SERVER_SSL_TRUSTSTORE`, `SERVER_SSL_TRUSTSTORE_PASSWORD`, and `SERVER_SSL_TRUSTSTORE_TYPE`
   must be empty when no server truststore is used, otherwise Spring Boot still attempts to
   materialize a PKCS12 truststore from an empty location.
- Keycloak `dev` realm authentication was verified from inside the compose network using the realm file:
   client `dev-client` + secret `dev-secret` with password grant for user `bob` / `bobP@ss`.
   `client_credentials` does not work for `dev-client` because service accounts are not enabled.
- Verified runtime path again from inside the network:
   `POST https://steel-hammer-sentinel-gear:8080/graphql` with the `bob` token returns `HTTP 200`
   and `{"data":{"__typename":"Query"}}`.
- Verified actual object operations through Sentinel Gear to MinIO using the existing UI E2E flows,
    not just GraphQL reachability:
    - `tests/ui-live-upload-persistence.spec.ts` passed and wrote
       `test-results/ui-e2e-traces/ui-live-upload-persistence.json` with `verified: true` for
       bucket `default-alice-files`.
    - `tests/ui-s3-methods-e2e.spec.ts` passed and wrote
       `test-results/ui-e2e-traces/ui-s3-methods-e2e.json` proving `createBucket`, `listBuckets`,
       `getBucket`, `uploadObject`, `listObjects`, `getObject`, `getBucketRoutingDecision`,
       `downloadObject`, `deleteObject`, and `deleteBucket` all succeeded through Sentinel.
    - Concrete proof artifact values from the passing run:
       bucket `default-alice-methods-1774212707180`,
       key `alice-all-methods-1774212707180.txt`,
       traceId `8cc1832ff9cecfcdc374e20456b3cf80`.
- Observability gap still remains:
    the request path is proven functionally, but the propagated trace id was not easily recoverable
    from plain `docker logs` on Sentinel/Graphite/Claimspindel/Brazz during this run. Log/trace
    correlation still needs a dedicated observability follow-up.

### Root Cause Found (TLS Handshake): PKCS12 + Disabled RSA Cipher Suites
The JVM policy (`jdk.tls.disabledAlgorithms`) disables `TLS_RSA_*` cipher suites. Graphite-Forge was
configured with a PKCS12 keystore. When JSSE attempted TLS, it tried RSA key-exchange ciphers (disabled)
and found no common cipher suite → `HANDSHAKE_FAILURE (alert 40)`.

### Fix Applied (Step 1): PEM-Based TLS for Graphite-Forge
Switched Graphite from PKCS12 to PEM-based SSL. Spring Boot 3.x supports `server.ssl.certificate` +
`server.ssl.certificate-private-key`. PEM mode allows JSSE to use `ECDHE_RSA` ciphers (not disabled).

**Graphite-forge state in `docker-compose-steel-hammer.yml`:**
```yaml
- "SERVER_SSL_ENABLED=true"
- "SERVER_SSL_CERTIFICATE=file:/vault-pki-certs/services/graphite-forge/fullchain.crt"
- "SERVER_SSL_CERTIFICATE_PRIVATE_KEY=file:/vault-pki-certs/services/graphite-forge/tls.key"
- "SERVER_SSL_KEYSTORE="            # explicitly cleared
- "SERVER_SSL_KEYSTORE_PASSWORD="   # explicitly cleared
- "SERVER_SSL_KEYSTORE_TYPE="       # explicitly cleared
- "SERVER_HTTP2_ENABLED=true"
```

### Fix Applied (Step 2): Gateway Route Forced to HTTPS
The remaining `/graphql` proxy failure was not a cipher issue anymore; Sentinel was still routing with
`http://steel-hammer-graphite-forge:8084` for the GraphQL route at runtime.

Fixes applied in `docker-compose-steel-hammer.yml`:
```yaml
- "GRAPHITE_FORGE_URI=https://steel-hammer-graphite-forge:8084"
- "SPRING_CLOUD_GATEWAY_HTTPCLIENT_SSL_TRUSTEDX509CERTIFICATES[0]=/vault-pki-certs/ca/ca-chain.pem"
```

### Verification: GraphQL via Sentinel is Green
Verified from inside the compose network with Keycloak realm `dev` token (`dev-client` + secret):
`POST https://steel-hammer-sentinel-gear:8080/graphql` returns `HTTP 200` and:
```json
{"data":{"__typename":"Query"}}
```

Verified authentication detail:
- `dev-client` is usable with password grant for seeded realm users.
- Example confirmed credential: `bob` / `bobP@ss`.
- `client_credentials` for `dev-client` returns `unauthorized_client` because service-account access
   is not enabled in `steel-hammer/keycloak/dev-realm.json`.

### Also Fixed: PKI Init Script Naming Contract
`steel-hammer/vault/init-vault-pki.sh` patched to also write `intermediate-ca.crt` and `root-ca.crt`
as compatibility copies alongside the existing `issuing-ca.crt` / `ca-chain.crt`.

### Current Gate Status (All Projects E2E)
Latest run report:
- `test-results/all-projects-e2e-gate/20260322T190853Z/ALL_PROJECTS_E2E_GATE_REPORT.md`

Result summary:
- Java projects: `11/11` passed
- UI projects: `1/2` passed
- Overall: `FAILED`

Historical failing item in that full gate run:
- `ironbucket-app-nextjs` (Playwright E2E)

Targeted rerun status from this session:
- `tests/ui-live-upload-persistence.spec.ts`: `PASS`
- `tests/ui-s3-methods-e2e.spec.ts`: `PASS`
- Playwright report: `test-results/ui-playwright-report.json`
- Proof artifacts: `test-results/ui-e2e-traces/`

---

## Priority 1: Re-run the Full UI Gate with the Proven Fix Path

### Step 1 — Re-run the canonical containerized UI gate
The targeted proof specs are green. Re-run the broader gate to replace the stale historical failure:
```bash
bash steel-hammer/test-scripts/run-e2e-complete.sh
```

### Step 2 — Preserve the passing proof artifacts
Collect and inspect:
- `test-results/ui-playwright-report.json`
- `test-results/ui-playwright-report.xml`
- `test-results/ui-e2e-traces/`

### Step 3 — Validate environment + endpoints in test runtime
Confirm the UI test container still sees expected URLs and healthy dependencies:
```bash
docker compose -f steel-hammer/docker-compose-steel-hammer.yml ps
docker compose -f steel-hammer/docker-compose-steel-hammer.yml exec steel-hammer-sentinel-gear curl -sk https://steel-hammer-sentinel-gear:8080/graphql
```

### Step 4 — Close the observability correlation gap
Functional proof is complete, but tracing evidence is weaker than it should be. Ensure the same request
can be found cleanly in logs/traces across Sentinel, Graphite, Claimspindel, and Brazz.

### Step 5 — Re-run the aggregate gate
```bash
bash scripts/ci/run-all-projects-e2e-gate.sh
```
Expected target: UI `2/2`, Overall `PASS`.

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
