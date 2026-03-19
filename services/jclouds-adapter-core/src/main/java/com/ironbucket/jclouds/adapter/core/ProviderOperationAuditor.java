package com.ironbucket.jclouds.adapter.core;

public interface ProviderOperationAuditor {
    void record(
        String tenantId,
        ProviderType providerType,
        ProviderCapability capability,
        ObjectKey objectKey,
        String outcome
    );
}