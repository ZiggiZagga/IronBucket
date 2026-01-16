package com.ironbucket.brazznossel.service;

import com.ironbucket.brazznossel.model.NormalizedIdentity;
import reactor.core.publisher.Mono;

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
    
    /**
     * Get an object from a bucket.
     * 
     * @param bucket The bucket name
     * @param key The object key
     * @param identity The user's normalized identity
     * @return A Mono containing the object bytes
     */
    Mono<byte[]> getObject(String bucket, String key, NormalizedIdentity identity);
    
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
    
    /**
     * Initiate a multipart upload.
     * 
     * @param bucket The bucket name
     * @param key The object key
     * @param identity The user's normalized identity
     * @return A Mono containing the upload ID
     */
    Mono<String> initiateMultipartUpload(String bucket, String key, NormalizedIdentity identity);
}
