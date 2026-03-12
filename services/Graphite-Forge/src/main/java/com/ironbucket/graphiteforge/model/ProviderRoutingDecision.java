package com.ironbucket.graphiteforge.model;

public record ProviderRoutingDecision(
    String tenantId,
    String bucketName,
    String requiredCapability,
    String selectedProvider,
    String reason
) {
}