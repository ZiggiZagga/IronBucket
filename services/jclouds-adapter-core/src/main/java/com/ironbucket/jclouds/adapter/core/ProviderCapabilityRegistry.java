package com.ironbucket.jclouds.adapter.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

public final class ProviderCapabilityRegistry {
    private final Map<ProviderType, ProviderCapabilityProfile> profiles;

    public ProviderCapabilityRegistry() {
        this.profiles = new EnumMap<>(ProviderType.class);
        seedDefaultProfiles();
    }

    private void seedDefaultProfiles() {
        profiles.put(
            ProviderType.AWS_S3,
            new ProviderCapabilityProfile(
                ProviderType.AWS_S3,
                EnumSet.of(
                    ProviderCapability.OBJECT_READ,
                    ProviderCapability.OBJECT_WRITE,
                    ProviderCapability.OBJECT_DELETE,
                    ProviderCapability.MULTIPART_UPLOAD,
                    ProviderCapability.VERSIONING,
                    ProviderCapability.OBJECT_TAGGING,
                    ProviderCapability.OBJECT_ACL,
                    ProviderCapability.LIFECYCLE_POLICY,
                    ProviderCapability.PRESIGNED_URLS
                )
            )
        );

        profiles.put(
            ProviderType.GCS,
            new ProviderCapabilityProfile(
                ProviderType.GCS,
                EnumSet.of(
                    ProviderCapability.OBJECT_READ,
                    ProviderCapability.OBJECT_WRITE,
                    ProviderCapability.OBJECT_DELETE,
                    ProviderCapability.MULTIPART_UPLOAD,
                    ProviderCapability.VERSIONING,
                    ProviderCapability.OBJECT_TAGGING,
                    ProviderCapability.LIFECYCLE_POLICY,
                    ProviderCapability.PRESIGNED_URLS
                )
            )
        );

        profiles.put(
            ProviderType.AZURE_BLOB,
            new ProviderCapabilityProfile(
                ProviderType.AZURE_BLOB,
                EnumSet.of(
                    ProviderCapability.OBJECT_READ,
                    ProviderCapability.OBJECT_WRITE,
                    ProviderCapability.OBJECT_DELETE,
                    ProviderCapability.MULTIPART_UPLOAD,
                    ProviderCapability.OBJECT_TAGGING,
                    ProviderCapability.PRESIGNED_URLS
                )
            )
        );

        profiles.put(
            ProviderType.LOCAL_FILESYSTEM,
            new ProviderCapabilityProfile(
                ProviderType.LOCAL_FILESYSTEM,
                EnumSet.of(
                    ProviderCapability.OBJECT_READ,
                    ProviderCapability.OBJECT_WRITE,
                    ProviderCapability.OBJECT_DELETE
                )
            )
        );
    }

    public Optional<ProviderCapabilityProfile> profileOf(ProviderType providerType) {
        return Optional.ofNullable(profiles.get(providerType));
    }

    public boolean supports(ProviderType providerType, ProviderCapability capability) {
        return profileOf(providerType).map(profile -> profile.supports(capability)).orElse(false);
    }
}
