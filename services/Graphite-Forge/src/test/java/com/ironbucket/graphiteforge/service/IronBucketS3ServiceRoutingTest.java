package com.ironbucket.graphiteforge.service;

import com.ironbucket.graphiteforge.model.ProviderRoutingDecision;
import com.ironbucket.graphiteforge.model.S3Bucket;
import com.ironbucket.graphiteforge.model.S3Object;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronBucketS3ServiceRoutingTest {

    @Test
    void bucketResponsesContainSelectedProviderAndReason() {
        IronBucketS3Service service = new IronBucketS3Service();
        service.setTenantDefaultProvider("tenant-a", "AZURE_BLOB");

        S3Bucket created = service.createBucket("jwt", "tenant-a-files", "tenant-a");
        S3Bucket fetched = service.getBucket("jwt", "tenant-a-files");
        List<S3Bucket> listed = service.listBuckets("jwt");

        assertEquals("AZURE_BLOB", created.selectedProvider());
        assertEquals("tenant-default", created.routingReason());
        assertEquals("AZURE_BLOB", fetched.selectedProvider());
        assertEquals("tenant-default", fetched.routingReason());
        assertEquals("AZURE_BLOB", listed.getFirst().selectedProvider());
        assertEquals("tenant-default", listed.getFirst().routingReason());
    }

    @Test
    void objectResponsesExposeFallbackReasonWhenOverridesCannotSatisfyCapability() {
        IronBucketS3Service service = new IronBucketS3Service();
        service.setTenantDefaultProvider("tenant-a", "LOCAL_FILESYSTEM");
        service.setBucketOverrideProvider("tenant-a", "tenant-a-files", "AZURE_BLOB");
        service.createBucket("jwt", "tenant-a-files", "tenant-a");

        ProviderRoutingDecision routing = service.getBucketRoutingDecision(
            "jwt",
            "tenant-a",
            "tenant-a-files",
            "VERSIONING"
        );

        assertEquals("AWS_S3", routing.selectedProvider());
        assertTrue(routing.reason().contains("fallback:default-order"));
        assertTrue(routing.reason().contains("bucket-override-unsupported:AZURE_BLOB"));
        assertTrue(routing.reason().contains("tenant-default-unsupported:LOCAL_FILESYSTEM"));
    }

    @Test
    void uploadedAndFetchedObjectsCarryRoutingMetadata() {
        IronBucketS3Service service = new IronBucketS3Service();
        service.setTenantDefaultProvider("tenant-a", "GCS");
        service.createBucket("jwt", "tenant-a-files", "tenant-a");

        S3Object uploaded = service.uploadObject("jwt", "tenant-a-files", "a.txt", 10L, "text/plain");
        S3Object fetched = service.getObject("jwt", "tenant-a-files", "a.txt");

        assertEquals("GCS", uploaded.selectedProvider());
        assertEquals("tenant-default", uploaded.routingReason());
        assertEquals("GCS", fetched.selectedProvider());
        assertEquals("tenant-default", fetched.routingReason());
    }
}
