package com.ironbucket.jclouds.adapter.core;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProviderSelectionService {
    private final ProviderCapabilityRegistry capabilityRegistry;
    private final TenantBucketProviderRegistry tenantRegistry;

    public ProviderSelectionService(ProviderCapabilityRegistry capabilityRegistry) {
        this(capabilityRegistry, new TenantBucketProviderRegistry());
    }

    public ProviderSelectionService(
        ProviderCapabilityRegistry capabilityRegistry,
        TenantBucketProviderRegistry tenantRegistry
    ) {
        this.capabilityRegistry = capabilityRegistry;
        this.tenantRegistry = tenantRegistry;
    }

    public Set<ProviderType> providersSupporting(Set<ProviderCapability> requiredCapabilities) {
        Set<ProviderCapability> required = normalizeRequiredCapabilities(requiredCapabilities);
        return EnumSet.allOf(ProviderType.class)
            .stream()
            .filter(provider -> required.stream().allMatch(capability -> capabilityRegistry.supports(provider, capability)))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProviderType.class)));
    }

    public Optional<ProviderType> selectProvider(
        String tenantId,
        String bucketName,
        Set<ProviderCapability> requiredCapabilities
    ) {
        Set<ProviderCapability> required = normalizeRequiredCapabilities(requiredCapabilities);

        for (ProviderType provider : prioritizedProviders(tenantId, bucketName)) {
            boolean supportsAll = required.stream().allMatch(capability -> capabilityRegistry.supports(provider, capability));
            if (supportsAll) {
                return Optional.of(provider);
            }
        }

        return Optional.empty();
    }

    private LinkedHashSet<ProviderType> prioritizedProviders(String tenantId, String bucketName) {
        LinkedHashSet<ProviderType> providers = new LinkedHashSet<>();
        tenantRegistry.bucketProvider(tenantId, bucketName).ifPresent(providers::add);
        tenantRegistry.tenantDefaultProvider(tenantId).ifPresent(providers::add);
        providers.addAll(EnumSet.allOf(ProviderType.class));
        return providers;
    }

    private static Set<ProviderCapability> normalizeRequiredCapabilities(Set<ProviderCapability> requiredCapabilities) {
        if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
            return EnumSet.noneOf(ProviderCapability.class);
        }
        return EnumSet.copyOf(requiredCapabilities);
    }
}
