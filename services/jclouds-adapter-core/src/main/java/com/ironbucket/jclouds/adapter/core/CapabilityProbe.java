package com.ironbucket.jclouds.adapter.core;

public interface CapabilityProbe {
    CapabilityProbeResult probe(ProviderConnectionConfig connectionConfig);
}
