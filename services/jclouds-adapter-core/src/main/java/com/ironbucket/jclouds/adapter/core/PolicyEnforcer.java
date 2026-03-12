package com.ironbucket.jclouds.adapter.core;

public interface PolicyEnforcer {
    void assertAllowed(ProviderConnectionConfig connectionConfig, ProviderCapability capability, ObjectKey objectKey);
}