# Next TODOs Plan

## Goal
Reach a stable, fully containerized branch-tip run where GraphQL, refreshed UI evidence, observability, and performance proofs are all green and reported from one canonical flow.

## Current State

### What is proven
- Application-path TLS is proven end to end for:
   - Keycloak
   - Sentinel-Gear
   - Graphite-Forge
   - Claimspindel
   - Brazz-Nossel
   - MinIO
   - Next.js E2E backend path
- Sentinel to Graphite GraphQL routing is working over HTTPS.
- Real object operations through Sentinel to MinIO are proven by the UI E2E path.
- `scripts/ci/run-observability-infra-gate.sh` is green.
- `scripts/e2e/prove-phase2-performance.sh` is green.
- Mixed-user observability/performance proof is green for Alice and Bob, with the latest container re-run producing 44 operations in 647 ms (`68.01 ops/s`) and 4/4 Tempo trace lookups.
- The refreshed Next.js control-plane shell is container-validated with cinematic screenshot proof for `/` and `/e2e-object-browser`.
- Certificate generation is integrated into the compose flow via `steel-hammer-cert-bootstrap`.
- LGTM and application stacks are now correctly separated:
   - `steel-hammer/docker-compose-lgtm.yml` = LGTM only
   - `steel-hammer/docker-compose-steel-hammer.yml` = application stack

### What is not yet proven or finalized
- LGTM services themselves are not running under TLS in Docker.
   Current design is internal HTTP for:
   - Loki
   - Tempo
   - Mimir
   - OTEL Collector HTTP ingest
   - postgres-exporter
- The mixed-user proof is green, but it is not yet part of the canonical shell-based gate flow.
- The cinematic UI proof is green, but it is not yet surfaced in complete-run reporting.
- The complete wrapper should still be rerun from the latest branch tip if a fresh all-in-one report is required.
- Historical test failures in the larger wrapper flow still need final reconciliation where they are outside the now-green observability and mixed-user proof path.

## Current TODOs

### Priority 0: Tenant UI sprint follow-up (intentionally red E2E)
- New UI + Playwright mappings were added for tenant foundation and membership/RBAC:
   - `ironbucket-app-nextjs/tests/ui-tenant-management-foundation.spec.ts`
   - `ironbucket-app-nextjs/tests/ui-tenant-membership-rbac.spec.ts`
- These two specs are expected to fail right now by design.
- Next sprint objective:
   - wire deterministic backend fixtures for tenant and Keycloak membership synchronization,
   - replace the deliberate failing assertions with real readiness markers and service-backed checks,
   - then promote both specs into canonical gate reporting.

### Priority 0.5: Policy Engine UI sprint follow-up (intentionally red E2E)
- New Phase-3 policy management UI was added with list/editor/validation/dry-run/diff/gitops mapping:
   - `ironbucket-app-nextjs/tests/ui-policy-engine-management.spec.ts`
   - `ironbucket-app-nextjs/tests/ui-policy-engine-diff-gitops.spec.ts`
- These policy specs are expected to fail by design for now.
- Completed in this follow-up pass:
   - true JSON/YAML mode switching in the policy editor,
   - beginner-focused semantic validation hints and on-page validation summary,
   - explicit GitOps action-state guidance before/after first save.
- Next sprint objective:
   - connect policy versioning and gitops actions to persistent SCM-backed storage,
   - replace deliberate readiness-marker failures with real service-backed assertions,
   - add YAML linting/autocomplete quality parity with JSON suggestions,
   - then include policy engine specs in canonical evidence reporting.

### Priority 0.75: Sprint 9 UI hardening completed, productionization follow-up remains
- Completed in this pass:
   - role-based read/write gating across tenant and policy UI surfaces,
   - explicit sign-in-required and access-restricted states,
   - retryable error states for key data queries,
   - loading skeletons for table and detail loading paths,
   - mobile action-row wrapping and accessibility improvements (`aria-busy`, clearer action labels, read-only notices).
- Follow-up objective:
   - enforce RBAC server-side for all corresponding API routes (currently UI-focused hardening),
   - add automated accessibility checks (axe or equivalent) in CI,
   - add viewport-specific Playwright coverage for mobile breakpoints,
   - include accessibility and responsive evidence in release reporting.

### Priority 1: Promote mixed-user proof into the canonical gates
- Integrate `ironbucket-app-nextjs/tests/ui-mixed-actor-observability-performance.spec.ts` into a formal gate path.
- Preferred integration points:
   - `scripts/e2e/prove-phase2-performance.sh`
   - or `scripts/ci/run-observability-infra-gate.sh`
