package com.ironbucket.jclouds.adapter.core;

import org.jclouds.blobstore.BlobStoreContext;

public interface BlobStoreContextProvider {
    BlobStoreContext openContext(ProviderConnectionConfig connectionConfig);
}
