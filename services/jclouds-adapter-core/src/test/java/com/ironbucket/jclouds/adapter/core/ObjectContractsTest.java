package com.ironbucket.jclouds.adapter.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectContractsTest {

    @Test
    void objectKeyRejectsBlankValues() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("", "a"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("bucket", " "));
    }

    @Test
    void putObjectCommandDefensivelyCopiesPayloadAndMetadata() {
        byte[] inputPayload = "hello".getBytes();
        PutObjectCommand command = new PutObjectCommand(
            new ObjectKey("bucket", "key"),
            inputPayload,
            "text/plain",
            Map.of("tenant", "demo")
        );

        inputPayload[0] = 'X';
        byte[] payloadFromRecord = command.payload();
        payloadFromRecord[1] = 'Y';

        assertArrayEquals("hello".getBytes(), command.payload());
        assertEquals("demo", command.metadata().get("tenant"));
    }

    @Test
    void storedObjectDefensivelyCopiesPayloadAndMetadata() {
        byte[] inputPayload = "world".getBytes();
        StoredObject storedObject = new StoredObject(
            new ObjectKey("bucket", "key"),
            inputPayload,
            "text/plain",
            Map.of("owner", "alice")
        );

        inputPayload[0] = 'X';
        byte[] payloadFromRecord = storedObject.payload();
        payloadFromRecord[1] = 'Y';

        assertArrayEquals("world".getBytes(), storedObject.payload());
        assertEquals("alice", storedObject.metadata().get("owner"));
    }
}