package com.ironbucket.jclouds.adapter.core;

public record ObjectKey(String bucket, String key) {
    public ObjectKey {
        if (isBlank(bucket)) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        if (isBlank(key)) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}