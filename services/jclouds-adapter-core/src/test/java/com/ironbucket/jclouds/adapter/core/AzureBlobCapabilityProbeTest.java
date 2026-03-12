package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AzureBlobCapabilityProbeTest {

    @Test
    void probeReturnsDegradedWhenCredentialsAreMissing() {
        AtomicBoolean contextCalled = new AtomicBoolean(false);
        BlobStoreContextProvider neverCalledProvider = connectionConfig -> {
            contextCalled.set(true);
            throw new IllegalStateException("context provider should not be called without credentials");
        };

        AzureBlobCapabilityProbe probe = new AzureBlobCapabilityProbe(new ProviderCapabilityRegistry(), neverCalledProvider);
        ProviderConnectionConfig config = new ProviderConnectionConfig(ProviderType.AZURE_BLOB, null, null, null, Map.of());

        CapabilityProbeResult result = probe.probe(config);

        assertEquals(CapabilityProbeStatus.DEGRADED, result.status());
        assertTrue(result.capabilities().contains(ProviderCapability.OBJECT_TAGGING));
        assertFalse(contextCalled.get());
    }

    @Test
    void probeReturnsUnavailableForNonAzureRequests() {
        AzureBlobCapabilityProbe probe = new AzureBlobCapabilityProbe(
            new ProviderCapabilityRegistry(),
            connectionConfig -> { throw new IllegalStateException("not used"); }
        );

        ProviderConnectionConfig config = ProviderConnectionConfig.of(ProviderType.GCS, null, "id", "secret");
        CapabilityProbeResult result = probe.probe(config);

        assertEquals(CapabilityProbeStatus.UNAVAILABLE, result.status());
        assertTrue(result.capabilities().isEmpty());
    }

    @Test
    void probeReturnsUnavailableWhenContextCreationFails() {
        AzureBlobCapabilityProbe probe = new AzureBlobCapabilityProbe(
            new ProviderCapabilityRegistry(),
            connectionConfig -> { throw new RuntimeException("auth failed"); }
        );

        ProviderConnectionConfig config = ProviderConnectionConfig.of(ProviderType.AZURE_BLOB, null, "access", "secret");
        CapabilityProbeResult result = probe.probe(config);

        assertEquals(CapabilityProbeStatus.UNAVAILABLE, result.status());
        assertTrue(result.details().contains("auth failed"));
    }
}