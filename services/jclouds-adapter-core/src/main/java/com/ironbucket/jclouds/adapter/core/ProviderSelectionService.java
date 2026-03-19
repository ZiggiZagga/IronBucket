package com.ironbucket.jclouds.adapter.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProviderSelectionService {
    private final ProviderCapabilityRegistry capabilityRegistry;
    private final TenantBucketProviderRegistry tenantRegistry;
    private final MeterRegistry meterRegistry;
    private final Timer selectionLatencyTimer;
    private final Counter selectionSuccessCounter;
    private final Counter selectionNoMatchCounter;
    private final Counter selectionFallbackCounter;
    private final Counter selectionDeniedByPolicyCounter;

    public ProviderSelectionService(ProviderCapabilityRegistry capabilityRegistry) {
        this(capabilityRegistry, new TenantBucketProviderRegistry(), null);
    }

    public ProviderSelectionService(
        ProviderCapabilityRegistry capabilityRegistry,
        TenantBucketProviderRegistry tenantRegistry
    ) {
        this(capabilityRegistry, tenantRegistry, null);
    }

    public ProviderSelectionService(
        ProviderCapabilityRegistry capabilityRegistry,
        TenantBucketProviderRegistry tenantRegistry,
        MeterRegistry meterRegistry
    ) {
        this.capabilityRegistry = capabilityRegistry;
        this.tenantRegistry = tenantRegistry;
        this.meterRegistry = meterRegistry == null ? new SimpleMeterRegistry() : meterRegistry;
        this.selectionLatencyTimer = this.meterRegistry.timer("ironbucket.provider.selection.latency");
        this.selectionSuccessCounter = this.meterRegistry.counter("ironbucket.provider.selection.result", "outcome", "selected");
        this.selectionNoMatchCounter = this.meterRegistry.counter("ironbucket.provider.selection.result", "outcome", "no_match");
        this.selectionFallbackCounter = this.meterRegistry.counter("ironbucket.provider.selection.result", "outcome", "fallback");
        this.selectionDeniedByPolicyCounter = this.meterRegistry.counter("ironbucket.provider.selection.result", "outcome", "policy_denied");
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
        return selectProvider(tenantId, bucketName, requiredCapabilities, ProviderRoutingPolicy.unrestricted());
    }

    public Optional<ProviderType> selectProvider(
        String tenantId,
        String bucketName,
        Set<ProviderCapability> requiredCapabilities,
        ProviderRoutingPolicy policy
    ) {
        ProviderSelectionDecision decision = selectProviderDecision(tenantId, bucketName, requiredCapabilities, policy);
        return decision.hasSelection() ? Optional.of(decision.selectedProvider()) : Optional.empty();
    }

    public ProviderSelectionDecision selectProviderDecision(
        String tenantId,
        String bucketName,
        Set<ProviderCapability> requiredCapabilities,
        ProviderRoutingPolicy policy
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Set<ProviderCapability> required = normalizeRequiredCapabilities(requiredCapabilities);
            ProviderRoutingPolicy normalizedPolicy = policy == null ? ProviderRoutingPolicy.unrestricted() : policy;

            List<ProviderType> attempts = prioritizedProviders(tenantId, bucketName).stream().toList();
            int deniedCount = 0;

            for (ProviderType provider : attempts) {
                if (normalizedPolicy.isExplicitlyDenied(provider)) {
                    deniedCount++;
                    selectionDeniedByPolicyCounter.increment();
                    continue;
                }
                if (!normalizedPolicy.isAllowed(provider)) {
                    deniedCount++;
                    selectionDeniedByPolicyCounter.increment();
                    continue;
                }
                boolean supportsAll = required.stream().allMatch(capability -> capabilityRegistry.supports(provider, capability));
                if (supportsAll) {
                    selectionSuccessCounter.increment();
                    if (!attempts.isEmpty() && provider != attempts.getFirst()) {
                        selectionFallbackCounter.increment();
                    }
                    return new ProviderSelectionDecision(
                        tenantId,
                        bucketName,
                        attempts,
                        provider,
                        deniedCount > 0 ? "capability-aware-provider-selection-with-policy-filter" : "capability-aware-provider-selection"
                    );
                }
            }

            selectionNoMatchCounter.increment();
            return new ProviderSelectionDecision(
                tenantId,
                bucketName,
                attempts,
                null,
                "no-provider-satisfies-capabilities-or-policy"
            );
        } finally {
            sample.stop(selectionLatencyTimer);
        }
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

    public Set<ProviderCapability> capabilitiesOf(ProviderType providerType) {
        return capabilityRegistry.profileOf(providerType)
            .map(ProviderCapabilityProfile::supportedCapabilities)
            .orElseGet(() -> EnumSet.noneOf(ProviderCapability.class));
    }
}
