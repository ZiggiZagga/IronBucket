package com.ironbucket.graphiteforge.exception;

public class BucketNotFoundException extends RuntimeException {
    public BucketNotFoundException(String bucketName) {
        super("Bucket not found: " + bucketName);
    }
}
