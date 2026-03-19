package com.ironbucket.jclouds.adapter.core;

import java.util.List;

public record ProviderSelectionDecision(
    String tenantId,
    String bucketName,
    List<ProviderType> attemptedProviders,
    ProviderType selectedProvider,
    String reason
) {
    public boolean hasSelection() {
        return selectedProvider != null;
    }
}