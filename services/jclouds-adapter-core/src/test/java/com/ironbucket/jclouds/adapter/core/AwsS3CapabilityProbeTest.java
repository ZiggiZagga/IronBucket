package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwsS3CapabilityProbeTest {

    @Test
    void probeReturnsDegradedWhenCredentialsAreMissing() {
        AtomicBoolean contextCalled = new AtomicBoolean(false);
        BlobStoreContextProvider neverCalledProvider = connectionConfig -> {
            contextCalled.set(true);
            throw new IllegalStateException("context provider should not be called without credentials");
        };

        AwsS3CapabilityProbe probe = new AwsS3CapabilityProbe(new ProviderCapabilityRegistry(), neverCalledProvider);
        ProviderConnectionConfig config = new ProviderConnectionConfig(ProviderType.AWS_S3, null, null, null, Map.of());

        CapabilityProbeResult result = probe.probe(config);

        assertEquals(CapabilityProbeStatus.DEGRADED, result.status());
        assertTrue(result.capabilities().contains(ProviderCapability.MULTIPART_UPLOAD));
        assertFalse(contextCalled.get());
    }

    @Test
    void probeReturnsUnavailableForNonAwsProbeRequests() {
        AwsS3CapabilityProbe probe = new AwsS3CapabilityProbe(
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
        AwsS3CapabilityProbe probe = new AwsS3CapabilityProbe(
            new ProviderCapabilityRegistry(),
            connectionConfig -> { throw new RuntimeException("auth failed"); }
        );

        ProviderConnectionConfig config = ProviderConnectionConfig.of(ProviderType.AWS_S3, null, "access", "secret");
        CapabilityProbeResult result = probe.probe(config);

        assertEquals(CapabilityProbeStatus.UNAVAILABLE, result.status());
        assertTrue(result.details().contains("auth failed"));
    }
}
