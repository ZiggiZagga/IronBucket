package com.ironbucket.jclouds.adapter.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record CapabilityProbeResult(
    ProviderType providerType,
    CapabilityProbeStatus status,
    Set<ProviderCapability> capabilities,
    String details
) {
    public CapabilityProbeResult {
        if (capabilities == null || capabilities.isEmpty()) {
            capabilities = Set.of();
        } else {
            capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        }
    }
}
