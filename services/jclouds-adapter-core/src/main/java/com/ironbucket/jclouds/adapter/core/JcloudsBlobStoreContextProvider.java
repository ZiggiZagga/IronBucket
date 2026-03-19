package com.ironbucket.jclouds.adapter.core;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;

import java.util.Map;
import java.util.Properties;

public final class JcloudsBlobStoreContextProvider implements BlobStoreContextProvider {

    @Override
    public BlobStoreContext openContext(ProviderConnectionConfig connectionConfig) {
        String providerId = toProviderId(connectionConfig.providerType());
        ContextBuilder builder = ContextBuilder.newBuilder(providerId);
        ResolvedCredentials resolvedCredentials = resolveCredentials(connectionConfig);

        if (resolvedCredentials.present()) {
            builder = builder.credentials(resolvedCredentials.identity(), resolvedCredentials.credential());
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

    static boolean hasResolvableCredentials(ProviderConnectionConfig connectionConfig) {
        return resolveCredentials(connectionConfig).present();
    }

    static ResolvedCredentials resolveCredentials(ProviderConnectionConfig connectionConfig) {
        if (connectionConfig.hasCredentials()) {
            return new ResolvedCredentials(connectionConfig.identity(), connectionConfig.credential());
        }

        Map<String, String> properties = connectionConfig.properties();

        if (connectionConfig.providerType() == ProviderType.GCS) {
            String serviceAccountEmail = firstNonBlank(
                properties.get("ironbucket.gcs.service-account-email"),
                properties.get("jclouds.identity")
            );
            String serviceAccountPrivateKey = firstNonBlank(
                properties.get("ironbucket.gcs.service-account-private-key"),
                properties.get("jclouds.credential")
            );
            if (isNonBlank(serviceAccountEmail) && isNonBlank(serviceAccountPrivateKey)) {
                return new ResolvedCredentials(serviceAccountEmail, serviceAccountPrivateKey);
            }

            String accessToken = firstNonBlank(
                properties.get("ironbucket.gcs.access-token"),
                properties.get("ironbucket.gcs.managed-identity-token")
            );
            if (isNonBlank(accessToken)) {
                return new ResolvedCredentials("oauth2accesstoken", accessToken);
            }
        }

        if (connectionConfig.providerType() == ProviderType.AZURE_BLOB) {
            String accountName = firstNonBlank(
                properties.get("ironbucket.azure.account-name"),
                properties.get("azure.storage.account")
            );
            String accountKey = firstNonBlank(
                properties.get("ironbucket.azure.account-key"),
                properties.get("azure.storage.key")
            );
            if (isNonBlank(accountName) && isNonBlank(accountKey)) {
                return new ResolvedCredentials(accountName, accountKey);
            }

            String sasToken = firstNonBlank(properties.get("ironbucket.azure.sas-token"));
            if (isNonBlank(accountName) && isNonBlank(sasToken)) {
                return new ResolvedCredentials(accountName, sasToken);
            }

            String managedIdentityToken = firstNonBlank(properties.get("ironbucket.azure.managed-identity-token"));
            if (isNonBlank(managedIdentityToken)) {
                return new ResolvedCredentials("oauth2accesstoken", managedIdentityToken);
            }
        }

        return ResolvedCredentials.none();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (isNonBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    record ResolvedCredentials(String identity, String credential) {
        boolean present() {
            return identity != null && !identity.isBlank() && credential != null && !credential.isBlank();
        }

        static ResolvedCredentials none() {
            return new ResolvedCredentials(null, null);
        }
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
