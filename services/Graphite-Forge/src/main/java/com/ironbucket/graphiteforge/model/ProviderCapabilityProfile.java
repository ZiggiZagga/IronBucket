package com.ironbucket.graphiteforge.model;

import java.util.List;

public record ProviderCapabilityProfile(
    String provider,
    List<String> capabilities
) {
}