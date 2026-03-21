package com.ironbucket.jclouds.adapter.core;

import org.jclouds.blobstore.BlobStoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinioObjectCrudIT {

    private final BlobStoreContextProvider contextProvider = new JcloudsBlobStoreContextProvider();
    private final ObjectStorageAdapter adapter = new JcloudsObjectStorageAdapter(contextProvider);
    private final ProviderConnectionConfig config = new ProviderConnectionConfig(
        ProviderType.AWS_S3,
        propertyOrDefault("ironbucket.minio.endpoint", "https://127.0.0.1:9000"),
        propertyOrDefault("ironbucket.minio.accessKey", "minioadmin"),
        propertyOrDefault("ironbucket.minio.secretKey", "minioadmin"),
        Map.of(
            "jclouds.s3.virtual-host-buckets", "false",
            "jclouds.regions", propertyOrDefault("ironbucket.minio.region", "us-east-1")
        )
    );

    private String testBucket;

    @AfterEach
    void cleanupContainer() {
        if (testBucket == null) {
            return;
        }

        try (BlobStoreContext context = contextProvider.openContext(config)) {
            context.getBlobStore().deleteContainer(testBucket);
        }
    }

    @Test
    void putGetDeleteRoundTripAgainstMinio() {
        testBucket = "ib-minio-it-" + UUID.randomUUID().toString().replace("-", "");
        ObjectKey objectKey = new ObjectKey(testBucket, "tenant-a/path/example.txt");
        byte[] payload = "phase4-minio-it".getBytes(StandardCharsets.UTF_8);

        createBucket(testBucket);

        adapter.putObject(
            config,
            new PutObjectCommand(objectKey, payload, "text/plain", Map.of("tenant", "tenant-a"))
        );

        StoredObject fetchedObject = adapter.getObject(config, objectKey);
        assertArrayEquals(payload, fetchedObject.payload());
        assertEquals("tenant-a", fetchedObject.metadata().get("tenant"));

        adapter.deleteObject(config, objectKey);

        try (BlobStoreContext context = contextProvider.openContext(config)) {
            assertFalse(context.getBlobStore().blobExists(testBucket, objectKey.key()));
        }
    }

    @Test
    void putObjectOverwriteKeepsLatestPayloadAndMetadata() {
        testBucket = "ib-minio-it-" + UUID.randomUUID().toString().replace("-", "");
        ObjectKey objectKey = new ObjectKey(testBucket, "tenant-a/path/overwrite.txt");
        createBucket(testBucket);

        adapter.putObject(
            config,
            new PutObjectCommand(objectKey, "v1".getBytes(StandardCharsets.UTF_8), "text/plain", Map.of("version", "1"))
        );

        adapter.putObject(
            config,
            new PutObjectCommand(objectKey, "v2".getBytes(StandardCharsets.UTF_8), "text/plain", Map.of("version", "2"))
        );

        StoredObject fetchedObject = adapter.getObject(config, objectKey);
        assertArrayEquals("v2".getBytes(StandardCharsets.UTF_8), fetchedObject.payload());
        assertEquals("2", fetchedObject.metadata().get("version"));
    }

    @Test
    void getAfterDeleteThrowsObjectNotFound() {
        testBucket = "ib-minio-it-" + UUID.randomUUID().toString().replace("-", "");
        ObjectKey objectKey = new ObjectKey(testBucket, "tenant-a/path/deleted.txt");
        createBucket(testBucket);

        adapter.putObject(
            config,
            new PutObjectCommand(objectKey, "payload".getBytes(StandardCharsets.UTF_8), "text/plain", Map.of())
        );

        adapter.deleteObject(config, objectKey);

        assertThrows(ObjectNotFoundException.class, () -> adapter.getObject(config, objectKey));
    }

    private void createBucket(String bucketName) {
        try (BlobStoreContext context = contextProvider.openContext(config)) {
            context.getBlobStore().createContainerInLocation(null, bucketName);
        }
    }

    private static String propertyOrDefault(String key, String fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}