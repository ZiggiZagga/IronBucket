package com.ironbucket.adminshell.service;

public record BackfillStatus(String tenantId, String bucket, boolean started, String message) {
    public String summary() {
        return "tenant=%s bucket=%s started=%s message=%s".formatted(tenantId, bucket, started, message);
    }
}
