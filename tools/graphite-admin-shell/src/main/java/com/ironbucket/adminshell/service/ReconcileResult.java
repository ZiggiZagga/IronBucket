package com.ironbucket.adminshell.service;

import java.util.List;

public record ReconcileResult(String bucket, boolean succeeded, List<String> diffs) {
    public String summary() {
        return "bucket=%s succeeded=%s diffs=%s".formatted(bucket, succeeded, diffs);
    }
}