- Expected outcome:
   - mixed-user throughput
   - Tempo trace lookup
   - Loki ingestion confirmation
   - metrics deltas
   become part of release-grade evidence, not just a standalone Playwright proof.

### Priority 2: Surface the mixed-user and cinematic artifacts in complete-run reporting
- Include `test-results/ui-e2e-traces/ui-mixed-actor-observability-performance.json` in complete-run summaries.
- Include `test-results/ui-e2e-traces/ui-cinematic-showcase.json` plus the generated screenshot files in complete-run summaries.
- Include a short summary of:
   - total operations
   - throughput
   - successful trace lookups
   - Loki ingestion check
   - metric deltas
   - cinematic screenshot artifact names

### Priority 3: Make the LGTM TLS posture explicit
- Decide whether the supported model is:
   - application stack on HTTPS, LGTM internal HTTP
   - or full internal TLS including LGTM
- Once decided:
   - document it consistently
   - align proof scripts and readiness probes to that decision

### Priority 4: Re-run the canonical branch-tip wrapper
- Re-run:
```bash
bash steel-hammer/test-scripts/run-e2e-complete.sh
```
- Goal:
   - produce one fresh branch-tip report after the latest observability, performance, and mixed-user proof work.

### Priority 5: Resolve remaining wider-suite failures outside the now-green proof path
- `Vault_Minio_SSE_Encryption`
   - review MinIO SSE/KMS expectations versus current deployment mode
- `Jclouds_Minio_CRUD_Via_Vault`
   - complete TLS truststore handling for the jclouds integration path
- `tools/Storage-Conductor` Maven build
   - resolve `com.ironbucket:vault-smith:4.0.1` dependency ordering / bootstrap installation
- lockfile consistency
   - align `package-lock.json` with `package.json` so CI can use strict `npm ci`

## Key Learnings From Troubleshooting

### TLS and routing learnings
- Graphite-Forge PEM mode is the correct runtime path here; PKCS12 plus disabled RSA cipher suites caused handshake failure.
- In PEM mode, Graphite must explicitly clear keystore and truststore environment variables that would otherwise trigger empty PKCS12 handling.
- Sentinel routing must point to Graphite over HTTPS in the Docker runtime path.

### Build and compose learnings
- Certificate bootstrapping must be part of compose startup, not a manual pre-step.
- Cross-module Java image builds need `services/Pactum-Scroll` installed inside the image build path or bootstrapped before dependent modules run.
- The LGTM compose file must remain observability-only; mixing application services into it causes proof-script drift and confusion.

### Observability learnings
- Application-path traceability is now strong enough when using deterministic OTLP bridge spans for returned trace IDs.
- Loki actor-specific logs from the ephemeral UI E2E container are less reliable than Tempo trace lookup plus workload-window log-ingestion checks.
- Internal protocol assumptions matter:
   - application services use HTTPS
   - LGTM internals currently use HTTP

### Authentication learnings
- Keycloak `dev-client` is valid for password grant with seeded users such as `bob` / `bobP@ss`.
- `client_credentials` is not valid for `dev-client` because service-account access is not enabled in the dev realm.

## Current Proof References
- GraphQL via Sentinel:
   - `POST https://steel-hammer-sentinel-gear:8080/graphql` returns `HTTP 200`
- UI E2E object-path proof:
   - `test-results/ui-e2e-traces/object-browser-baseline-e2e.json`
   - `test-results/ui-e2e-traces/ui-live-upload-persistence.json`
   - `test-results/ui-e2e-traces/ui-s3-methods-e2e.json`
- Mixed-user observability/performance proof:
   - `test-results/ui-e2e-traces/ui-mixed-actor-observability-performance.json`
- Cinematic UI proof:
   - `test-results/ui-e2e-traces/ui-cinematic-showcase.json`
   - `test-results/ui-e2e-traces/ui-cinematic-overview-proof.png`
   - `test-results/ui-e2e-traces/ui-cinematic-object-browser-proof.png`
- Latest observability proof:
   - `test-results/phase2-observability/20260322T221104Z/PHASE2_OBSERVABILITY_PROOF_REPORT.md`
- Latest performance proof:
   - `test-results/phase2-performance/20260322T221139Z/PHASE2_PERFORMANCE_REPORT.md`

## Verification Commands
```bash
bash scripts/ci/run-observability-infra-gate.sh
bash scripts/e2e/prove-phase2-performance.sh
bash steel-hammer/test-scripts/run-e2e-complete.sh
```

## Success Condition
- One branch-tip run produces green evidence for:
   - GraphQL path
   - UI object path
   - refreshed UI screenshot path
   - observability infra
   - performance proof
   - mixed-user proof
- The documented TLS scope is explicit and matches runtime reality.
