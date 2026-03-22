package com.ironbucket.brazznossel.service;

import com.ironbucket.pactumscroll.identity.NormalizedIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * S3ProxyServiceImpl - Real S3 proxy implementation using AWS SDK
 * 
 * Proxies S3 operations to MinIO/S3 backend with tenant isolation
 * and policy enforcement.
 */
@Service
public class S3ProxyServiceImpl implements S3ProxyService {
    
    private static final Logger logger = LoggerFactory.getLogger(S3ProxyServiceImpl.class);
    
    private final S3Client s3Client;
    private final Map<String, BackendProvider> tenantDefaultProviders;
    private final Map<String, BackendProvider> bucketOverrides;
    
    public S3ProxyServiceImpl(
            @Value("${app.s3.endpoint:https://localhost:9000}") String endpoint,
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey,
            @Value("${app.s3.region:us-east-1}") String region,
            @Value("${app.s3.routing.tenant-defaults:}") String tenantDefaults,
            @Value("${app.s3.routing.bucket-overrides:}") String bucketOverrides) {
        
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Vault-backed S3 credentials are required: app.s3.access-key and app.s3.secret-key must be set");
        }

        logger.info("Initializing S3 Proxy Service with endpoint: {}", endpoint);
        
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true) // Required for MinIO
                .build();

        this.tenantDefaultProviders = parseRoutingMap(tenantDefaults, false);
        this.bucketOverrides = parseRoutingMap(bucketOverrides, true);
    }

    enum BackendProvider {
        AWS_S3,
        GCS,
        AZURE_BLOB,
        LOCAL_FILESYSTEM
    }

    enum RequiredCapability {
        OBJECT_READ,
        OBJECT_WRITE,
        OBJECT_DELETE,
        MULTIPART_UPLOAD,
        VERSIONING
    }

    private Map<String, BackendProvider> parseRoutingMap(String raw, boolean expectBucketKey) {
        Map<String, BackendProvider> parsed = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return parsed;
        }

        for (String entry : raw.split(",")) {
            String token = entry.trim();
            if (token.isBlank()) {
                continue;
            }

            String[] parts = token.split("=", 2);
            if (parts.length != 2) {
                logger.warn("Ignoring malformed routing token: {}", token);
                continue;
            }

            String key = parts[0].trim();
            String providerRaw = parts[1].trim();
            if (key.isBlank() || providerRaw.isBlank()) {
                logger.warn("Ignoring malformed routing token: {}", token);
                continue;
            }

            if (expectBucketKey && !key.contains("/")) {
                logger.warn("Bucket override must use tenant/bucket key, ignoring token: {}", token);
                continue;
            }

            if (!expectBucketKey && key.contains("/")) {
                logger.warn("Tenant default must use tenant key only, ignoring token: {}", token);
                continue;
            }

            try {
                parsed.put(key, parseProvider(providerRaw));
            } catch (IllegalArgumentException illegalArgumentException) {
                logger.warn("Ignoring unknown provider '{}' in routing token: {}", providerRaw, token);
            }
        }

        return parsed;
    }

    private BackendProvider parseProvider(String rawProvider) {
        return BackendProvider.valueOf(rawProvider.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    private boolean supports(BackendProvider provider, RequiredCapability capability) {
        return switch (provider) {
            case AWS_S3 -> true;
            case GCS -> true;
            case AZURE_BLOB -> capability != RequiredCapability.VERSIONING;
            case LOCAL_FILESYSTEM -> EnumSet.of(
                RequiredCapability.OBJECT_READ,
                RequiredCapability.OBJECT_WRITE,
                RequiredCapability.OBJECT_DELETE
            ).contains(capability);
        };
    }

    BackendProvider resolveProviderForRequest(NormalizedIdentity identity, String bucket, RequiredCapability capability) {
        List<BackendProvider> candidates = new ArrayList<>();
        String tenantBucketKey = identity.getTenant() + "/" + bucket;

        BackendProvider bucketOverride = bucketOverrides.get(tenantBucketKey);
        if (bucketOverride != null) {
            candidates.add(bucketOverride);
        }

        BackendProvider tenantDefault = tenantDefaultProviders.get(identity.getTenant());
        if (tenantDefault != null) {
            candidates.add(tenantDefault);
        }

        for (BackendProvider fallback : BackendProvider.values()) {
            if (!candidates.contains(fallback)) {
                candidates.add(fallback);
            }
        }

        for (BackendProvider candidate : candidates) {
            if (supports(candidate, capability)) {
                logger.debug(
                    "Resolved provider '{}' for tenant='{}', bucket='{}', capability='{}'",
                    candidate,
                    identity.getTenant(),
                    bucket,
                    capability
                );
                return candidate;
            }
        }

        throw new IllegalStateException(
            "No provider supports capability " + capability + " for tenant " + identity.getTenant() + " and bucket " + bucket
        );
    }

    private S3Client routedClient(NormalizedIdentity identity, String bucket, RequiredCapability capability) {
        resolveProviderForRequest(identity, bucket, capability);
        return s3Client;
    }

    private void assertTenantBucketAccess(String bucket, NormalizedIdentity identity) {
        if (!bucket.startsWith(identity.getTenant() + "-")) {
            throw new SecurityException("Access denied: bucket does not belong to tenant " + identity.getTenant());
        }
    }
    
    @Override
    public Mono<String> listBuckets(NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Listing buckets for user: {}, tenant: {}", 
                    identity.getUsername(), identity.getTenant());
            
            ListBucketsResponse response = s3Client.listBuckets();
            
            // Filter buckets by tenant prefix
            String tenantPrefix = identity.getTenant() + "-";
            StringBuilder result = new StringBuilder("Buckets for tenant " + identity.getTenant() + ":\n");
            
            response.buckets().stream()
                    .filter(bucket -> bucket.name().startsWith(tenantPrefix))
                    .forEach(bucket -> result.append("  - ")
                            .append(bucket.name())
                            .append(" (created: ")
                            .append(bucket.creationDate())
                            .append(")\n"));
            
            return result.toString();
        });
    }

    @Override
    public Mono<String> createBucket(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            CreateBucketRequest request = CreateBucketRequest.builder().bucket(bucket).build();
            routedClient(identity, bucket, RequiredCapability.OBJECT_WRITE).createBucket(request);
            return bucket;
        });
    }

    @Override
    public Mono<Void> deleteBucket(String bucket, NormalizedIdentity identity) {
        return Mono.fromRunnable(() -> {
            assertTenantBucketAccess(bucket, identity);
            DeleteBucketRequest request = DeleteBucketRequest.builder().bucket(bucket).build();
            routedClient(identity, bucket, RequiredCapability.OBJECT_DELETE).deleteBucket(request);
        });
    }

    @Override
    public Mono<String> listObjects(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).build();
            ListObjectsV2Response response = routedClient(identity, bucket, RequiredCapability.OBJECT_READ).listObjectsV2(request);
            return response.contents().stream().map(S3Object::key).collect(Collectors.joining("\n"));
        });
    }
    
    @Override
    public Mono<byte[]> getObject(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Getting object: {}/{} for user: {}", bucket, key, identity.getUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            return routedClient(identity, bucket, RequiredCapability.OBJECT_READ).getObjectAsBytes(request).asByteArray();
        });
    }

    @Override
    public Mono<String> headObject(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(key).build();
            HeadObjectResponse response = routedClient(identity, bucket, RequiredCapability.OBJECT_READ).headObject(request);
            return response.eTag();
        });
    }

    @Override
    public Mono<String> headBucket(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            HeadBucketRequest request = HeadBucketRequest.builder().bucket(bucket).build();
            routedClient(identity, bucket, RequiredCapability.OBJECT_READ).headBucket(request);
            return bucket;
        });
    }
    
    @Override
    public Mono<byte[]> getObjectRange(String bucket, String key, long start, long end, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Getting object range: {}/{}[{}-{}] for user: {}", 
                    bucket, key, start, end, identity.getUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range(String.format("bytes=%d-%d", start, end))
                    .build();
            
            return routedClient(identity, bucket, RequiredCapability.OBJECT_READ).getObjectAsBytes(request).asByteArray();
        });
    }
    
    @Override
    public Mono<String> putObject(String bucket, String key, byte[] content, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Putting object: {}/{} ({} bytes) for user: {}", 
                    bucket, key, content.length, identity.getUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .metadata(java.util.Map.of(
                            "uploaded-by", identity.getUsername(),
                            "tenant", identity.getTenant()
                    ))
                    .build();
            
                PutObjectResponse response = routedClient(identity, bucket, RequiredCapability.OBJECT_WRITE)
                    .putObject(request, RequestBody.fromBytes(content));
            return response.eTag();
        });
    }
    
    @Override
    public Mono<Void> deleteObject(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromRunnable(() -> {
            logger.info("Deleting object: {}/{} for user: {}", bucket, key, identity.getUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            routedClient(identity, bucket, RequiredCapability.OBJECT_DELETE).deleteObject(request);
            logger.info("Object deleted successfully: {}/{}", bucket, key);
        });
    }

    @Override
    public Mono<Void> deleteObjectVersion(String bucket, String key, String versionId, NormalizedIdentity identity) {
        return Mono.fromRunnable(() -> {
            assertTenantBucketAccess(bucket, identity);
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .versionId(versionId)
                    .build();
            routedClient(identity, bucket, RequiredCapability.VERSIONING).deleteObject(request);
        });
    }

    @Override
    public Mono<byte[]> getObjectVersion(String bucket, String key, String versionId, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .versionId(versionId)
                    .build();
            return routedClient(identity, bucket, RequiredCapability.VERSIONING).getObjectAsBytes(request).asByteArray();
        });
    }

    @Override
    public Mono<String> listObjectVersions(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            ListObjectVersionsRequest request = ListObjectVersionsRequest.builder().bucket(bucket).build();
                ListObjectVersionsResponse response = routedClient(identity, bucket, RequiredCapability.VERSIONING)
                    .listObjectVersions(request);
            return response.versions().stream()
                    .map(v -> v.key() + ":" + v.versionId())
                    .collect(Collectors.joining("\n"));
        });
    }
    
    @Override
    public Mono<String> initiateMultipartUpload(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Initiating multipart upload: {}/{} for user: {}", 
                    bucket, key, identity.getUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .metadata(java.util.Map.of(
                            "uploaded-by", identity.getUsername(),
                            "tenant", identity.getTenant()
                    ))
                    .build();
            
                CreateMultipartUploadResponse response = routedClient(identity, bucket, RequiredCapability.MULTIPART_UPLOAD)
                    .createMultipartUpload(request);
            logger.info("Multipart upload initiated: uploadId={}", response.uploadId());
            return response.uploadId();
        });
    }

    @Override
    public Mono<String> uploadPart(String bucket, String key, String uploadId, int partNumber, byte[] content, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            UploadPartRequest request = UploadPartRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
                UploadPartResponse response = routedClient(identity, bucket, RequiredCapability.MULTIPART_UPLOAD)
                    .uploadPart(request, RequestBody.fromBytes(content));
            return response.eTag();
        });
    }

    @Override
    public Mono<String> completeMultipartUpload(String bucket, String key, String uploadId, List<CompletedPart> parts, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            CompletedMultipartUpload completed = CompletedMultipartUpload.builder().parts(parts).build();
            CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(completed)
                    .build();
                CompleteMultipartUploadResponse response = routedClient(identity, bucket, RequiredCapability.MULTIPART_UPLOAD)
                    .completeMultipartUpload(request);
            return response.eTag();
        });
    }

    @Override
    public Mono<Void> abortMultipartUpload(String bucket, String key, String uploadId, NormalizedIdentity identity) {
        return Mono.fromRunnable(() -> {
            assertTenantBucketAccess(bucket, identity);
            AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .build();
            routedClient(identity, bucket, RequiredCapability.MULTIPART_UPLOAD).abortMultipartUpload(request);
        });
    }

    @Override
    public Mono<String> listMultipartUploads(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            ListMultipartUploadsRequest request = ListMultipartUploadsRequest.builder().bucket(bucket).build();
                ListMultipartUploadsResponse response = routedClient(identity, bucket, RequiredCapability.MULTIPART_UPLOAD)
                    .listMultipartUploads(request);
            return response.uploads().stream()
                    .map(upload -> upload.key() + ":" + upload.uploadId())
                    .collect(Collectors.joining("\n"));
        });
    }

    @Override
    public Mono<String> listParts(String bucket, String key, String uploadId, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            ListPartsRequest request = ListPartsRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .build();
                ListPartsResponse response = routedClient(identity, bucket, RequiredCapability.MULTIPART_UPLOAD)
                    .listParts(request);
            return response.parts().stream()
                    .map(part -> part.partNumber() + ":" + part.eTag())
                    .collect(Collectors.joining("\n"));
        });
    }

    @Override
    public Mono<String> getBucketVersioning(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            GetBucketVersioningRequest request = GetBucketVersioningRequest.builder().bucket(bucket).build();
            GetBucketVersioningResponse response = routedClient(identity, bucket, RequiredCapability.VERSIONING)
                    .getBucketVersioning(request);
            return response.statusAsString();
        });
    }

    @Override
    public Mono<String> putBucketVersioning(String bucket, String status, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            BucketVersioningStatus targetStatus = "Enabled".equalsIgnoreCase(status)
                    ? BucketVersioningStatus.ENABLED
                    : BucketVersioningStatus.SUSPENDED;
            VersioningConfiguration versioning = VersioningConfiguration.builder().status(targetStatus).build();
            PutBucketVersioningRequest request = PutBucketVersioningRequest.builder()
                    .bucket(bucket)
                    .versioningConfiguration(versioning)
                    .build();
            routedClient(identity, bucket, RequiredCapability.VERSIONING).putBucketVersioning(request);
            return targetStatus.toString();
        });
    }

    @Override
    public Mono<String> putObjectTagging(String bucket, String key, Map<String, String> tags, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            List<Tag> tagSet = tags == null ? List.of() : tags.entrySet().stream()
                    .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                    .toList();
            PutObjectTaggingRequest request = PutObjectTaggingRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .tagging(Tagging.builder().tagSet(tagSet).build())
                    .build();
            routedClient(identity, bucket, RequiredCapability.OBJECT_WRITE).putObjectTagging(request);
            return "OK";
        });
    }

    @Override
    public Mono<Map<String, String>> getObjectTagging(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            GetObjectTaggingRequest request = GetObjectTaggingRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            GetObjectTaggingResponse response = routedClient(identity, bucket, RequiredCapability.OBJECT_READ)
                    .getObjectTagging(request);
            return response.tagSet().stream().collect(Collectors.toMap(Tag::key, Tag::value));
        });
    }

    @Override
    public Mono<Void> deleteObjectTagging(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromRunnable(() -> {
            assertTenantBucketAccess(bucket, identity);
            DeleteObjectTaggingRequest request = DeleteObjectTaggingRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            routedClient(identity, bucket, RequiredCapability.OBJECT_DELETE).deleteObjectTagging(request);
        });
    }

    @Override
    public Mono<String> getBucketPolicy(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            GetBucketPolicyRequest request = GetBucketPolicyRequest.builder().bucket(bucket).build();
            GetBucketPolicyResponse response = routedClient(identity, bucket, RequiredCapability.OBJECT_READ)
                    .getBucketPolicy(request);
            return response.policy();
        });
    }

    @Override
    public Mono<String> putBucketPolicy(String bucket, String policyJson, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            PutBucketPolicyRequest request = PutBucketPolicyRequest.builder()
                    .bucket(bucket)
                    .policy(policyJson)
                    .build();
            routedClient(identity, bucket, RequiredCapability.OBJECT_WRITE).putBucketPolicy(request);
            return "OK";
        });
    }

    @Override
    public Mono<Void> deleteBucketPolicy(String bucket, NormalizedIdentity identity) {
        return Mono.fromRunnable(() -> {
            assertTenantBucketAccess(bucket, identity);
            DeleteBucketPolicyRequest request = DeleteBucketPolicyRequest.builder().bucket(bucket).build();
            routedClient(identity, bucket, RequiredCapability.OBJECT_DELETE).deleteBucketPolicy(request);
        });
    }

    @Override
    public Mono<String> getObjectAcl(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            GetObjectAclRequest request = GetObjectAclRequest.builder().bucket(bucket).key(key).build();
            GetObjectAclResponse response = routedClient(identity, bucket, RequiredCapability.OBJECT_READ)
                    .getObjectAcl(request);
            return response.grants().stream()
                    .map(grant -> grant.permissionAsString())
                    .collect(Collectors.joining(","));
        });
    }

    @Override
    public Mono<String> putObjectAcl(String bucket, String key, String acl, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            ObjectCannedACL cannedAcl = ObjectCannedACL.fromValue(acl.toLowerCase(Locale.ROOT));
            PutObjectAclRequest request = PutObjectAclRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .acl(cannedAcl)
                    .build();
            routedClient(identity, bucket, RequiredCapability.OBJECT_WRITE).putObjectAcl(request);
            return cannedAcl.toString();
        });
    }

    @Override
    public Mono<String> getBucketAcl(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            GetBucketAclRequest request = GetBucketAclRequest.builder().bucket(bucket).build();
            GetBucketAclResponse response = routedClient(identity, bucket, RequiredCapability.OBJECT_READ)
                    .getBucketAcl(request);
            return response.grants().stream()
                    .map(grant -> grant.permissionAsString())
                    .collect(Collectors.joining(","));
        });
    }

    @Override
    public Mono<String> putBucketAcl(String bucket, String acl, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            BucketCannedACL cannedAcl = BucketCannedACL.fromValue(acl.toLowerCase(Locale.ROOT));
            PutBucketAclRequest request = PutBucketAclRequest.builder()
                    .bucket(bucket)
                    .acl(cannedAcl)
                    .build();
            routedClient(identity, bucket, RequiredCapability.OBJECT_WRITE).putBucketAcl(request);
            return cannedAcl.toString();
        });
    }

    @Override
    public Mono<String> copyObject(
        String sourceBucket,
        String sourceKey,
        String destinationBucket,
        String destinationKey,
        NormalizedIdentity identity
    ) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(sourceBucket, identity);
            assertTenantBucketAccess(destinationBucket, identity);
            CopyObjectRequest request = CopyObjectRequest.builder()
                    .copySource(sourceBucket + "/" + sourceKey)
                    .destinationBucket(destinationBucket)
                    .destinationKey(destinationKey)
                    .build();
            CopyObjectResponse response = routedClient(identity, destinationBucket, RequiredCapability.OBJECT_WRITE)
                    .copyObject(request);
            return response.copyObjectResult().eTag();
        });
    }

    @Override
    public Mono<String> getBucketLocation(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            GetBucketLocationRequest request = GetBucketLocationRequest.builder().bucket(bucket).build();
            GetBucketLocationResponse response = routedClient(identity, bucket, RequiredCapability.OBJECT_READ)
                    .getBucketLocation(request);
            String location = response.locationConstraintAsString();
            return location == null || location.isBlank() ? "us-east-1" : location;
        });
    }
}
