package com.ironbucket.storageconductor;

import com.ironbucket.vaultsmith.adapter.S3StorageBackend;
import com.ironbucket.vaultsmith.config.S3BackendConfig;
import com.ironbucket.vaultsmith.impl.AwsS3Backend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive S3 Compatibility Tests
 * 
 * Tests S3 backend functionality against:
 * - AWS S3 (if AWS credentials provided)
 * - MinIO (local docker instance)
 * - Ceph RGW (if configured)
 * 
 * NOTE: These are integration tests that require a running S3-compatible backend.
 * Set S3_TESTS_ENABLED=true environment variable to run these tests.
 */
@DisplayName("S3 Compatibility Tests")
@EnabledIfEnvironmentVariable(named = "S3_TESTS_ENABLED", matches = "true")
public class S3CompatibilityTest {

    private S3StorageBackend backend;
    private S3BackendConfig config;

    @BeforeEach
    public void setup() {
        // Configure for local MinIO or S3-compatible service
        String endpoint = System.getProperty("s3.endpoint", "http://localhost:9000");
        String accessKey = System.getProperty("s3.accessKey", "minioadmin");
        String secretKey = System.getProperty("s3.secretKey", "minioadmin");
        String region = System.getProperty("s3.region", "us-east-1");

        config = new S3BackendConfig("aws-s3", region, endpoint, accessKey, secretKey);
        config.setPathStyleAccess(true);

        backend = new AwsS3Backend(config);
    }

    @Test
    @DisplayName("Should initialize S3 backend")
    public void testInitialization() {
        assertDoesNotThrow(() -> backend.initialize());
    }

    @Test
    @DisplayName("Should test connectivity to S3 backend")
    public void testConnectivity() {
        assertDoesNotThrow(() -> backend.initialize());
        assertDoesNotThrow(() -> {
            boolean connected = backend.testConnectivity();
            assertTrue(connected, "Should be able to connect to S3 backend");
        });
    }

    @Test
    @DisplayName("Should create bucket")
    public void testCreateBucket() {
        assertDoesNotThrow(() -> {
            backend.initialize();
            String bucketName = "test-bucket-" + System.currentTimeMillis();
            backend.createBucket(bucketName);
            assertTrue(backend.bucketExists(bucketName), "Bucket should exist after creation");
            backend.deleteBucket(bucketName);
        });
    }

    @Test
    @DisplayName("Should list buckets")
    public void testListBuckets() {
        assertDoesNotThrow(() -> {
            backend.initialize();
            Set<String> buckets = backend.listBuckets();
            assertNotNull(buckets, "Bucket list should not be null");
        });
    }

    @Test
    @DisplayName("Should upload and download object")
    public void testUploadAndDownload() {
        assertDoesNotThrow(() -> {
            backend.initialize();
            String bucketName = "test-bucket-" + System.currentTimeMillis();
            String objectKey = "test-file.txt";
            String content = "Hello, S3!";

            backend.createBucket(bucketName);
            
            // Upload
            byte[] contentBytes = content.getBytes();
            backend.uploadObject(bucketName, objectKey, new ByteArrayInputStream(contentBytes), contentBytes.length, "text/plain");
            
            // Verify metadata
            var metadata = backend.getObjectMetadata(bucketName, objectKey);
            assertNotNull(metadata);
            assertEquals(contentBytes.length, metadata.getContentLength());
            
            // Download
            var downloadedStream = backend.downloadObject(bucketName, objectKey);
            assertNotNull(downloadedStream);
            
            // Cleanup
            backend.deleteObject(bucketName, objectKey);
            backend.deleteBucket(bucketName);
        });
    }

    @Test
    @DisplayName("Should list objects in bucket")
    public void testListObjects() {
        assertDoesNotThrow(() -> {
            backend.initialize();
            String bucketName = "test-bucket-" + System.currentTimeMillis();
            backend.createBucket(bucketName);

            Set<String> objects = backend.listObjects(bucketName, null);
            assertNotNull(objects);

            backend.deleteBucket(bucketName);
        });
    }

