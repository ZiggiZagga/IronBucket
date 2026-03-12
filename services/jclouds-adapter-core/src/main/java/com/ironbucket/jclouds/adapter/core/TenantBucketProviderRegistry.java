package com.ironbucket.jclouds.adapter.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class TenantBucketProviderRegistry {
    private final Map<String, ProviderType> tenantDefaults = new ConcurrentHashMap<>();
    private final Map<TenantBucketKey, ProviderType> bucketOverrides = new ConcurrentHashMap<>();

    public void setTenantDefaultProvider(String tenantId, ProviderType providerType) {
        tenantDefaults.put(normalize(tenantId, "tenantId"), requireProvider(providerType));
    }

    public void setBucketProvider(String tenantId, String bucketName, ProviderType providerType) {
        bucketOverrides.put(
            new TenantBucketKey(normalize(tenantId, "tenantId"), normalize(bucketName, "bucketName")),
            requireProvider(providerType)
        );
    }

    public Optional<ProviderType> bucketProvider(String tenantId, String bucketName) {
        return Optional.ofNullable(
            bucketOverrides.get(new TenantBucketKey(normalize(tenantId, "tenantId"), normalize(bucketName, "bucketName")))
        );
    }

    public Optional<ProviderType> tenantDefaultProvider(String tenantId) {
        return Optional.ofNullable(tenantDefaults.get(normalize(tenantId, "tenantId")));
    }

    private static ProviderType requireProvider(ProviderType providerType) {
        if (providerType == null) {
            throw new IllegalArgumentException("providerType must not be null");
        }
        return providerType;
    }

    private static String normalize(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private record TenantBucketKey(String tenantId, String bucketName) {
    }
}