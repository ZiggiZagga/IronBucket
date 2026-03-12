package com.ironbucket.jclouds.adapter.core;

import org.jclouds.blobstore.BlobStoreContext;

import java.util.Set;

public final class AzureBlobCapabilityProbe implements CapabilityProbe {
    private final ProviderCapabilityRegistry capabilityRegistry;
    private final BlobStoreContextProvider contextProvider;

    public AzureBlobCapabilityProbe(ProviderCapabilityRegistry capabilityRegistry, BlobStoreContextProvider contextProvider) {
        this.capabilityRegistry = capabilityRegistry;
        this.contextProvider = contextProvider;
    }

    @Override
    public CapabilityProbeResult probe(ProviderConnectionConfig connectionConfig) {
        if (connectionConfig.providerType() != ProviderType.AZURE_BLOB) {
            return new CapabilityProbeResult(
                connectionConfig.providerType(),
                CapabilityProbeStatus.UNAVAILABLE,
                Set.of(),
                "AzureBlobCapabilityProbe supports only AZURE_BLOB"
            );
        }

        Set<ProviderCapability> capabilities = capabilityRegistry.profileOf(ProviderType.AZURE_BLOB)
            .map(ProviderCapabilityProfile::supportedCapabilities)
            .orElse(Set.of());

        if (!connectionConfig.hasCredentials()) {
            return new CapabilityProbeResult(
                ProviderType.AZURE_BLOB,
                CapabilityProbeStatus.DEGRADED,
                capabilities,
                "Credentials not provided; returned static capability profile only"
            );
        }

        try (BlobStoreContext ignored = contextProvider.openContext(connectionConfig)) {
            return new CapabilityProbeResult(
                ProviderType.AZURE_BLOB,
                CapabilityProbeStatus.SUPPORTED,
                capabilities,
                "Azure Blob context opened successfully"
            );
        } catch (RuntimeException runtimeException) {
            return new CapabilityProbeResult(
                ProviderType.AZURE_BLOB,
                CapabilityProbeStatus.UNAVAILABLE,
                capabilities,
                "Azure Blob context probe failed: " + runtimeException.getMessage()
            );
        }
    }
}