package com.ironbucket.jclouds.adapter.core;

public final class AllowAllPolicyEnforcer implements PolicyEnforcer {

    @Override
    public void assertAllowed(ProviderConnectionConfig connectionConfig, ProviderCapability capability, ObjectKey objectKey) {
    }
}