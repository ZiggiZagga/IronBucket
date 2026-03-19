package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JcloudsBlobStoreContextProviderTest {

    @Test
    void providerTypeMappingMatchesExpectedJcloudsIdentifiers() {
        assertEquals("aws-s3", JcloudsBlobStoreContextProvider.toProviderId(ProviderType.AWS_S3));
        assertEquals("google-cloud-storage", JcloudsBlobStoreContextProvider.toProviderId(ProviderType.GCS));
        assertEquals("azureblob", JcloudsBlobStoreContextProvider.toProviderId(ProviderType.AZURE_BLOB));
        assertEquals("filesystem", JcloudsBlobStoreContextProvider.toProviderId(ProviderType.LOCAL_FILESYSTEM));
    }

    @Test
    void resolvesGcsServiceAccountCredentialsFromProperties() {
        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.GCS,
            null,
            null,
            null,
            Map.of(
                "ironbucket.gcs.service-account-email", "svc@project.iam.gserviceaccount.com",
                "ironbucket.gcs.service-account-private-key", "-----BEGIN PRIVATE KEY-----abc"
            )
        );

        var resolved = JcloudsBlobStoreContextProvider.resolveCredentials(config);
        assertTrue(resolved.present());
        assertEquals("svc@project.iam.gserviceaccount.com", resolved.identity());
    }

    @Test
    void resolvesGcsTokenCredentialFlow() {
        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.GCS,
            null,
            null,
            null,
            Map.of("ironbucket.gcs.access-token", "token-123")
        );

        var resolved = JcloudsBlobStoreContextProvider.resolveCredentials(config);
        assertTrue(resolved.present());
        assertEquals("oauth2accesstoken", resolved.identity());
        assertEquals("token-123", resolved.credential());
    }

    @Test
    void resolvesAzureManagedIdentityTokenFlow() {
        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.AZURE_BLOB,
            null,
            null,
            null,
            Map.of("ironbucket.azure.managed-identity-token", "azure-mi-token")
        );

        var resolved = JcloudsBlobStoreContextProvider.resolveCredentials(config);
        assertTrue(resolved.present());
        assertEquals("oauth2accesstoken", resolved.identity());
        assertEquals("azure-mi-token", resolved.credential());
    }

    @Test
    void unresolvedCredentialsRemainAbsent() {
        ProviderConnectionConfig config = new ProviderConnectionConfig(
            ProviderType.GCS,
            null,
            null,
            null,
            Map.of()
        );

        var resolved = JcloudsBlobStoreContextProvider.resolveCredentials(config);
        assertFalse(resolved.present());
    }
}
