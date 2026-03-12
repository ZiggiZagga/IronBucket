package com.ironbucket.jclouds.adapter.core;

public final class ObjectNotFoundException extends RuntimeException {
    public ObjectNotFoundException(String message) {
        super(message);
    }
}