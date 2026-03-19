package com.ironbucket.jclouds.adapter.core;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void denyOverridesAllowWhenPolicyExplicitlyDeniesTenantPreferredProvider() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setTenantDefaultProvider("tenant-a", ProviderType.AWS_S3);

        ProviderSelectionService selectionService = new ProviderSelectionService(
            new ProviderCapabilityRegistry(),
            tenantRegistry
        );

        ProviderRoutingPolicy policy = new ProviderRoutingPolicy(
            EnumSet.of(ProviderType.AWS_S3, ProviderType.GCS),
            EnumSet.of(ProviderType.AWS_S3)
        );

        ProviderType selected = selectionService.selectProvider(
            "tenant-a",
            "orders",
            EnumSet.of(ProviderCapability.VERSIONING),
            policy
        ).orElseThrow();

        assertEquals(ProviderType.GCS, selected);
    }

    @Test
    void selectProviderDecisionReturnsAttemptOrderAndReasonWhenNoMatch() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setTenantDefaultProvider("tenant-a", ProviderType.LOCAL_FILESYSTEM);

        ProviderSelectionService selectionService = new ProviderSelectionService(
            new ProviderCapabilityRegistry(),
            tenantRegistry
        );

        ProviderSelectionDecision decision = selectionService.selectProviderDecision(
            "tenant-a",
            "orders",
            EnumSet.of(ProviderCapability.OBJECT_ACL),
            new ProviderRoutingPolicy(Set.of(), EnumSet.of(ProviderType.AWS_S3))
        );

        assertFalse(decision.hasSelection());
        assertTrue(decision.attemptedProviders().contains(ProviderType.LOCAL_FILESYSTEM));
        assertTrue(decision.reason().contains("no-provider"));
    }

    @Test
    void exposesCapabilityMatrixForAllKnownProviders() {
        ProviderCapabilityRegistry registry = new ProviderCapabilityRegistry();
        ProviderSelectionService selectionService = new ProviderSelectionService(registry);

        Map<ProviderType, Set<ProviderCapability>> matrix = registry.capabilityMatrix();

        assertTrue(matrix.containsKey(ProviderType.AWS_S3));
        assertTrue(matrix.get(ProviderType.AWS_S3).contains(ProviderCapability.VERSIONING));
        assertTrue(matrix.containsKey(ProviderType.LOCAL_FILESYSTEM));
        assertFalse(matrix.get(ProviderType.LOCAL_FILESYSTEM).contains(ProviderCapability.MULTIPART_UPLOAD));
        assertTrue(selectionService.capabilitiesOf(ProviderType.GCS).contains(ProviderCapability.MULTIPART_UPLOAD));
    }

    @Test
    void publishesSelectionAndFallbackMetrics() {
        TenantBucketProviderRegistry tenantRegistry = new TenantBucketProviderRegistry();
        tenantRegistry.setTenantDefaultProvider("tenant-a", ProviderType.LOCAL_FILESYSTEM);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ProviderSelectionService selectionService = new ProviderSelectionService(
            new ProviderCapabilityRegistry(),
            tenantRegistry,
            meterRegistry
        );

        selectionService.selectProvider(
            "tenant-a",
            "orders",
            EnumSet.of(ProviderCapability.VERSIONING),
            ProviderRoutingPolicy.unrestricted()
        ).orElseThrow();

        double selected = meterRegistry.get("ironbucket.provider.selection.result")
            .tag("outcome", "selected")
            .counter()
            .count();
        double fallback = meterRegistry.get("ironbucket.provider.selection.result")
            .tag("outcome", "fallback")
            .counter()
            .count();
        long latencyCount = meterRegistry.get("ironbucket.provider.selection.latency")
            .timer()
            .count();

        assertTrue(selected >= 1.0d);
        assertTrue(fallback >= 1.0d);
        assertTrue(latencyCount >= 1L);
    }
}