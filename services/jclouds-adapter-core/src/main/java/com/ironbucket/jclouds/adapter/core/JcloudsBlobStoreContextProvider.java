package com.ironbucket.jclouds.adapter.core;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;

import java.util.Properties;

public final class JcloudsBlobStoreContextProvider implements BlobStoreContextProvider {

    @Override
    public BlobStoreContext openContext(ProviderConnectionConfig connectionConfig) {
        String providerId = toProviderId(connectionConfig.providerType());
        ContextBuilder builder = ContextBuilder.newBuilder(providerId);

        if (connectionConfig.hasCredentials()) {
            builder = builder.credentials(connectionConfig.identity(), connectionConfig.credential());
        }

        if (connectionConfig.hasEndpoint()) {
            builder = builder.endpoint(connectionConfig.endpoint());
        }

        if (!connectionConfig.properties().isEmpty()) {
            Properties overrides = new Properties();
            connectionConfig.properties().forEach(overrides::setProperty);
            builder = builder.overrides(overrides);
        }

        return builder.buildView(BlobStoreContext.class);
    }

    static String toProviderId(ProviderType providerType) {
        return switch (providerType) {
            case AWS_S3 -> "aws-s3";
            case GCS -> "google-cloud-storage";
            case AZURE_BLOB -> "azureblob";
            case LOCAL_FILESYSTEM -> "filesystem";
        };
    }
}
