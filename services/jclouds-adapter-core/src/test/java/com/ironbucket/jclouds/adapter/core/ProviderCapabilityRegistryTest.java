package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCapabilityRegistryTest {

    @Test
    void allProvidersHaveCapabilityProfiles() {
        ProviderCapabilityRegistry registry = new ProviderCapabilityRegistry();

        for (ProviderType provider : ProviderType.values()) {
            assertTrue(registry.profileOf(provider).isPresent(), "missing profile for " + provider);
            assertFalse(registry.profileOf(provider).orElseThrow().supportedCapabilities().isEmpty(), "empty profile for " + provider);
        }
    }

    @Test
    void providerSpecificCapabilitiesAreMapped() {
        ProviderCapabilityRegistry registry = new ProviderCapabilityRegistry();

        assertTrue(registry.supports(ProviderType.AWS_S3, ProviderCapability.VERSIONING));
        assertTrue(registry.supports(ProviderType.AWS_S3, ProviderCapability.OBJECT_ACL));

        assertTrue(registry.supports(ProviderType.GCS, ProviderCapability.VERSIONING));
        assertFalse(registry.supports(ProviderType.GCS, ProviderCapability.OBJECT_ACL));

        assertTrue(registry.supports(ProviderType.LOCAL_FILESYSTEM, ProviderCapability.OBJECT_READ));
        assertFalse(registry.supports(ProviderType.LOCAL_FILESYSTEM, ProviderCapability.MULTIPART_UPLOAD));
    }

    @Test
    void providerSelectionUsesRequiredCapabilities() {
        ProviderSelectionService selectionService = new ProviderSelectionService(new ProviderCapabilityRegistry());

        Set<ProviderType> multipartAndVersioning = selectionService.providersSupporting(
            EnumSet.of(ProviderCapability.MULTIPART_UPLOAD, ProviderCapability.VERSIONING)
        );
        assertTrue(multipartAndVersioning.contains(ProviderType.AWS_S3));
        assertTrue(multipartAndVersioning.contains(ProviderType.GCS));
        assertFalse(multipartAndVersioning.contains(ProviderType.AZURE_BLOB));

        Set<ProviderType> aclRequired = selectionService.providersSupporting(
            EnumSet.of(ProviderCapability.OBJECT_ACL)
        );
        assertTrue(aclRequired.contains(ProviderType.AWS_S3));
        assertFalse(aclRequired.contains(ProviderType.GCS));
        assertFalse(aclRequired.contains(ProviderType.AZURE_BLOB));
        assertFalse(aclRequired.contains(ProviderType.LOCAL_FILESYSTEM));
    }

    @Test
    void profileRegistrationCanExtendProviderCapabilities() {
        ProviderCapabilityRegistry registry = new ProviderCapabilityRegistry();

        registry.registerProfile(
            new ProviderCapabilityProfile(
                ProviderType.LOCAL_FILESYSTEM,
                EnumSet.of(ProviderCapability.OBJECT_READ, ProviderCapability.OBJECT_WRITE, ProviderCapability.VERSIONING)
            )
        );

        assertTrue(registry.supports(ProviderType.LOCAL_FILESYSTEM, ProviderCapability.VERSIONING));
        assertFalse(registry.supports(ProviderType.LOCAL_FILESYSTEM, ProviderCapability.MULTIPART_UPLOAD));
    }
}
