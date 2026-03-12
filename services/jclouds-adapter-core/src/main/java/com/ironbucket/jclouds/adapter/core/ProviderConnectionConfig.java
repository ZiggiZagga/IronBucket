package com.ironbucket.jclouds.adapter.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record ProviderConnectionConfig(
    ProviderType providerType,
    String endpoint,
    String identity,
    String credential,
    Map<String, String> properties
) {
    public ProviderConnectionConfig {
        properties = properties == null
            ? Map.of()
            : Collections.unmodifiableMap(new HashMap<>(properties));
    }

    public static ProviderConnectionConfig of(ProviderType providerType, String endpoint, String identity, String credential) {
        return new ProviderConnectionConfig(providerType, endpoint, identity, credential, Map.of());
    }

    public boolean hasCredentials() {
        return notBlank(identity) && notBlank(credential);
    }

    public boolean hasEndpoint() {
        return notBlank(endpoint);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
