package com.ironbucket.jclouds.adapter.core;

import org.jclouds.blobstore.BlobStoreContext;

import java.util.Set;

public final class GcsCapabilityProbe implements CapabilityProbe {
    private final ProviderCapabilityRegistry capabilityRegistry;
    private final BlobStoreContextProvider contextProvider;

    public GcsCapabilityProbe(ProviderCapabilityRegistry capabilityRegistry, BlobStoreContextProvider contextProvider) {
        this.capabilityRegistry = capabilityRegistry;
        this.contextProvider = contextProvider;
    }

    @Override
    public CapabilityProbeResult probe(ProviderConnectionConfig connectionConfig) {
        if (connectionConfig.providerType() != ProviderType.GCS) {
            return new CapabilityProbeResult(
                connectionConfig.providerType(),
                CapabilityProbeStatus.UNAVAILABLE,
                Set.of(),
                "GcsCapabilityProbe supports only GCS"
            );
        }

        Set<ProviderCapability> capabilities = capabilityRegistry.profileOf(ProviderType.GCS)
            .map(ProviderCapabilityProfile::supportedCapabilities)
            .orElse(Set.of());

        if (!JcloudsBlobStoreContextProvider.hasResolvableCredentials(connectionConfig)) {
            return new CapabilityProbeResult(
                ProviderType.GCS,
                CapabilityProbeStatus.DEGRADED,
                capabilities,
                "Credentials not provided; returned static capability profile only"
            );
        }

        try (BlobStoreContext ignored = contextProvider.openContext(connectionConfig)) {
            return new CapabilityProbeResult(
                ProviderType.GCS,
                CapabilityProbeStatus.SUPPORTED,
                capabilities,
                "GCS context opened successfully"
            );
        } catch (RuntimeException runtimeException) {
            return new CapabilityProbeResult(
                ProviderType.GCS,
                CapabilityProbeStatus.UNAVAILABLE,
                capabilities,
                "GCS context probe failed: " + runtimeException.getMessage()
            );
        }
    }
}