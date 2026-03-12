package com.ironbucket.graphiteforge.exception;

public class IronBucketServiceException extends RuntimeException {
    public IronBucketServiceException(String message) {
        super(message);
    }

    public IronBucketServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
