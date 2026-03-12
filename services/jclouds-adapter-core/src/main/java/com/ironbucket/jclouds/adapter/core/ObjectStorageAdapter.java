package com.ironbucket.jclouds.adapter.core;

public interface ObjectStorageAdapter {
    void putObject(ProviderConnectionConfig connectionConfig, PutObjectCommand command);

    StoredObject getObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey);

    void deleteObject(ProviderConnectionConfig connectionConfig, ObjectKey objectKey);
}