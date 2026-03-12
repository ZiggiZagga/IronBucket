package com.ironbucket.vaultsmith.impl;

import com.ironbucket.vaultsmith.adapter.S3StorageBackend;
import com.ironbucket.vaultsmith.config.S3BackendConfig;
import com.ironbucket.vaultsmith.model.S3CopyResult;
import com.ironbucket.vaultsmith.model.S3ObjectMetadata;
import com.ironbucket.vaultsmith.model.S3UploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * S3 backend implementation using AWS SDK v2.
 * Supports AWS S3, MinIO, Ceph RGW, and other S3-compatible services.
 */
public class AwsS3Backend implements S3StorageBackend {
    private static final Logger logger = LoggerFactory.getLogger(AwsS3Backend.class);

    private final S3BackendConfig config;
    private S3Client s3Client;

    public AwsS3Backend(S3BackendConfig config) {
        this.config = config;
    }

    @Override
    public void initialize() throws Exception {
        logger.info("Initializing AWS SDK S3 backend with provider: {}", config.getProvider());

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())
                ));

        // Configure endpoint if provided (for MinIO, Ceph RGW, etc.)
        if (config.getEndpoint() != null && !config.getEndpoint().isEmpty()) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }

        // Configure region
        if (config.getRegion() != null && !config.getRegion().isEmpty()) {
            builder.region(Region.of(config.getRegion()));
        } else {
            builder.region(Region.US_EAST_1);
        }

        // Path style access for S3-compatible services
        builder.forcePathStyle(config.isPathStyleAccess());

        s3Client = builder.build();
        logger.info("AWS SDK S3 backend initialized successfully");
    }

    @Override
    public boolean testConnectivity() throws Exception {
        try {
            listBuckets();
            logger.info("Connectivity test successful");
            return true;
        } catch (Exception e) {
            logger.error("Connectivity test failed", e);
            return false;
        }
    }

    @Override
    public void createBucket(String bucketName) throws Exception {
        logger.info("Creating bucket: {}", bucketName);
        CreateBucketRequest request = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();
        s3Client.createBucket(request);
        logger.info("Bucket created successfully: {}", bucketName);
    }

    @Override
    public boolean bucketExists(String bucketName) throws Exception {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(request);
            logger.debug("Bucket exists: {}", bucketName);
            return true;
        } catch (NoSuchBucketException e) {
            logger.debug("Bucket does not exist: {}", bucketName);
            return false;
        }
    }

    @Override
    public void deleteBucket(String bucketName) throws Exception {
        logger.info("Deleting bucket: {}", bucketName);
        DeleteBucketRequest request = DeleteBucketRequest.builder()
                .bucket(bucketName)
                .build();
        s3Client.deleteBucket(request);
        logger.info("Bucket deleted successfully: {}", bucketName);
    }

    @Override
    public Set<String> listBuckets() throws Exception {
        logger.debug("Listing buckets");
        ListBucketsResponse response = s3Client.listBuckets();
        Set<String> bucketNames = response.buckets().stream()
                .map(Bucket::name)
                .collect(Collectors.toSet());
        logger.debug("Found {} buckets", bucketNames.size());
        return bucketNames;
    }

    @Override
    public S3ObjectMetadata getObjectMetadata(String bucketName, String objectKey) throws Exception {
        logger.debug("Getting metadata for object: {}/{}", bucketName, objectKey);
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        HeadObjectResponse response = s3Client.headObject(request);

        return new S3ObjectMetadata(
                objectKey,
                response.contentLength(),
                response.contentType(),
                response.eTag(),
                Instant.ofEpochMilli(response.lastModified().toEpochMilli()),
                response.storageClass() != null ? response.storageClass().toString() : "STANDARD"
        );
    }

    @Override
    public S3UploadResult uploadObject(String bucketName, String objectKey, InputStream inputStream, long contentLength, String contentType) throws Exception {
        logger.info("Uploading object: {}/{}", bucketName, objectKey);

        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentLength(contentLength);

        if (contentType != null) {
            requestBuilder.contentType(contentType);
        }

        PutObjectRequest request = requestBuilder.build();
        PutObjectResponse response = s3Client.putObject(request, software.amazon.awssdk.core.sync.RequestBody.fromInputStream(inputStream, contentLength));

        logger.info("Object uploaded successfully: {}/{}, etag: {}", bucketName, objectKey, response.eTag());
        return new S3UploadResult(bucketName, objectKey, response.eTag(), contentLength);
    }

    @Override
    public InputStream downloadObject(String bucketName, String objectKey) throws Exception {
        logger.info("Downloading object: {}/{}", bucketName, objectKey);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        return s3Client.getObject(request);
    }

    @Override
    public void deleteObject(String bucketName, String objectKey) throws Exception {
        logger.info("Deleting object: {}/{}", bucketName, objectKey);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        s3Client.deleteObject(request);
        logger.info("Object deleted successfully: {}/{}", bucketName, objectKey);
    }

    @Override
    public Set<String> listObjects(String bucketName, String prefix) throws Exception {
        logger.debug("Listing objects in bucket: {} with prefix: {}", bucketName, prefix);

        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                .bucket(bucketName);

        if (prefix != null && !prefix.isEmpty()) {
            requestBuilder.prefix(prefix);
        }

        ListObjectsV2Request request = requestBuilder.build();
        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        Set<String> objectKeys = response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toSet());

        logger.debug("Listed {} objects in bucket: {}", objectKeys.size(), bucketName);
        return objectKeys;
    }

    @Override
    public S3CopyResult copyObject(String sourceBucket, String sourceKey, String destBucket, String destKey) throws Exception {
        logger.info("Copying object: {}/{} -> {}/{}", sourceBucket, sourceKey, destBucket, destKey);

        CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(sourceKey)
                .destinationBucket(destBucket)
                .destinationKey(destKey)
                .build();

        CopyObjectResponse response = s3Client.copyObject(request);
        long contentLength = getObjectMetadata(sourceBucket, sourceKey).getContentLength();

        logger.info("Object copied successfully, etag: {}", response.copyObjectResult().eTag());
        return new S3CopyResult(sourceBucket, sourceKey, destBucket, destKey, response.copyObjectResult().eTag(), contentLength);
    }

    @Override
    public String initiateMultipartUpload(String bucketName, String objectKey) throws Exception {
        logger.info("Initiating multipart upload: {}/{}", bucketName, objectKey);
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
        logger.info("Multipart upload initiated with ID: {}", response.uploadId());
        return response.uploadId();
    }

    @Override
    public String uploadPart(String bucketName, String objectKey, String uploadId, int partNumber, InputStream inputStream, long contentLength) throws Exception {
        logger.info("Uploading part {} for multipart upload: {}/{}", partNumber, bucketName, objectKey);

        UploadPartRequest request = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .contentLength(contentLength)
                .build();

        UploadPartResponse response = s3Client.uploadPart(request, software.amazon.awssdk.core.sync.RequestBody.fromInputStream(inputStream, contentLength));
        logger.info("Part {} uploaded with ETag: {}", partNumber, response.eTag());
        return response.eTag();
    }

    @Override
    public void completeMultipartUpload(String bucketName, String objectKey, String uploadId, Map<Integer, String> partETags) throws Exception {
        logger.info("Completing multipart upload: {}/{}", bucketName, objectKey);

        List<CompletedPart> completedParts = partETags.entrySet().stream()
                .map(entry -> CompletedPart.builder()
                        .partNumber(entry.getKey())
                        .eTag(entry.getValue())
                        .build())
                .collect(Collectors.toList());

        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build())
                .build();

        s3Client.completeMultipartUpload(request);
        logger.info("Multipart upload completed successfully");
    }

    @Override
    public void abortMultipartUpload(String bucketName, String objectKey, String uploadId) throws Exception {
        logger.info("Aborting multipart upload: {}/{}", bucketName, objectKey);
        AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .build();
        s3Client.abortMultipartUpload(request);
        logger.info("Multipart upload aborted successfully");
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down AWS SDK S3 backend");
        if (s3Client != null) {
            s3Client.close();
        }
    }
}
