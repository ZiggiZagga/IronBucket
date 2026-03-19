package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiProviderFailoverRoutingIntegrationTest {

    @Test
    void deniedPrimaryProviderFailsOverToNextCapabilityCompatibleProvider() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setBucketProvider("tenant-a", "tenant-a-objects", ProviderType.AWS_S3);

        ProviderSelectionService selectionService = new ProviderSelectionService(
            new ProviderCapabilityRegistry(),
            tenantRegistry
        );

        RecordingObjectStorageAdapter delegate = new RecordingObjectStorageAdapter();
        TenantAwareObjectStorageService service = new TenantAwareObjectStorageService(
            delegate,
            selectionService,
            providerConfigs(),
            new ProviderRoutingPolicy(
                EnumSet.of(ProviderType.AWS_S3, ProviderType.GCS, ProviderType.AZURE_BLOB),
                EnumSet.of(ProviderType.AWS_S3)
            ),
            new NoOpProviderOperationAuditor(),
            0L
        );

        service.getObject("tenant-a", new ObjectKey("tenant-a-objects", "path/item.json"));

        assertEquals(ProviderType.GCS, delegate.lastProviderType);
    }

    private static Map<ProviderType, ProviderConnectionConfig> providerConfigs() {
        Map<ProviderType, ProviderConnectionConfig> configs = new EnumMap<>(ProviderType.class);
        configs.put(ProviderType.AWS_S3, ProviderConnectionConfig.of(ProviderType.AWS_S3, "http://aws", "a", "b"));
        configs.put(ProviderType.GCS, ProviderConnectionConfig.of(ProviderType.GCS, "http://gcs", "a", "b"));
        configs.put(ProviderType.AZURE_BLOB, ProviderConnectionConfig.of(ProviderType.AZURE_BLOB, "http://azure", "a", "b"));
        configs.put(ProviderType.LOCAL_FILESYSTEM, ProviderConnectionConfig.of(ProviderType.LOCAL_FILESYSTEM, "file:///tmp", "a", "b"));
        return configs;
    }

    private static final class RecordingObjectStorageAdapter implements ObjectStorageAdapter {
        private ProviderType lastProviderType;

        @Override
        public void putObject(ProviderConnectionConfig connectionConfig, PutObjectCommand command) {
            lastProviderType = connectionConfig.providerType();
        }

        @Override
        public StoredObject getObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey) {
            lastProviderType = connectionConfig.providerType();
            return new StoredObject(objectKey, new byte[0], "application/octet-stream", Map.of());
        }

        @Override
        public void deleteObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey) {
            lastProviderType = connectionConfig.providerType();
        }
    }
}
