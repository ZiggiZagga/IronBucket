package com.ironbucket.brazznossel.service;

import com.ironbucket.pactumscroll.identity.NormalizedIdentity;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.s3.model.CompletedPart;

/**
 * S3ProxyService - Interface for S3 proxy operations.
 * 
 * This service handles the actual proxying of S3 operations to the backend
 * (MinIO, S3, Ceph RGW, etc.). It assumes authentication and authorization
 * have already been validated by Sentinel-Gear and Claimspindel.
 */
public interface S3ProxyService {
    
    /**
     * List buckets available to the user.
     * 
     * @param identity The user's normalized identity (for context)
     * @return A Mono containing the bucket list response
     */
    Mono<String> listBuckets(NormalizedIdentity identity);

    Mono<String> createBucket(String bucket, NormalizedIdentity identity);

    Mono<Void> deleteBucket(String bucket, NormalizedIdentity identity);

    Mono<String> listObjects(String bucket, NormalizedIdentity identity);
    
    /**
     * Get an object from a bucket.
     * 
     * @param bucket The bucket name
     * @param key The object key
     * @param identity The user's normalized identity
     * @return A Mono containing the object bytes
     */
    Mono<byte[]> getObject(String bucket, String key, NormalizedIdentity identity);

    Mono<String> headObject(String bucket, String key, NormalizedIdentity identity);

    Mono<String> headBucket(String bucket, NormalizedIdentity identity);
    
    /**
     * Get a range of bytes from an object (partial read).
     * 
     * @param bucket The bucket name
     * @param key The object key
     * @param start Start byte offset
     * @param end End byte offset
     * @param identity The user's normalized identity
     * @return A Mono containing the requested bytes
     */
    Mono<byte[]> getObjectRange(String bucket, String key, long start, long end, NormalizedIdentity identity);
    
    /**
     * Put (upload) an object to a bucket.
     * 
     * @param bucket The bucket name
     * @param key The object key
     * @param content The object content (bytes)
     * @param identity The user's normalized identity
     * @return A Mono containing the ETag response
     */
    Mono<String> putObject(String bucket, String key, byte[] content, NormalizedIdentity identity);
    
    /**
     * Delete an object from a bucket.
     * 
     * @param bucket The bucket name
     * @param key The object key
     * @param identity The user's normalized identity
     * @return A Mono that completes when deletion is done
     */
    Mono<Void> deleteObject(String bucket, String key, NormalizedIdentity identity);

    Mono<Void> deleteObjectVersion(String bucket, String key, String versionId, NormalizedIdentity identity);

    Mono<byte[]> getObjectVersion(String bucket, String key, String versionId, NormalizedIdentity identity);

    Mono<String> listObjectVersions(String bucket, NormalizedIdentity identity);
    
    /**
     * Initiate a multipart upload.
     * 
     * @param bucket The bucket name
     * @param key The object key
     * @param identity The user's normalized identity
     * @return A Mono containing the upload ID
     */
    Mono<String> initiateMultipartUpload(String bucket, String key, NormalizedIdentity identity);

    Mono<String> uploadPart(String bucket, String key, String uploadId, int partNumber, byte[] content, NormalizedIdentity identity);

    Mono<String> completeMultipartUpload(String bucket, String key, String uploadId, List<CompletedPart> parts, NormalizedIdentity identity);

    Mono<Void> abortMultipartUpload(String bucket, String key, String uploadId, NormalizedIdentity identity);

    Mono<String> listMultipartUploads(String bucket, NormalizedIdentity identity);

    Mono<String> listParts(String bucket, String key, String uploadId, NormalizedIdentity identity);

    Mono<String> getBucketVersioning(String bucket, NormalizedIdentity identity);

    Mono<String> putBucketVersioning(String bucket, String status, NormalizedIdentity identity);

    Mono<String> putObjectTagging(String bucket, String key, Map<String, String> tags, NormalizedIdentity identity);

    Mono<Map<String, String>> getObjectTagging(String bucket, String key, NormalizedIdentity identity);

    Mono<Void> deleteObjectTagging(String bucket, String key, NormalizedIdentity identity);

    Mono<String> getBucketPolicy(String bucket, NormalizedIdentity identity);

    Mono<String> putBucketPolicy(String bucket, String policyJson, NormalizedIdentity identity);

    Mono<Void> deleteBucketPolicy(String bucket, NormalizedIdentity identity);

    Mono<String> getObjectAcl(String bucket, String key, NormalizedIdentity identity);

    Mono<String> putObjectAcl(String bucket, String key, String acl, NormalizedIdentity identity);

    Mono<String> getBucketAcl(String bucket, NormalizedIdentity identity);

    Mono<String> putBucketAcl(String bucket, String acl, NormalizedIdentity identity);

    Mono<String> copyObject(
        String sourceBucket,
        String sourceKey,
        String destinationBucket,
        String destinationKey,
        NormalizedIdentity identity
    );

    Mono<String> getBucketLocation(String bucket, NormalizedIdentity identity);
}
