package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantAwareObjectStorageServiceTest {

    @Test
    void putRoutesThroughBucketOverrideProviderWhenCapabilityMatches() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setBucketProvider("tenant-a", "orders", ProviderType.GCS);

        ProviderSelectionService selectionService = new ProviderSelectionService(new ProviderCapabilityRegistry(), tenantRegistry);
        RecordingObjectStorageAdapter delegate = new RecordingObjectStorageAdapter();

        TenantAwareObjectStorageService service = new TenantAwareObjectStorageService(
            delegate,
            selectionService,
            providerConfigs()
        );

        service.putObject(
            "tenant-a",
            new PutObjectCommand(new ObjectKey("orders", "invoice.json"), "ok".getBytes(StandardCharsets.UTF_8), "application/json", Map.of())
        );

        assertEquals(ProviderType.GCS, delegate.lastProviderType());
    }

    @Test
    void getUsesTenantDefaultWhenNoBucketOverrideExists() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setTenantDefaultProvider("tenant-a", ProviderType.AZURE_BLOB);

        ProviderSelectionService selectionService = new ProviderSelectionService(new ProviderCapabilityRegistry(), tenantRegistry);
        RecordingObjectStorageAdapter delegate = new RecordingObjectStorageAdapter();

        TenantAwareObjectStorageService service = new TenantAwareObjectStorageService(
            delegate,
            selectionService,
            providerConfigs()
        );

        service.getObject("tenant-a", new ObjectKey("orders", "invoice.json"));

        assertEquals(ProviderType.AZURE_BLOB, delegate.lastProviderType());
    }

    @Test
    void throwsWhenSelectedProviderHasNoConfiguredConnection() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setTenantDefaultProvider("tenant-a", ProviderType.AZURE_BLOB);

        ProviderSelectionService selectionService = new ProviderSelectionService(new ProviderCapabilityRegistry(), tenantRegistry);
        RecordingObjectStorageAdapter delegate = new RecordingObjectStorageAdapter();
        Map<ProviderType, ProviderConnectionConfig> configs = new EnumMap<>(ProviderType.class);
        configs.put(ProviderType.AWS_S3, ProviderConnectionConfig.of(ProviderType.AWS_S3, "http://aws", "a", "b"));

        TenantAwareObjectStorageService service = new TenantAwareObjectStorageService(delegate, selectionService, configs);

        assertThrows(
            ProviderRoutingException.class,
            () -> service.deleteObject("tenant-a", new ObjectKey("orders", "invoice.json"))
        );
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

        private ProviderType lastProviderType() {
            return lastProviderType;
        }
    }
}