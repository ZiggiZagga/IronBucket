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
- BlobStoreContext adapter: `BlobStoreContextProvider` + `JcloudsBlobStoreContextProvider`
- Provider-neutral CRUD contracts: `ObjectStorageAdapter`, `ObjectKey`, `PutObjectCommand`, `StoredObject`
- jclouds CRUD adapter: `JcloudsObjectStorageAdapter`
- Policy/capability enforcement: `CapabilityEnforcingObjectStorageAdapter`, `PolicyEnforcer`
- AWS baseline probe: `AwsS3CapabilityProbe`
- Contract tests: `ProviderCapabilityRegistryTest`

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
export AWS_S3_ENDPOINT=http://localhost:9000

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
- ✅ Deterministic MinIO CRUD integration gate added via Maven profile `minio-it`.

## MinIO CRUD Integration Gate

Run locally against MinIO:

```bash
docker run -d --name jclouds-minio-it -p 9000:9000 \
	-e MINIO_ROOT_USER=minioadmin \
	-e MINIO_ROOT_PASSWORD=minioadmin \
	minio/minio:latest server /data

cd services/jclouds-adapter-core
IRONBUCKET_MINIO_ENDPOINT=http://127.0.0.1:9000 \
IRONBUCKET_MINIO_ACCESS_KEY=minioadmin \
IRONBUCKET_MINIO_SECRET_KEY=minioadmin \
IRONBUCKET_MINIO_REGION=us-east-1 \
mvn -B -V verify -Pminio-it
```

CI executes this gate in `build-and-test.yml` as job `jclouds MinIO CRUD Gate`.

## Next Steps (Phase 4)

1. Add non-skipped integration tests for provider-neutral CRUD against controlled S3-compatible runtime.
2. Add capability probe integration tests per provider (GCS/Azure/local targets).
3. Surface capability matrix and capability failures via admin/management API contracts.
4. Wire `PolicyEnforcer` to Claimspindel policy decisions (deny-overrides-allow parity).
