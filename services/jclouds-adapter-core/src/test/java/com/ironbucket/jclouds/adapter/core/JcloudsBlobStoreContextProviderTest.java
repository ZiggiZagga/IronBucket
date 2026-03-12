package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JcloudsBlobStoreContextProviderTest {

    @Test
    void providerTypeMappingMatchesExpectedJcloudsIdentifiers() {
        assertEquals("aws-s3", JcloudsBlobStoreContextProvider.toProviderId(ProviderType.AWS_S3));
        assertEquals("google-cloud-storage", JcloudsBlobStoreContextProvider.toProviderId(ProviderType.GCS));
        assertEquals("azureblob", JcloudsBlobStoreContextProvider.toProviderId(ProviderType.AZURE_BLOB));
        assertEquals("filesystem", JcloudsBlobStoreContextProvider.toProviderId(ProviderType.LOCAL_FILESYSTEM));
    }
}
