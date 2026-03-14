package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderNeutralParityContractTest {

    @Test
    void crudContractIsConsistentAcrossAllProviderProfiles() {
        ProviderCapabilityRegistry capabilityRegistry = new ProviderCapabilityRegistry();
        CapabilityEnforcingObjectStorageAdapter adapter = new CapabilityEnforcingObjectStorageAdapter(
            new InMemoryObjectStorageAdapter(),
            capabilityRegistry,
            (connectionConfig, capability, objectKey) -> {
                // no-op for parity contract checks
            }
        );

        for (ProviderType providerType : ProviderType.values()) {
            ProviderConnectionConfig config = ProviderConnectionConfig.of(providerType, "http://example.invalid", "id", "secret");
            ObjectKey key = new ObjectKey("parity-" + providerType.name().toLowerCase(), "tenant-a/path/object.txt");
            byte[] payload = ("payload-" + providerType.name()).getBytes(StandardCharsets.UTF_8);

            adapter.putObject(config, new PutObjectCommand(key, payload, "text/plain", Map.of("provider", providerType.name())));
            StoredObject stored = adapter.getObject(config, key);
            adapter.deleteObject(config, key);

            assertArrayEquals(payload, stored.payload(), "payload mismatch for " + providerType);
            assertEquals(providerType.name(), stored.metadata().get("provider"), "metadata mismatch for " + providerType);
            assertThrows(ObjectNotFoundException.class, () -> adapter.getObject(config, key), "delete contract mismatch for " + providerType);
        }
    }

    @Test
    void versioningAndMultipartParityMatchesCapabilityProfiles() {
        ProviderSelectionService selectionService = new ProviderSelectionService(new ProviderCapabilityRegistry());

        Set<ProviderType> multipartProviders = selectionService.providersSupporting(EnumSet.of(ProviderCapability.MULTIPART_UPLOAD));
        assertEquals(EnumSet.of(ProviderType.AWS_S3, ProviderType.GCS, ProviderType.AZURE_BLOB), multipartProviders);

        Set<ProviderType> versioningProviders = selectionService.providersSupporting(EnumSet.of(ProviderCapability.VERSIONING));
        assertEquals(EnumSet.of(ProviderType.AWS_S3, ProviderType.GCS), versioningProviders);

        Set<ProviderType> multipartAndVersioning = selectionService.providersSupporting(
            EnumSet.of(ProviderCapability.MULTIPART_UPLOAD, ProviderCapability.VERSIONING)
        );
        assertEquals(EnumSet.of(ProviderType.AWS_S3, ProviderType.GCS), multipartAndVersioning);

        assertTrue(!multipartAndVersioning.contains(ProviderType.AZURE_BLOB));
        assertTrue(!multipartAndVersioning.contains(ProviderType.LOCAL_FILESYSTEM));
    }

    private static final class InMemoryObjectStorageAdapter implements ObjectStorageAdapter {
        private final Map<String, StoredObject> storage = new HashMap<>();

        @Override
        public void putObject(ProviderConnectionConfig connectionConfig, PutObjectCommand command) {
            storage.put(
                key(command.objectKey()),
                new StoredObject(command.objectKey(), command.payload(), command.contentType(), command.metadata())
            );
        }

        @Override
        public StoredObject getObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey) {
            StoredObject storedObject = storage.get(key(objectKey));
            if (storedObject == null) {
                throw new ObjectNotFoundException("Not found");
            }
            return storedObject;
        }

        @Override
        public void deleteObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey) {
            storage.remove(key(objectKey));
        }

        private String key(ObjectKey objectKey) {
            return objectKey.bucket() + "/" + objectKey.key();
        }
    }
}