    @Test
    @DisplayName("Should copy object")
    public void testCopyObject() {
        assertDoesNotThrow(() -> {
            backend.initialize();
            String bucketName = "test-bucket-" + System.currentTimeMillis();
            String sourceKey = "source.txt";
            String destKey = "dest.txt";
            String content = "Copy test";

            backend.createBucket(bucketName);
            
            // Upload source object
            byte[] contentBytes = content.getBytes();
            backend.uploadObject(bucketName, sourceKey, new ByteArrayInputStream(contentBytes), contentBytes.length, "text/plain");
            
            // Copy object
            var copyResult = backend.copyObject(bucketName, sourceKey, bucketName, destKey);
            assertNotNull(copyResult);
            
            // Verify destination exists
            assertTrue(backend.listObjects(bucketName, null).contains(destKey));
            
            // Cleanup
            backend.deleteObject(bucketName, sourceKey);
            backend.deleteObject(bucketName, destKey);
            backend.deleteBucket(bucketName);
        });
    }

    @Test
    @DisplayName("Should handle multipart upload")
    public void testMultipartUpload() {
        assertDoesNotThrow(() -> {
            backend.initialize();
            String bucketName = "test-bucket-" + System.currentTimeMillis();
            String objectKey = "multipart.txt";

            backend.createBucket(bucketName);
            
            // Initiate multipart
            String uploadId = backend.initiateMultipartUpload(bucketName, objectKey);
            assertNotNull(uploadId);
            
            // Upload parts
            byte[] part1Data = "Part 1".getBytes();
            String part1Etag = backend.uploadPart(bucketName, objectKey, uploadId, 1, 
                    new ByteArrayInputStream(part1Data), part1Data.length);
            assertNotNull(part1Etag);
            
            byte[] part2Data = "Part 2".getBytes();
            String part2Etag = backend.uploadPart(bucketName, objectKey, uploadId, 2, 
                    new ByteArrayInputStream(part2Data), part2Data.length);
            assertNotNull(part2Etag);
            
            // Complete multipart
            var partETags = new java.util.HashMap<Integer, String>();
            partETags.put(1, part1Etag);
            partETags.put(2, part2Etag);
            backend.completeMultipartUpload(bucketName, objectKey, uploadId, partETags);
            
            // Verify object exists
            assertTrue(backend.listObjects(bucketName, null).contains(objectKey));
            
            // Cleanup
            backend.deleteObject(bucketName, objectKey);
            backend.deleteBucket(bucketName);
        });
    }

    @Test
    @DisplayName("Should abort multipart upload")
    public void testAbortMultipartUpload() {
        assertDoesNotThrow(() -> {
            backend.initialize();
            String bucketName = "test-bucket-" + System.currentTimeMillis();
            String objectKey = "abort-test.txt";

            backend.createBucket(bucketName);
            
            // Initiate multipart
            String uploadId = backend.initiateMultipartUpload(bucketName, objectKey);
            
            // Upload a part
            byte[] partData = "Part data".getBytes();
            backend.uploadPart(bucketName, objectKey, uploadId, 1, 
                    new ByteArrayInputStream(partData), partData.length);
            
            // Abort multipart
            backend.abortMultipartUpload(bucketName, objectKey, uploadId);
            
            // Cleanup
            backend.deleteBucket(bucketName);
        });
    }

    @Test
    @DisplayName("Should delete object")
    public void testDeleteObject() {
        assertDoesNotThrow(() -> {
            backend.initialize();
            String bucketName = "test-bucket-" + System.currentTimeMillis();
            String objectKey = "delete-test.txt";

            backend.createBucket(bucketName);
            
            // Upload object
            byte[] contentBytes = "Delete me".getBytes();
            backend.uploadObject(bucketName, objectKey, new ByteArrayInputStream(contentBytes), contentBytes.length, "text/plain");
            
            // Delete object
            backend.deleteObject(bucketName, objectKey);
            
            // Verify deletion
            assertFalse(backend.listObjects(bucketName, null).contains(objectKey));
            
            // Cleanup
            backend.deleteBucket(bucketName);
        });
    }
}
