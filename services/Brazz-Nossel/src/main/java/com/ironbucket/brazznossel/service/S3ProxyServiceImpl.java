package com.ironbucket.brazznossel.service;

import com.ironbucket.brazznossel.model.NormalizedIdentity;
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
import java.util.List;
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
    
    public S3ProxyServiceImpl(
            @Value("${app.s3.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.s3.access-key:minioadmin}") String accessKey,
            @Value("${app.s3.secret-key:minioadmin}") String secretKey,
            @Value("${app.s3.region:us-east-1}") String region) {
        
        logger.info("Initializing S3 Proxy Service with endpoint: {}", endpoint);
        
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true) // Required for MinIO
                .build();
    }

    private void assertTenantBucketAccess(String bucket, NormalizedIdentity identity) {
        if (!bucket.startsWith(identity.getTenantId() + "-")) {
            throw new SecurityException("Access denied: bucket does not belong to tenant " + identity.getTenantId());
        }
    }
    
    @Override
    public Mono<String> listBuckets(NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Listing buckets for user: {}, tenant: {}", 
                    identity.getPreferredUsername(), identity.getTenantId());
            
            ListBucketsResponse response = s3Client.listBuckets();
            
            // Filter buckets by tenant prefix
            String tenantPrefix = identity.getTenantId() + "-";
            StringBuilder result = new StringBuilder("Buckets for tenant " + identity.getTenantId() + ":\n");
            
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
            s3Client.createBucket(request);
            return bucket;
        });
    }

    @Override
    public Mono<Void> deleteBucket(String bucket, NormalizedIdentity identity) {
        return Mono.fromRunnable(() -> {
            assertTenantBucketAccess(bucket, identity);
            DeleteBucketRequest request = DeleteBucketRequest.builder().bucket(bucket).build();
            s3Client.deleteBucket(request);
        });
    }

    @Override
    public Mono<String> listObjects(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).build();
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            return response.contents().stream().map(S3Object::key).collect(Collectors.joining("\n"));
        });
    }
    
    @Override
    public Mono<byte[]> getObject(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Getting object: {}/{} for user: {}", bucket, key, identity.getPreferredUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            return s3Client.getObjectAsBytes(request).asByteArray();
        });
    }

    @Override
    public Mono<String> headObject(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(key).build();
            HeadObjectResponse response = s3Client.headObject(request);
            return response.eTag();
        });
    }

    @Override
    public Mono<String> headBucket(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            HeadBucketRequest request = HeadBucketRequest.builder().bucket(bucket).build();
            s3Client.headBucket(request);
            return bucket;
        });
    }
    
    @Override
    public Mono<byte[]> getObjectRange(String bucket, String key, long start, long end, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Getting object range: {}/{}[{}-{}] for user: {}", 
                    bucket, key, start, end, identity.getPreferredUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range(String.format("bytes=%d-%d", start, end))
                    .build();
            
            return s3Client.getObjectAsBytes(request).asByteArray();
        });
    }
    
    @Override
    public Mono<String> putObject(String bucket, String key, byte[] content, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Putting object: {}/{} ({} bytes) for user: {}", 
                    bucket, key, content.length, identity.getPreferredUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .metadata(java.util.Map.of(
                            "uploaded-by", identity.getPreferredUsername(),
                            "tenant", identity.getTenantId()
                    ))
                    .build();
            
            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromBytes(content));
            return response.eTag();
        });
    }
    
    @Override
    public Mono<Void> deleteObject(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromRunnable(() -> {
            logger.info("Deleting object: {}/{} for user: {}", bucket, key, identity.getPreferredUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            s3Client.deleteObject(request);
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
            s3Client.deleteObject(request);
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
            return s3Client.getObjectAsBytes(request).asByteArray();
        });
    }

    @Override
    public Mono<String> listObjectVersions(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            ListObjectVersionsRequest request = ListObjectVersionsRequest.builder().bucket(bucket).build();
            ListObjectVersionsResponse response = s3Client.listObjectVersions(request);
            return response.versions().stream()
                    .map(v -> v.key() + ":" + v.versionId())
                    .collect(Collectors.joining("\n"));
        });
    }
    
    @Override
    public Mono<String> initiateMultipartUpload(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Initiating multipart upload: {}/{} for user: {}", 
                    bucket, key, identity.getPreferredUsername());
            
            assertTenantBucketAccess(bucket, identity);
            
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .metadata(java.util.Map.of(
                            "uploaded-by", identity.getPreferredUsername(),
                            "tenant", identity.getTenantId()
                    ))
                    .build();
            
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
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
            UploadPartResponse response = s3Client.uploadPart(request, RequestBody.fromBytes(content));
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
            CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(request);
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
            s3Client.abortMultipartUpload(request);
        });
    }

    @Override
    public Mono<String> listMultipartUploads(String bucket, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            assertTenantBucketAccess(bucket, identity);
            ListMultipartUploadsRequest request = ListMultipartUploadsRequest.builder().bucket(bucket).build();
            ListMultipartUploadsResponse response = s3Client.listMultipartUploads(request);
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
            ListPartsResponse response = s3Client.listParts(request);
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
            GetBucketVersioningResponse response = s3Client.getBucketVersioning(request);
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
            s3Client.putBucketVersioning(request);
            return targetStatus.toString();
        });
    }
}
