package com.ironbucket.jclouds.adapter.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record PutObjectCommand(
    ObjectKey objectKey,
    byte[] payload,
    String contentType,
    Map<String, String> metadata
) {
    public PutObjectCommand {
        if (objectKey == null) {
            throw new IllegalArgumentException("objectKey must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        payload = payload.clone();
        metadata = metadata == null
            ? Map.of()
            : Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}