package com.ironbucket.jclouds.adapter.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class ProviderCapabilityProfile {
    private final ProviderType providerType;
    private final Set<ProviderCapability> supportedCapabilities;

    public ProviderCapabilityProfile(ProviderType providerType, Set<ProviderCapability> supportedCapabilities) {
        this.providerType = providerType;
        this.supportedCapabilities = Collections.unmodifiableSet(EnumSet.copyOf(supportedCapabilities));
    }

    public ProviderType providerType() {
        return providerType;
    }

    public Set<ProviderCapability> supportedCapabilities() {
        return supportedCapabilities;
    }

    public boolean supports(ProviderCapability capability) {
        return supportedCapabilities.contains(capability);
    }
}
