package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AwsS3CapabilityProbeIntegrationTest {

    @Test
    void awsS3ProbeOpensContextWhenIntegrationVariablesAreProvided() {
        boolean integrationEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("IRONBUCKET_AWS_S3_INTEGRATION", "false"));
        assumeTrue(integrationEnabled, "Enable with IRONBUCKET_AWS_S3_INTEGRATION=true");

        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        assumeTrue(accessKey != null && !accessKey.isBlank(), "AWS_ACCESS_KEY_ID is required");
        assumeTrue(secretKey != null && !secretKey.isBlank(), "AWS_SECRET_ACCESS_KEY is required");

        String endpoint = System.getenv().getOrDefault("AWS_S3_ENDPOINT", "");
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

        Map<String, String> properties = new HashMap<>();
        properties.put("jclouds.regions", region);

        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.AWS_S3,
            endpoint.isBlank() ? null : endpoint,
            accessKey,
            secretKey,
            properties
        );

        AwsS3CapabilityProbe probe = new AwsS3CapabilityProbe(
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
