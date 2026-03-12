package com.ironbucket.jclouds.adapter.core;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProviderSelectionService {
    private final ProviderCapabilityRegistry capabilityRegistry;

    public ProviderSelectionService(ProviderCapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = capabilityRegistry;
    }

    public Set<ProviderType> providersSupporting(Set<ProviderCapability> requiredCapabilities) {
        Set<ProviderCapability> required = EnumSet.copyOf(requiredCapabilities);
        return EnumSet.allOf(ProviderType.class)
            .stream()
            .filter(provider -> required.stream().allMatch(capability -> capabilityRegistry.supports(provider, capability)))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProviderType.class)));
    }
}
