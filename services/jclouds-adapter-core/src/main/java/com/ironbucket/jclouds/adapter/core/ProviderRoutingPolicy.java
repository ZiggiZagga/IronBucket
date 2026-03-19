package com.ironbucket.jclouds.adapter.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record ProviderRoutingPolicy(
    Set<ProviderType> allowedProviders,
    Set<ProviderType> deniedProviders
) {
    public ProviderRoutingPolicy {
        allowedProviders = normalizeProviders(allowedProviders);
        deniedProviders = normalizeProviders(deniedProviders);
    }

    public static ProviderRoutingPolicy unrestricted() {
        return new ProviderRoutingPolicy(EnumSet.noneOf(ProviderType.class), EnumSet.noneOf(ProviderType.class));
    }

    public boolean isExplicitlyDenied(ProviderType providerType) {
        return deniedProviders.contains(providerType);
    }

    public boolean isAllowed(ProviderType providerType) {
        return allowedProviders.isEmpty() || allowedProviders.contains(providerType);
    }

    private static Set<ProviderType> normalizeProviders(Set<ProviderType> providers) {
        if (providers == null || providers.isEmpty()) {
            return Collections.unmodifiableSet(EnumSet.noneOf(ProviderType.class));
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(providers));
    }
}