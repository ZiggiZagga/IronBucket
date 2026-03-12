package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.model.S3Object;
import com.ironbucket.graphiteforge.service.IronBucketS3Service;

import java.util.List;

public class S3ObjectResolver {

    private final IronBucketS3Service s3Service;

    public S3ObjectResolver() {
        this(new IronBucketS3Service());
    }

    public S3ObjectResolver(IronBucketS3Service s3Service) {
        this.s3Service = s3Service;
    }

    public List<S3Object> listObjects(String jwtToken, String bucketName, String prefix) {
        return s3Service.listObjects(jwtToken, bucketName, prefix);
    }

    public S3Object getObject(String jwtToken, String bucketName, String objectKey) {
        return s3Service.getObject(jwtToken, bucketName, objectKey);
    }

    public boolean deleteObject(String jwtToken, String bucketName, String objectKey) {
        return s3Service.deleteObject(jwtToken, bucketName, objectKey);
    }

    public String getPresignedUrl(String jwtToken, String bucketName, String objectKey, int expiresIn) {
        return s3Service.getPresignedUrl(jwtToken, bucketName, objectKey, expiresIn);
    }

    public S3Object uploadObject(String jwtToken, String bucketName, String objectKey, long size, String contentType) {
        return s3Service.uploadObject(jwtToken, bucketName, objectKey, size, contentType);
    }
}
