package com.ironbucket.jclouds.adapter.core;

import org.jclouds.blobstore.BlobStoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProviderCrudParityIntegrationTest {

    private final BlobStoreContextProvider contextProvider = new JcloudsBlobStoreContextProvider();
    private final ObjectStorageAdapter adapter = new JcloudsObjectStorageAdapter(contextProvider);

    private ProviderConnectionConfig cleanupConfig;
    private String cleanupBucket;

    @AfterEach
    void cleanup() {
        if (cleanupConfig == null || cleanupBucket == null) {
            return;
        }

        try (BlobStoreContext context = contextProvider.openContext(cleanupConfig)) {
            context.getBlobStore().deleteContainer(cleanupBucket);
        } catch (RuntimeException ignored) {
            // best-effort cleanup for ephemeral integration buckets
        }
    }

    @Test
    void awsS3CrudRoundtripWhenCredentialsAvailable() {
        boolean enabled = Boolean.parseBoolean(System.getenv().getOrDefault("IRONBUCKET_AWS_S3_INTEGRATION", "false"));
        assumeTrue(enabled, "Enable with IRONBUCKET_AWS_S3_INTEGRATION=true");

        String identity = System.getenv("AWS_ACCESS_KEY_ID");
        String credential = System.getenv("AWS_SECRET_ACCESS_KEY");
        assumeTrue(identity != null && !identity.isBlank(), "AWS_ACCESS_KEY_ID is required");
        assumeTrue(credential != null && !credential.isBlank(), "AWS_SECRET_ACCESS_KEY is required");
        assumeFalse("dummy".equalsIgnoreCase(identity) || "dummy".equalsIgnoreCase(credential),
            "placeholder AWS credentials detected; skipping integration run");

        Map<String, String> properties = new HashMap<>();
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        properties.put("jclouds.regions", region);

        String endpoint = System.getenv().getOrDefault("AWS_S3_ENDPOINT", "");
        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.AWS_S3,
            endpoint.isBlank() ? null : endpoint,
            identity,
            credential,
            properties
        );

        runCrudRoundtrip(config, bucketName("ib-aws-crud-it"), "provider", "AWS_S3");
    }

    @Test
    void gcsCrudRoundtripWhenCredentialsAvailable() {
        boolean enabled = Boolean.parseBoolean(System.getenv().getOrDefault("IRONBUCKET_GCS_INTEGRATION", "false"));
        assumeTrue(enabled, "Enable with IRONBUCKET_GCS_INTEGRATION=true");

        String identity = System.getenv("IRONBUCKET_GCS_IDENTITY");
        String credential = System.getenv("IRONBUCKET_GCS_CREDENTIAL");
        assumeTrue(identity != null && !identity.isBlank(), "IRONBUCKET_GCS_IDENTITY is required");
        assumeTrue(credential != null && !credential.isBlank(), "IRONBUCKET_GCS_CREDENTIAL is required");
        assumeFalse("dummy".equalsIgnoreCase(identity) || "dummy".equalsIgnoreCase(credential),
            "placeholder GCS credentials detected; skipping integration run");

        Map<String, String> properties = new HashMap<>();
        String project = System.getenv().getOrDefault("IRONBUCKET_GCS_PROJECT", "");
        if (!project.isBlank()) {
            properties.put("jclouds.google.cloud.project-id", project);
        }

        String endpoint = System.getenv().getOrDefault("IRONBUCKET_GCS_ENDPOINT", "");
        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.GCS,
            endpoint.isBlank() ? null : endpoint,
            identity,
            credential,
            properties
        );

        runCrudRoundtrip(config, bucketName("ib-gcs-crud-it"), "provider", "GCS");
    }

    @Test
    void azureBlobCrudRoundtripWhenCredentialsAvailable() {
        boolean enabled = Boolean.parseBoolean(System.getenv().getOrDefault("IRONBUCKET_AZURE_BLOB_INTEGRATION", "false"));
        assumeTrue(enabled, "Enable with IRONBUCKET_AZURE_BLOB_INTEGRATION=true");

        String identity = System.getenv("AZURE_STORAGE_ACCOUNT");
        String credential = System.getenv("AZURE_STORAGE_KEY");
        assumeTrue(identity != null && !identity.isBlank(), "AZURE_STORAGE_ACCOUNT is required");
        assumeTrue(credential != null && !credential.isBlank(), "AZURE_STORAGE_KEY is required");
        assumeFalse("dummy".equalsIgnoreCase(identity) || "dummy".equalsIgnoreCase(credential),
            "placeholder Azure credentials detected; skipping integration run");

        String endpoint = System.getenv().getOrDefault("IRONBUCKET_AZURE_BLOB_ENDPOINT", "");
        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.AZURE_BLOB,
            endpoint.isBlank() ? null : endpoint,
            identity,
            credential,
            Map.of()
        );

        runCrudRoundtrip(config, bucketName("ibazcrudit"), "provider", "AZURE_BLOB");
    }

    private void runCrudRoundtrip(ProviderConnectionConfig config, String bucket, String metadataKey, String metadataValue) {
        cleanupConfig = config;
        cleanupBucket = bucket;

        ObjectKey key = new ObjectKey(bucket, "tenant-a/path/it-object-" + UUID.randomUUID() + ".txt");
        byte[] payload = ("parity-" + metadataValue + "-" + Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8);

        createBucket(config, bucket);

        adapter.putObject(config, new PutObjectCommand(key, payload, "text/plain", Map.of(metadataKey, metadataValue)));

        StoredObject fetched = adapter.getObject(config, key);
        assertArrayEquals(payload, fetched.payload());
        assertEquals(metadataValue, fetched.metadata().get(metadataKey));

        adapter.deleteObject(config, key);

        try (BlobStoreContext context = contextProvider.openContext(config)) {
            assertFalse(context.getBlobStore().blobExists(bucket, key.key()));
        }
    }

    private void createBucket(ProviderConnectionConfig config, String bucket) {
        try (BlobStoreContext context = contextProvider.openContext(config)) {
            context.getBlobStore().createContainerInLocation(null, bucket);
        }
    }

    private static String bucketName(String prefix) {
        String random = UUID.randomUUID().toString().replace("-", "").toLowerCase();
        String candidate = (prefix + random).toLowerCase();
        return candidate.length() > 63 ? candidate.substring(0, 63) : candidate;
    }
}
