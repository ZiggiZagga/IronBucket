package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GcsCapabilityProbeIntegrationTest {

    @Test
    void gcsProbeOpensContextWhenIntegrationVariablesAreProvided() {
        boolean integrationEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("IRONBUCKET_GCS_INTEGRATION", "false"));
        assumeTrue(integrationEnabled, "Enable with IRONBUCKET_GCS_INTEGRATION=true");

        String identity = System.getenv("IRONBUCKET_GCS_IDENTITY");
        String credential = System.getenv("IRONBUCKET_GCS_CREDENTIAL");
        assumeTrue(identity != null && !identity.isBlank(), "IRONBUCKET_GCS_IDENTITY is required");
        assumeTrue(credential != null && !credential.isBlank(), "IRONBUCKET_GCS_CREDENTIAL is required");

        String endpoint = System.getenv().getOrDefault("IRONBUCKET_GCS_ENDPOINT", "");
        String project = System.getenv().getOrDefault("IRONBUCKET_GCS_PROJECT", "");

        Map<String, String> properties = new HashMap<>();
        if (!project.isBlank()) {
            properties.put("jclouds.google.cloud.project-id", project);
        }

        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.GCS,
            endpoint.isBlank() ? null : endpoint,
            identity,
            credential,
            properties
        );

        GcsCapabilityProbe probe = new GcsCapabilityProbe(
            new ProviderCapabilityRegistry(),
            new JcloudsBlobStoreContextProvider()
        );

        CapabilityProbeResult result = probe.probe(config);

        assertEquals(CapabilityProbeStatus.SUPPORTED, result.status(), result.details());
        assertTrue(result.capabilities().contains(ProviderCapability.OBJECT_READ));
        assertTrue(result.capabilities().contains(ProviderCapability.MULTIPART_UPLOAD));
        assertTrue(result.capabilities().contains(ProviderCapability.VERSIONING));
    }
}
