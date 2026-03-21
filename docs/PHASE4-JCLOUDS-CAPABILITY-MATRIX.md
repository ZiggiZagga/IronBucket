# Phase 4 jclouds Capability Matrix

**Status:** Phase 4 kickoff artifact (baseline + CRUD/policy milestone 1)
**Backed by code:** `services/jclouds-adapter-core`

## Scope

This matrix defines the provider-neutral baseline used by IronBucket for initial Phase 4 provider selection and policy/capability checks.

## Capability Matrix (Initial Baseline)

| Capability | AWS S3 | GCS | Azure Blob | Local Filesystem |
|---|---|---|---|---|
| `OBJECT_READ` | ✅ | ✅ | ✅ | ✅ |
| `OBJECT_WRITE` | ✅ | ✅ | ✅ | ✅ |
| `OBJECT_DELETE` | ✅ | ✅ | ✅ | ✅ |
| `MULTIPART_UPLOAD` | ✅ | ✅ | ✅ | ❌ |
| `VERSIONING` | ✅ | ✅ | ❌ | ❌ |
| `OBJECT_TAGGING` | ✅ | ✅ | ✅ | ❌ |
| `OBJECT_ACL` | ✅ | ❌ | ❌ | ❌ |
| `LIFECYCLE_POLICY` | ✅ | ✅ | ❌ | ❌ |
| `PRESIGNED_URLS` | ✅ | ✅ | ✅ | ❌ |

## Implementation Mapping

- Capability registry: `ProviderCapabilityRegistry`
- Provider profile model: `ProviderCapabilityProfile`
- Provider selector: `ProviderSelectionService`
- Tenant/bucket runtime router: `TenantAwareObjectStorageService` + `TenantBucketProviderRegistry`
- BlobStoreContext adapter: `BlobStoreContextProvider` + `JcloudsBlobStoreContextProvider`
- Provider-neutral CRUD contracts: `ObjectStorageAdapter`, `ObjectKey`, `PutObjectCommand`, `StoredObject`
- jclouds CRUD adapter: `JcloudsObjectStorageAdapter`
- Policy/capability enforcement: `CapabilityEnforcingObjectStorageAdapter`, `PolicyEnforcer`
- Provider probes: `AwsS3CapabilityProbe`, `GcsCapabilityProbe`, `AzureBlobCapabilityProbe`
- Contract tests: `ProviderCapabilityRegistryTest`, `ProviderNeutralParityContractTest`

## AWS Baseline Probe Tests

- Unit behavior tests: `AwsS3CapabilityProbeTest`
- Integration-gated test: `AwsS3CapabilityProbeIntegrationTest`

Enable real AWS S3 integration probe with environment variables:

```bash
export IRONBUCKET_AWS_S3_INTEGRATION=true
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=us-east-1
# optional, for S3-compatible endpoint test targets:
export AWS_S3_ENDPOINT=https://localhost:9000

cd services/jclouds-adapter-core
mvn test -B -V
```

## Initial Design Decisions

- Keep provider selection deterministic and stateless.
- Represent capabilities as explicit enum contracts (not free-form strings).
- Treat unsupported capabilities as hard negatives during provider selection.
- Keep this baseline conservative; expand only via tests + roadmap contracts.

## Milestone 1 Status

- ✅ Provider capability probe contract tests (AWS baseline).
- ✅ Provider-neutral object CRUD contract introduced.
- ✅ Policy + capability gate decorator added for CRUD operations.
- ✅ Tenant-aware runtime provider routing for CRUD operations.
- ✅ GCS and Azure provider probe unit contract tests.
- ✅ Deterministic MinIO CRUD integration gate added via Maven profile `minio-it`.

## MinIO CRUD Integration Gate

Run locally against MinIO:

```bash
docker run -d --name jclouds-minio-it -p 9000:9000 \
	-e MINIO_ROOT_USER=minioadmin \
	-e MINIO_ROOT_PASSWORD=minioadmin \
	minio/minio:latest server /data

cd services/jclouds-adapter-core
IRONBUCKET_MINIO_ENDPOINT=https://127.0.0.1:9000 \
IRONBUCKET_MINIO_ACCESS_KEY=minioadmin \
IRONBUCKET_MINIO_SECRET_KEY=minioadmin \
IRONBUCKET_MINIO_REGION=us-east-1 \
mvn -B -V verify -Pminio-it
```

CI executes this gate in `build-and-test.yml` as job `jclouds MinIO CRUD Gate`.

## Provider Probe CI Gate

CI also executes deterministic provider capability probe tests in
`build-and-test.yml` as job `jclouds Provider Probe Gate` via:

```bash
bash scripts/ci/run-jclouds-provider-probe-gate.sh
```

Current probe-gate test set:
- `AwsS3CapabilityProbeTest`
- `GcsCapabilityProbeTest`
- `AzureBlobCapabilityProbeTest`

## Provider Integration Probe Gate (Credential-Backed)

CI also includes a credential-backed integration probe gate via:

```bash
bash scripts/ci/run-jclouds-provider-integration-probe-gate.sh
```

Integration probe test set (enabled per provider via environment toggles):
- `AwsS3CapabilityProbeIntegrationTest`
- `GcsCapabilityProbeIntegrationTest`
- `AzureBlobCapabilityProbeIntegrationTest`

Provider toggles:
- `IRONBUCKET_AWS_S3_INTEGRATION=true`
- `IRONBUCKET_GCS_INTEGRATION=true`
- `IRONBUCKET_AZURE_BLOB_INTEGRATION=true`

If no provider toggle is enabled, the integration probe gate exits successfully in skip mode.

## Provider Integration Parity Gate (Credential-Backed CRUD)

CI now includes a credential-backed CRUD parity integration gate via:

```bash
bash scripts/ci/run-jclouds-provider-integration-parity-gate.sh
```

Current parity integration test set:
- `ProviderCrudParityIntegrationTest`

This test suite executes provider-specific CRUD roundtrip checks for AWS S3, GCS,
and Azure Blob when corresponding integration toggles and credentials are enabled.

## Provider-Neutral Parity Contract Suite

Phase-4 now includes explicit provider-neutral parity contract tests for:

- CRUD contract consistency across all provider profiles.
- Multipart capability parity matrix.
- Versioning capability parity matrix.
- Multipart + versioning intersection contract.

Run locally:

```bash
cd services/jclouds-adapter-core
mvn -B -Dtest=ProviderNeutralParityContractTest test
```

## Phase-4 Versioning/Multipart Gate

CI now includes a dedicated Phase-4 versioning/multipart gate via:

```bash
bash scripts/ci/run-phase4-versioning-multipart-gate.sh
```

Gate checks:
- deterministic versioning/delete-marker fixture marker validation
- `ProviderNeutralParityContractTest`
- `ProviderCapabilityRegistryTest`

## Next Steps (Phase 4)

1. Expand credential-backed parity coverage from CRUD to explicit provider runtime multipart/versioning operations where credentials and backend semantics permit safe CI execution.
2. Continue surfacing capability matrix and routing/capability failures via admin/management API contracts.
3. Wire `PolicyEnforcer` to Claimspindel policy decisions (deny-overrides-allow parity).
