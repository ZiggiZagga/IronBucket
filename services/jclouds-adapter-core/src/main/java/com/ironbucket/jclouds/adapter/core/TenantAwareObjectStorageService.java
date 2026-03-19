package com.ironbucket.jclouds.adapter.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TenantAwareObjectStorageService {
    private final ObjectStorageAdapter objectStorageAdapter;
    private final ProviderSelectionService providerSelectionService;
    private final Map<ProviderType, ProviderConnectionConfig> providerConfigs;
    private final ProviderOperationAuditor operationAuditor;
    private final Map<RoutingCacheKey, CachedProviderSelection> routingCache;
    private final long routingCacheTtlMillis;

    public TenantAwareObjectStorageService(
        ObjectStorageAdapter objectStorageAdapter,
        ProviderSelectionService providerSelectionService,
        Map<ProviderType, ProviderConnectionConfig> providerConfigs
    ) {
        this(
            objectStorageAdapter,
            providerSelectionService,
            providerConfigs,
            ProviderRoutingPolicy.unrestricted(),
            new NoOpProviderOperationAuditor(),
            30_000L
        );
    }

    public TenantAwareObjectStorageService(
        ObjectStorageAdapter objectStorageAdapter,
        ProviderSelectionService providerSelectionService,
        Map<ProviderType, ProviderConnectionConfig> providerConfigs,
        ProviderRoutingPolicy routingPolicy,
        ProviderOperationAuditor operationAuditor,
        long routingCacheTtlMillis
    ) {
        this.objectStorageAdapter = objectStorageAdapter;
        this.providerSelectionService = providerSelectionService;
        EnumMap<ProviderType, ProviderConnectionConfig> copiedConfigs = new EnumMap<>(ProviderType.class);
        if (providerConfigs != null) {
            copiedConfigs.putAll(providerConfigs);
        }
        this.providerConfigs = Collections.unmodifiableMap(copiedConfigs);
        this.routingPolicy = routingPolicy == null ? ProviderRoutingPolicy.unrestricted() : routingPolicy;
        this.operationAuditor = operationAuditor == null ? new NoOpProviderOperationAuditor() : operationAuditor;
        this.routingCacheTtlMillis = Math.max(0L, routingCacheTtlMillis);
        this.routingCache = new ConcurrentHashMap<>();
    }

    private final ProviderRoutingPolicy routingPolicy;

    public void putObject(String tenantId, PutObjectCommand command) {
        ProviderConnectionConfig config = resolveConfig(tenantId, command.objectKey(), Set.of(ProviderCapability.OBJECT_WRITE));
        enforceLocalFilesystemBoundaries(config, command.objectKey(), command.payload().length);
        try {
            objectStorageAdapter.putObject(config, command);
            operationAuditor.record(tenantId, config.providerType(), ProviderCapability.OBJECT_WRITE, command.objectKey(), "ALLOW");
        } catch (RuntimeException runtimeException) {
            operationAuditor.record(tenantId, config.providerType(), ProviderCapability.OBJECT_WRITE, command.objectKey(), "ERROR");
            throw runtimeException;
        }
    }

    public StoredObject getObject(String tenantId, ObjectKey objectKey) {
        ProviderConnectionConfig config = resolveConfig(tenantId, objectKey, Set.of(ProviderCapability.OBJECT_READ));
        enforceLocalFilesystemBoundaries(config, objectKey, null);
        try {
            StoredObject object = objectStorageAdapter.getObject(config, objectKey);
            operationAuditor.record(tenantId, config.providerType(), ProviderCapability.OBJECT_READ, objectKey, "ALLOW");
            return object;
        } catch (RuntimeException runtimeException) {
            operationAuditor.record(tenantId, config.providerType(), ProviderCapability.OBJECT_READ, objectKey, "ERROR");
            throw runtimeException;
        }
    }

    public void deleteObject(String tenantId, ObjectKey objectKey) {
        ProviderConnectionConfig config = resolveConfig(tenantId, objectKey, Set.of(ProviderCapability.OBJECT_DELETE));
        enforceLocalFilesystemBoundaries(config, objectKey, null);
        try {
            objectStorageAdapter.deleteObject(config, objectKey);
            operationAuditor.record(tenantId, config.providerType(), ProviderCapability.OBJECT_DELETE, objectKey, "ALLOW");
        } catch (RuntimeException runtimeException) {
            operationAuditor.record(tenantId, config.providerType(), ProviderCapability.OBJECT_DELETE, objectKey, "ERROR");
            throw runtimeException;
        }
    }

    public void invalidateRoutingCacheForTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            routingCache.clear();
            return;
        }
        String normalizedTenant = tenantId.trim();
        routingCache.keySet().removeIf(key -> key.tenantId.equals(normalizedTenant));
    }

    public void invalidateRoutingCacheForBucket(String tenantId, String bucketName) {
        if (tenantId == null || tenantId.isBlank() || bucketName == null || bucketName.isBlank()) {
            return;
        }
        String normalizedTenant = tenantId.trim();
        String normalizedBucket = bucketName.trim();
        routingCache.keySet().removeIf(key -> key.tenantId.equals(normalizedTenant) && key.bucketName.equals(normalizedBucket));
    }

    private ProviderConnectionConfig resolveConfig(
        String tenantId,
        ObjectKey objectKey,
        Set<ProviderCapability> requiredCapabilities
    ) {
        String normalizedTenant = normalize(tenantId, "tenantId");
        String normalizedBucket = normalize(objectKey.bucket(), "bucketName");
        RoutingCacheKey cacheKey = new RoutingCacheKey(normalizedTenant, normalizedBucket, requiredCapabilities);

        ProviderType providerType = cachedProviderSelection(cacheKey)
            .orElseGet(() -> {
                ProviderSelectionDecision decision = providerSelectionService.selectProviderDecision(
                    normalizedTenant,
                    normalizedBucket,
                    requiredCapabilities,
                    routingPolicy
                );

                if (!decision.hasSelection()) {
                    throw new ProviderRoutingException(
                        "No provider can satisfy capabilities " + requiredCapabilities
                            + " for tenant " + normalizedTenant + " and bucket " + normalizedBucket
                            + "; attempted=" + decision.attemptedProviders()
                    );
                }

                routingCache.put(cacheKey, new CachedProviderSelection(decision.selectedProvider(), nowMillis()));
                return decision.selectedProvider();
            });

        ProviderConnectionConfig config = providerConfigs.get(providerType);
        if (config == null) {
            throw new ProviderRoutingException("No connection config registered for provider " + providerType);
        }

        return config;
    }

    private java.util.Optional<ProviderType> cachedProviderSelection(RoutingCacheKey key) {
        if (routingCacheTtlMillis == 0L) {
            return java.util.Optional.empty();
        }
        CachedProviderSelection cached = routingCache.get(key);
        if (cached == null) {
            return java.util.Optional.empty();
        }
        long ageMillis = nowMillis() - cached.createdAtMillis;
        if (ageMillis > routingCacheTtlMillis) {
            routingCache.remove(key);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(cached.providerType);
    }

    private static long nowMillis() {
        return System.currentTimeMillis();
    }

    private static String normalize(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static void enforceLocalFilesystemBoundaries(
        ProviderConnectionConfig config,
        ObjectKey objectKey,
        Integer payloadBytes
    ) {
        if (config.providerType() != ProviderType.LOCAL_FILESYSTEM) {
            return;
        }

        String key = objectKey.key();
        if (key == null || key.isBlank()) {
            throw new PolicyDeniedException("LOCAL_FILESYSTEM policy denied blank object key");
        }

        String normalizedKey = key.replace('\\', '/').trim();
        if (normalizedKey.startsWith("/") || normalizedKey.startsWith("../") || normalizedKey.contains("/../") || normalizedKey.contains("\u0000")) {
            throw new PolicyDeniedException("LOCAL_FILESYSTEM policy denied unsafe object key path: " + key);
        }
        if (normalizedKey.contains("->") || normalizedKey.contains("://") || normalizedKey.matches("^[A-Za-z]:.*")) {
            throw new PolicyDeniedException("LOCAL_FILESYSTEM policy denied symlink/external path marker in key: " + key);
        }

        String maxBytes = config.properties().get("ironbucket.local.max-object-bytes");
        if (payloadBytes != null && maxBytes != null && !maxBytes.isBlank()) {
            long configuredMax;
            try {
                configuredMax = Long.parseLong(maxBytes.trim());
            } catch (NumberFormatException numberFormatException) {
                throw new PolicyDeniedException("LOCAL_FILESYSTEM policy has invalid quota value: " + maxBytes);
            }
            if (payloadBytes > configuredMax) {
                throw new PolicyDeniedException(
                    "LOCAL_FILESYSTEM policy denied object size " + payloadBytes + " > quota " + configuredMax
                );
            }
        }
    }

    private record CachedProviderSelection(ProviderType providerType, long createdAtMillis) {
    }

    private record RoutingCacheKey(String tenantId, String bucketName, Set<ProviderCapability> requiredCapabilities) {
        private RoutingCacheKey {
            requiredCapabilities = requiredCapabilities == null || requiredCapabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(requiredCapabilities);
        }
    }
}