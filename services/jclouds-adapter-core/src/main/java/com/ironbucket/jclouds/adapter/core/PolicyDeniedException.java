package com.ironbucket.jclouds.adapter.core;

public final class PolicyDeniedException extends RuntimeException {
    public PolicyDeniedException(String message) {
        super(message);
    }
}