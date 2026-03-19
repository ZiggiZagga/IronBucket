package com.ironbucket.jclouds.adapter.core;

public final class NoOpProviderOperationAuditor implements ProviderOperationAuditor {

    @Override
    public void record(
        String tenantId,
        ProviderType providerType,
        ProviderCapability capability,
        ObjectKey objectKey,
        String outcome
    ) {
        // Default no-op implementation keeps adapter-core dependency-free.
    }
}