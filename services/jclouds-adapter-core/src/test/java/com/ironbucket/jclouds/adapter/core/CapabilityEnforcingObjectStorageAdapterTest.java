package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityEnforcingObjectStorageAdapterTest {

    @Test
    void putObjectCallsPolicyAndDelegateWhenCapabilityIsSupported() {
        InMemoryObjectStorageAdapter delegate = new InMemoryObjectStorageAdapter();
        CountingPolicyEnforcer policyEnforcer = new CountingPolicyEnforcer(false);

        CapabilityEnforcingObjectStorageAdapter adapter = new CapabilityEnforcingObjectStorageAdapter(
            delegate,
            new ProviderCapabilityRegistry(),
            policyEnforcer
        );

        ProviderConnectionConfig config = ProviderConnectionConfig.of(ProviderType.AWS_S3, "http://localhost:9000", "a", "b");
        PutObjectCommand command = new PutObjectCommand(new ObjectKey("bucket", "key"), "hello".getBytes(), "text/plain", Map.of());
        adapter.putObject(config, command);

        assertEquals(1, policyEnforcer.assertions());
        assertEquals("hello", new String(delegate.getObject(config, new ObjectKey("bucket", "key")).payload()));
    }

    @Test
    void getObjectThrowsPolicyDeniedWhenPolicyRejectsRequest() {
        InMemoryObjectStorageAdapter delegate = new InMemoryObjectStorageAdapter();
        delegate.putObject(
            ProviderConnectionConfig.of(ProviderType.GCS, null, null, null),
            new PutObjectCommand(new ObjectKey("bucket", "key"), "hello".getBytes(), "text/plain", Map.of())
        );

        CapabilityEnforcingObjectStorageAdapter adapter = new CapabilityEnforcingObjectStorageAdapter(
            delegate,
            new ProviderCapabilityRegistry(),
            new CountingPolicyEnforcer(true)
        );

        ProviderConnectionConfig config = ProviderConnectionConfig.of(ProviderType.GCS, null, null, null);
        assertThrows(PolicyDeniedException.class, () -> adapter.getObject(config, new ObjectKey("bucket", "key")));
    }

    @Test
    void putObjectThrowsUnsupportedCapabilityWhenProviderTypeIsUnknown() {
        CapabilityEnforcingObjectStorageAdapter adapter = new CapabilityEnforcingObjectStorageAdapter(
            new InMemoryObjectStorageAdapter(),
            new ProviderCapabilityRegistry(),
            new CountingPolicyEnforcer(false)
        );

        ProviderConnectionConfig unknownProviderConfig = new ProviderConnectionConfig(null, null, null, null, Map.of());
        PutObjectCommand command = new PutObjectCommand(new ObjectKey("bucket", "key"), "hello".getBytes(), "text/plain", Map.of());

        assertThrows(UnsupportedCapabilityException.class, () -> adapter.putObject(unknownProviderConfig, command));
    }

    private static final class CountingPolicyEnforcer implements PolicyEnforcer {
        private final AtomicInteger assertions = new AtomicInteger(0);
        private final boolean deny;

        private CountingPolicyEnforcer(boolean deny) {
            this.deny = deny;
        }

        @Override
        public void assertAllowed(ProviderConnectionConfig connectionConfig, ProviderCapability capability, ObjectKey objectKey) {
            assertions.incrementAndGet();
            if (deny) {
                throw new PolicyDeniedException("Denied by test policy");
            }
        }

        private int assertions() {
            return assertions.get();
        }
    }

    private static final class InMemoryObjectStorageAdapter implements ObjectStorageAdapter {
        private final Map<String, StoredObject> storage = new java.util.HashMap<>();

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