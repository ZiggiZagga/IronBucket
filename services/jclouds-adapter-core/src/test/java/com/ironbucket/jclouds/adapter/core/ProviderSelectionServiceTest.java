package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderSelectionServiceTest {

    @Test
    void selectsBucketOverrideWhenCapabilitiesMatch() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setBucketProvider("tenant-a", "orders", ProviderType.GCS);

        ProviderSelectionService selectionService = new ProviderSelectionService(
            new ProviderCapabilityRegistry(),
            tenantRegistry
        );

        ProviderType selected = selectionService.selectProvider(
            "tenant-a",
            "orders",
            EnumSet.of(ProviderCapability.VERSIONING)
        ).orElseThrow();

        assertEquals(ProviderType.GCS, selected);
    }

    @Test
    void fallsBackWhenBucketOverrideDoesNotSupportRequiredCapabilities() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setBucketProvider("tenant-a", "orders", ProviderType.LOCAL_FILESYSTEM);

        ProviderSelectionService selectionService = new ProviderSelectionService(
            new ProviderCapabilityRegistry(),
            tenantRegistry
        );

        ProviderType selected = selectionService.selectProvider(
            "tenant-a",
            "orders",
            EnumSet.of(ProviderCapability.VERSIONING)
        ).orElseThrow();

        assertEquals(ProviderType.AWS_S3, selected);
    }

    @Test
    void usesTenantDefaultWhenNoBucketOverrideExists() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setTenantDefaultProvider("tenant-a", ProviderType.AZURE_BLOB);

        ProviderSelectionService selectionService = new ProviderSelectionService(
            new ProviderCapabilityRegistry(),
            tenantRegistry
        );

        ProviderType selected = selectionService.selectProvider(
            "tenant-a",
            "orders",
            EnumSet.of(ProviderCapability.PRESIGNED_URLS)
        ).orElseThrow();

        assertEquals(ProviderType.AZURE_BLOB, selected);
    }

    @Test
    void usesDeterministicFallbackOrderWhenTenantPreferenceCannotSatisfyCapabilities() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setTenantDefaultProvider("tenant-a", ProviderType.LOCAL_FILESYSTEM);

        ProviderCapabilityRegistry capabilityRegistry = new ProviderCapabilityRegistry();
        ProviderSelectionService selectionService = new ProviderSelectionService(capabilityRegistry, tenantRegistry);

        ProviderType selected = selectionService.selectProvider(
            "tenant-a",
            "orders",
            EnumSet.allOf(ProviderCapability.class)
        ).orElseThrow();

        assertEquals(ProviderType.AWS_S3, selected);
    }
}