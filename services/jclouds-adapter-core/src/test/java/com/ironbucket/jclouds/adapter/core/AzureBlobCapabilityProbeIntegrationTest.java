package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AzureBlobCapabilityProbeIntegrationTest {

    @Test
    void azureProbeOpensContextWhenIntegrationVariablesAreProvided() {
        boolean integrationEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("IRONBUCKET_AZURE_BLOB_INTEGRATION", "false"));
        assumeTrue(integrationEnabled, "Enable with IRONBUCKET_AZURE_BLOB_INTEGRATION=true");

        String identity = System.getenv("AZURE_STORAGE_ACCOUNT");
        String credential = System.getenv("AZURE_STORAGE_KEY");
        assumeTrue(identity != null && !identity.isBlank(), "AZURE_STORAGE_ACCOUNT is required");
        assumeTrue(credential != null && !credential.isBlank(), "AZURE_STORAGE_KEY is required");

        String endpoint = System.getenv().getOrDefault("IRONBUCKET_AZURE_BLOB_ENDPOINT", "");

        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.AZURE_BLOB,
            endpoint.isBlank() ? null : endpoint,
            identity,
            credential,
            Map.of()
        );

        AzureBlobCapabilityProbe probe = new AzureBlobCapabilityProbe(
            new ProviderCapabilityRegistry(),
            new JcloudsBlobStoreContextProvider()
        );

        CapabilityProbeResult result = probe.probe(config);

        assertEquals(CapabilityProbeStatus.SUPPORTED, result.status(), result.details());
        assertTrue(result.capabilities().contains(ProviderCapability.OBJECT_READ));
        assertTrue(result.capabilities().contains(ProviderCapability.MULTIPART_UPLOAD));
        assertTrue(result.capabilities().contains(ProviderCapability.PRESIGNED_URLS));
    }
}
