package com.ironbucket.jclouds.adapter.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class TenantAwareObjectStorageService {
    private final ObjectStorageAdapter objectStorageAdapter;
    private final ProviderSelectionService providerSelectionService;
    private final Map<ProviderType, ProviderConnectionConfig> providerConfigs;

    public TenantAwareObjectStorageService(
        ObjectStorageAdapter objectStorageAdapter,
        ProviderSelectionService providerSelectionService,
        Map<ProviderType, ProviderConnectionConfig> providerConfigs
    ) {
        this.objectStorageAdapter = objectStorageAdapter;
        this.providerSelectionService = providerSelectionService;
        EnumMap<ProviderType, ProviderConnectionConfig> copiedConfigs = new EnumMap<>(ProviderType.class);
        if (providerConfigs != null) {
            copiedConfigs.putAll(providerConfigs);
        }
        this.providerConfigs = Collections.unmodifiableMap(copiedConfigs);
    }

    public void putObject(String tenantId, PutObjectCommand command) {
        ProviderConnectionConfig config = resolveConfig(tenantId, command.objectKey(), Set.of(ProviderCapability.OBJECT_WRITE));
        objectStorageAdapter.putObject(config, command);
    }

    public StoredObject getObject(String tenantId, ObjectKey objectKey) {
        ProviderConnectionConfig config = resolveConfig(tenantId, objectKey, Set.of(ProviderCapability.OBJECT_READ));
        return objectStorageAdapter.getObject(config, objectKey);
    }

    public void deleteObject(String tenantId, ObjectKey objectKey) {
        ProviderConnectionConfig config = resolveConfig(tenantId, objectKey, Set.of(ProviderCapability.OBJECT_DELETE));
        objectStorageAdapter.deleteObject(config, objectKey);
    }

    private ProviderConnectionConfig resolveConfig(
        String tenantId,
        ObjectKey objectKey,
        Set<ProviderCapability> requiredCapabilities
    ) {
        ProviderType providerType = providerSelectionService.selectProvider(tenantId, objectKey.bucket(), requiredCapabilities)
            .orElseThrow(() -> new ProviderRoutingException(
                "No provider can satisfy capabilities " + requiredCapabilities
                    + " for tenant " + tenantId + " and bucket " + objectKey.bucket()
            ));

        ProviderConnectionConfig config = providerConfigs.get(providerType);
        if (config == null) {
            throw new ProviderRoutingException("No connection config registered for provider " + providerType);
        }

        return config;
    }
}