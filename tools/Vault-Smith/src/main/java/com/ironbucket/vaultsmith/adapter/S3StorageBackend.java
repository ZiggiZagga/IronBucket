package com.ironbucket.vaultsmith.adapter;

import com.ironbucket.vaultsmith.model.S3CopyResult;
import com.ironbucket.vaultsmith.model.S3ObjectMetadata;
import com.ironbucket.vaultsmith.model.S3UploadResult;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Cloud-agnostic S3-compatible storage backend interface.
 * Abstracts S3 operations across AWS S3, MinIO, Ceph RGW, and other S3-compatible services.
 */
public interface S3StorageBackend {

    /**
     * Initialize the S3 backend with configuration.
     */
    void initialize() throws Exception;

    /**
     * Test connectivity to the S3 backend.
     */
    boolean testConnectivity() throws Exception;

    /**
     * Create a new bucket.
     */
    void createBucket(String bucketName) throws Exception;

    /**
     * Check if a bucket exists.
     */
    boolean bucketExists(String bucketName) throws Exception;

    /**
     * Delete a bucket (must be empty).
     */
    void deleteBucket(String bucketName) throws Exception;

    /**
     * List all buckets.
     */
    Set<String> listBuckets() throws Exception;

    /**
     * Get object metadata without downloading.
     */
    S3ObjectMetadata getObjectMetadata(String bucketName, String objectKey) throws Exception;

    /**
     * Upload an object to S3.
     */
    S3UploadResult uploadObject(String bucketName, String objectKey, InputStream inputStream, long contentLength, String contentType) throws Exception;

    /**
     * Download an object from S3.
     */
    InputStream downloadObject(String bucketName, String objectKey) throws Exception;

    /**
     * Delete an object from S3.
     */
    void deleteObject(String bucketName, String objectKey) throws Exception;

    /**
     * List objects in a bucket with optional prefix.
     */
    Set<String> listObjects(String bucketName, String prefix) throws Exception;

    /**
     * Copy an object from source to destination.
     */
    S3CopyResult copyObject(String sourceBucket, String sourceKey, String destBucket, String destKey) throws Exception;

    /**
     * Initiate a multipart upload.
     */
    String initiateMultipartUpload(String bucketName, String objectKey) throws Exception;

    /**
     * Upload a part for multipart upload.
     */
    String uploadPart(String bucketName, String objectKey, String uploadId, int partNumber, InputStream inputStream, long contentLength) throws Exception;

    /**
     * Complete a multipart upload.
     */
    void completeMultipartUpload(String bucketName, String objectKey, String uploadId, Map<Integer, String> partETags) throws Exception;

    /**
     * Abort a multipart upload.
     */
    void abortMultipartUpload(String bucketName, String objectKey, String uploadId) throws Exception;

    /**
     * Shutdown the backend and release resources.
     */
    void shutdown();
}
