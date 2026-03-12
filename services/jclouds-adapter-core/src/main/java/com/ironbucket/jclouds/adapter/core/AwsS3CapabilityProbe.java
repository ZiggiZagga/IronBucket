package com.ironbucket.jclouds.adapter.core;

import org.jclouds.blobstore.BlobStoreContext;

import java.util.Set;

public final class AwsS3CapabilityProbe implements CapabilityProbe {
    private final ProviderCapabilityRegistry capabilityRegistry;
    private final BlobStoreContextProvider contextProvider;

    public AwsS3CapabilityProbe(ProviderCapabilityRegistry capabilityRegistry, BlobStoreContextProvider contextProvider) {
        this.capabilityRegistry = capabilityRegistry;
        this.contextProvider = contextProvider;
    }

    @Override
    public CapabilityProbeResult probe(ProviderConnectionConfig connectionConfig) {
        if (connectionConfig.providerType() != ProviderType.AWS_S3) {
            return new CapabilityProbeResult(
                connectionConfig.providerType(),
                CapabilityProbeStatus.UNAVAILABLE,
                Set.of(),
                "AwsS3CapabilityProbe supports only AWS_S3"
            );
        }

        Set<ProviderCapability> capabilities = capabilityRegistry.profileOf(ProviderType.AWS_S3)
            .map(ProviderCapabilityProfile::supportedCapabilities)
            .orElse(Set.of());

        if (!connectionConfig.hasCredentials()) {
            return new CapabilityProbeResult(
                ProviderType.AWS_S3,
                CapabilityProbeStatus.DEGRADED,
                capabilities,
                "Credentials not provided; returned static capability profile only"
            );
        }

        try (BlobStoreContext ignored = contextProvider.openContext(connectionConfig)) {
            return new CapabilityProbeResult(
                ProviderType.AWS_S3,
                CapabilityProbeStatus.SUPPORTED,
                capabilities,
                "AWS S3 context opened successfully"
            );
        } catch (RuntimeException runtimeException) {
            return new CapabilityProbeResult(
                ProviderType.AWS_S3,
                CapabilityProbeStatus.UNAVAILABLE,
                capabilities,
                "AWS S3 context probe failed: " + runtimeException.getMessage()
            );
        }
    }
}
