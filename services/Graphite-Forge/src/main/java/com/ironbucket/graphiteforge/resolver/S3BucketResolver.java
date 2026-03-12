package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.model.S3Bucket;
import com.ironbucket.graphiteforge.service.IronBucketS3Service;

import java.util.List;

public class S3BucketResolver {

    private final IronBucketS3Service s3Service;

    public S3BucketResolver() {
        this(new IronBucketS3Service());
    }

    public S3BucketResolver(IronBucketS3Service s3Service) {
        this.s3Service = s3Service;
    }

    public List<S3Bucket> listBuckets(String jwtToken) {
        return s3Service.listBuckets(jwtToken);
    }

    public S3Bucket getBucket(String jwtToken, String bucketName) {
        return s3Service.getBucket(jwtToken, bucketName);
    }

    public S3Bucket createBucket(String jwtToken, String bucketName, String ownerTenant) {
        return s3Service.createBucket(jwtToken, bucketName, ownerTenant);
    }

    public boolean deleteBucket(String jwtToken, String bucketName) {
        return s3Service.deleteBucket(jwtToken, bucketName);
    }
}
