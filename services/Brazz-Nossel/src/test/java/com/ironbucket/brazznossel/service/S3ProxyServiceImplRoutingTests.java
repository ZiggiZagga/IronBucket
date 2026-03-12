package com.ironbucket.brazznossel.service;

import com.ironbucket.brazznossel.model.NormalizedIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3ProxyServiceImplRoutingTests {

    @Test
    void bucketOverrideIsPreferredWhenCapabilitySupported() {
        S3ProxyServiceImpl service = new S3ProxyServiceImpl(
            "http://localhost:9000",
            "minioadmin",
            "minioadmin",
            "us-east-1",
            "tenant-a=aws_s3",
            "tenant-a/tenant-a-orders=gcs"
        );

        NormalizedIdentity identity = identity("tenant-a");

        S3ProxyServiceImpl.BackendProvider provider = service.resolveProviderForRequest(
            identity,
            "tenant-a-orders",
            S3ProxyServiceImpl.RequiredCapability.OBJECT_READ
        );

        assertEquals(S3ProxyServiceImpl.BackendProvider.GCS, provider);
    }

    @Test
    void tenantDefaultIsUsedWhenNoBucketOverrideExists() {
        S3ProxyServiceImpl service = new S3ProxyServiceImpl(
            "http://localhost:9000",
            "minioadmin",
            "minioadmin",
            "us-east-1",
            "tenant-a=azure_blob",
            ""
        );

        NormalizedIdentity identity = identity("tenant-a");

        S3ProxyServiceImpl.BackendProvider provider = service.resolveProviderForRequest(
            identity,
            "tenant-a-orders",
            S3ProxyServiceImpl.RequiredCapability.OBJECT_WRITE
        );

        assertEquals(S3ProxyServiceImpl.BackendProvider.AZURE_BLOB, provider);
    }

    @Test
    void fallbackSkipsUnsupportedVersioningProvider() {
        S3ProxyServiceImpl service = new S3ProxyServiceImpl(
            "http://localhost:9000",
            "minioadmin",
            "minioadmin",
            "us-east-1",
            "tenant-a=local_filesystem",
            "tenant-a/tenant-a-orders=azure_blob"
        );

        NormalizedIdentity identity = identity("tenant-a");

        S3ProxyServiceImpl.BackendProvider provider = service.resolveProviderForRequest(
            identity,
            "tenant-a-orders",
            S3ProxyServiceImpl.RequiredCapability.VERSIONING
        );

        assertEquals(S3ProxyServiceImpl.BackendProvider.AWS_S3, provider);
    }

    private static NormalizedIdentity identity(String tenantId) {
        return NormalizedIdentity.builder()
            .userId("user-1")
            .tenantId(tenantId)
            .preferredUsername("alice")
            .roles(List.of("s3:read", "s3:write"))
            .build();
    }
}