package com.ironbucket.jclouds.adapter.core;

import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

public final class JcloudsObjectStorageAdapter implements ObjectStorageAdapter {
    private final BlobStoreContextProvider contextProvider;

    public JcloudsObjectStorageAdapter(BlobStoreContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public void putObject(ProviderConnectionConfig connectionConfig, PutObjectCommand command) {
        try (BlobStoreContext context = contextProvider.openContext(connectionConfig)) {
            BlobStore blobStore = context.getBlobStore();
            BlobBuilder.PayloadBlobBuilder builder = blobStore.blobBuilder(command.objectKey().key())
                .payload(command.payload());

            if (command.contentType() != null && !command.contentType().isBlank()) {
                builder.contentType(command.contentType());
            }

            if (!command.metadata().isEmpty()) {
                builder.userMetadata(command.metadata());
            }

            blobStore.putBlob(command.objectKey().bucket(), builder.build());
        }
    }

    @Override
    public StoredObject getObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey) {
        try (BlobStoreContext context = contextProvider.openContext(connectionConfig)) {
            BlobStore blobStore = context.getBlobStore();
            Blob blob = blobStore.getBlob(objectKey.bucket(), objectKey.key());
            if (blob == null) {
                throw new ObjectNotFoundException("Object not found: " + objectKey.bucket() + "/" + objectKey.key());
            }

            try (InputStream inputStream = blob.getPayload().openStream()) {
                byte[] payload = inputStream.readAllBytes();
                String contentType = blob.getMetadata().getContentMetadata().getContentType();
                Map<String, String> metadata = blob.getMetadata().getUserMetadata() == null
                    ? Map.of()
                    : Map.copyOf(blob.getMetadata().getUserMetadata());
                return new StoredObject(objectKey, payload, contentType, metadata);
            } catch (IOException ioException) {
                throw new UncheckedIOException("Unable to read payload for object " + objectKey.key(), ioException);
            }
        }
    }

    @Override
    public void deleteObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey) {
        try (BlobStoreContext context = contextProvider.openContext(connectionConfig)) {
            context.getBlobStore().removeBlob(objectKey.bucket(), objectKey.key());
        }
    }
}