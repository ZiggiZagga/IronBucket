package com.ironbucket.graphiteforge.service;

import com.ironbucket.graphiteforge.exception.BucketNotFoundException;
import com.ironbucket.graphiteforge.model.S3Bucket;
import com.ironbucket.graphiteforge.model.S3Object;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class IronBucketS3Service {

    private final List<S3Bucket> buckets = new CopyOnWriteArrayList<>();
    private final List<S3Object> objects = new CopyOnWriteArrayList<>();

    public List<S3Bucket> listBuckets(String jwtToken) {
        return List.copyOf(buckets);
    }

    public S3Bucket getBucket(String jwtToken, String bucketName) {
        return buckets.stream()
            .filter(bucket -> bucket.name().equals(bucketName))
            .findFirst()
            .orElseThrow(() -> new BucketNotFoundException(bucketName));
    }

    public S3Bucket createBucket(String jwtToken, String bucketName, String ownerTenant) {
        S3Bucket bucket = new S3Bucket(bucketName, Instant.now(), ownerTenant);
        buckets.add(bucket);
        return bucket;
    }

    public boolean deleteBucket(String jwtToken, String bucketName) {
        objects.removeIf(object -> object.bucketName().equals(bucketName));
        return buckets.removeIf(bucket -> bucket.name().equals(bucketName));
    }

    public List<S3Object> listObjects(String jwtToken, String bucketName, String prefix) {
        return objects.stream()
            .filter(object -> object.bucketName().equals(bucketName))
            .filter(object -> prefix == null || prefix.isBlank() || object.key().startsWith(prefix))
            .toList();
    }

    public S3Object getObject(String jwtToken, String bucketName, String objectKey) {
        return objects.stream()
            .filter(object -> object.bucketName().equals(bucketName) && object.key().equals(objectKey))
            .findFirst()
            .orElse(new S3Object(objectKey, bucketName, 0L, Instant.now(), "application/octet-stream", Map.of()));
    }

    public S3Object uploadObject(String jwtToken, String bucketName, String objectKey, long size, String contentType) {
        S3Object object = new S3Object(objectKey, bucketName, size, Instant.now(), contentType, Map.of());
        objects.removeIf(existing -> existing.bucketName().equals(bucketName) && existing.key().equals(objectKey));
        objects.add(object);
        return object;
    }

    public boolean deleteObject(String jwtToken, String bucketName, String objectKey) {
        return objects.removeIf(object -> object.bucketName().equals(bucketName) && object.key().equals(objectKey));
    }

    public String getPresignedUrl(String jwtToken, String bucketName, String objectKey, int expiresIn) {
        return "https://s3.local/" + bucketName + "/" + objectKey + "?expires=" + expiresIn;
    }
}
