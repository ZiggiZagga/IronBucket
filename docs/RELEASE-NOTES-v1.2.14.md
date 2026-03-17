# Release Notes v1.2.14

Date: 2026-03-17

## Summary

This release stabilizes the first-user and roadmap E2E gates by completing Graphite-Forge operation coverage in the end-to-end flow, harmonizing tenant claim extraction across Sentinel-Gear and Brazz-Nossel, and hardening Phase 4 MinIO CRUD gate execution.

## Highlights

- Completed full Graphite-Forge operation flow execution for additional users (`charlie`, `dana`, `eve`) through Sentinel-Gear.
- Harmonized tenant claim extraction support for `tenant`, `tenant_id`, and `tenantId`.
- Added organization/group fallback tenant resolution in Brazz identity extraction to align runtime behavior.
- Fixed GraphQL routing capability request to use supported capability enum (`OBJECT_READ`).
- Stabilized Phase 4 MinIO CRUD proof by using a deterministic MinIO startup path and configurable endpoint scheme (`PHASE4_MINIO_SCHEME`, default `http`).

## Changed Components

- `scripts/e2e/e2e-alice-bob-test.sh`
- `scripts/e2e/prove-phase1-3-complete.sh`
- `scripts/e2e/prove-phase1-4-complete.sh`
- `services/Sentinel-Gear/src/main/java/com/ironbucket/sentinelgear/identity/JWTValidator.java`
- `services/Sentinel-Gear/src/test/java/com/ironbucket/sentinelgear/identity/SentinelGearJWTValidationTest.java`
- `services/Brazz-Nossel/src/main/java/com/ironbucket/brazznossel/controller/S3Controller.java`
- `services/Brazz-Nossel/src/test/java/com/ironbucket/brazznossel/controller/S3ControllerTests.java`
- `docs/CI-CD-PIPELINE.md`
- `steel-hammer/docker-compose-steel-hammer.yml`

## Validation Snapshot

- `mvn -Dtest=SentinelGearJWTValidationTest test` (Sentinel-Gear): PASS
- `mvn -Dtest=S3ControllerTests test` (Brazz-Nossel): PASS
- `scripts/e2e/prove-phase1-3-complete.sh`: PASS
- `scripts/e2e/prove-phase1-4-complete.sh`: PASS
- `scripts/ci/run-first-user-experience-gate.sh`: PASS

## Notes

- This release keeps Sentinel-Gear as the request entrypoint for GraphQL/S3 flow validation.
- Service discovery assumptions remain Eureka-based; no IP-pinning was introduced.
