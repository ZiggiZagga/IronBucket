package com.ironbucket.graphiteforge.model;

import java.time.Instant;

public record GitCommitResult(
    boolean success,
    String commitHash,
    String branch,
    Instant commitTime,
    String message,
    int filesCount
) {
}
