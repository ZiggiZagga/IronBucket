package com.ironbucket.jclouds.adapter.core;

public final class CapabilityEnforcingObjectStorageAdapter implements ObjectStorageAdapter {
    private final ObjectStorageAdapter delegate;
    private final ProviderCapabilityRegistry capabilityRegistry;
    private final PolicyEnforcer policyEnforcer;

    public CapabilityEnforcingObjectStorageAdapter(
        ObjectStorageAdapter delegate,
        ProviderCapabilityRegistry capabilityRegistry,
        PolicyEnforcer policyEnforcer
    ) {
        this.delegate = delegate;
        this.capabilityRegistry = capabilityRegistry;
        this.policyEnforcer = policyEnforcer;
    }

    @Override
    public void putObject(ProviderConnectionConfig connectionConfig, PutObjectCommand command) {
        assertSupported(connectionConfig, ProviderCapability.OBJECT_WRITE, command.objectKey());
        delegate.putObject(connectionConfig, command);
    }

    @Override
    public StoredObject getObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey) {
        assertSupported(connectionConfig, ProviderCapability.OBJECT_READ, objectKey);
        return delegate.getObject(connectionConfig, objectKey);
    }

    @Override
    public void deleteObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey) {
        assertSupported(connectionConfig, ProviderCapability.OBJECT_DELETE, objectKey);
        delegate.deleteObject(connectionConfig, objectKey);
    }

    private void assertSupported(ProviderConnectionConfig connectionConfig, ProviderCapability capability, ObjectKey objectKey) {
        ProviderType providerType = connectionConfig.providerType();
        if (!capabilityRegistry.supports(providerType, capability)) {
            throw new UnsupportedCapabilityException(
                "Provider " + providerType + " does not support capability " + capability
            );
        }

        policyEnforcer.assertAllowed(connectionConfig, capability, objectKey);
    }
}