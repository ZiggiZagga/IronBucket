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
            @Value("${s3.endpoint:http://localhost:9000}") String endpoint,
            @Value("${s3.access-key:minioadmin}") String accessKey,
            @Value("${s3.secret-key:minioadmin}") String secretKey,
            @Value("${s3.region:us-east-1}") String region) {
        
        logger.info("Initializing S3 Proxy Service with endpoint: {}", endpoint);
        
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true) // Required for MinIO
                .build();
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
    public Mono<byte[]> getObject(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Getting object: {}/{} for user: {}", bucket, key, identity.getPreferredUsername());
            
            // Enforce tenant isolation
            if (!bucket.startsWith(identity.getTenantId() + "-")) {
                throw new SecurityException("Access denied: bucket does not belong to tenant " + identity.getTenantId());
            }
            
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            return s3Client.getObjectAsBytes(request).asByteArray();
        });
    }
    
    @Override
    public Mono<byte[]> getObjectRange(String bucket, String key, long start, long end, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Getting object range: {}/{}[{}-{}] for user: {}", 
                    bucket, key, start, end, identity.getPreferredUsername());
            
            // Enforce tenant isolation
            if (!bucket.startsWith(identity.getTenantId() + "-")) {
                throw new SecurityException("Access denied: bucket does not belong to tenant " + identity.getTenantId());
            }
            
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
            
            // Enforce tenant isolation
            if (!bucket.startsWith(identity.getTenantId() + "-")) {
                throw new SecurityException("Access denied: bucket does not belong to tenant " + identity.getTenantId());
            }
            
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
            
            // Enforce tenant isolation
            if (!bucket.startsWith(identity.getTenantId() + "-")) {
                throw new SecurityException("Access denied: bucket does not belong to tenant " + identity.getTenantId());
            }
            
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            s3Client.deleteObject(request);
            logger.info("Object deleted successfully: {}/{}", bucket, key);
        });
    }
    
    @Override
    public Mono<String> initiateMultipartUpload(String bucket, String key, NormalizedIdentity identity) {
        return Mono.fromCallable(() -> {
            logger.info("Initiating multipart upload: {}/{} for user: {}", 
                    bucket, key, identity.getPreferredUsername());
            
            // Enforce tenant isolation
            if (!bucket.startsWith(identity.getTenantId() + "-")) {
                throw new SecurityException("Access denied: bucket does not belong to tenant " + identity.getTenantId());
            }
            
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
}
