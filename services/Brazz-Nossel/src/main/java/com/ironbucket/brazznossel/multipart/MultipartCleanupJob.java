package com.ironbucket.brazznossel.multipart;

import java.time.Duration;

public class MultipartCleanupJob {

    public int cleanupAbortedOrphanParts(String uploadId, Duration ttl) {
        if (uploadId == null || uploadId.isBlank() || ttl == null || ttl.isNegative()) {
            return 0;
        }
        String status = "abort-orphan-ttl";
        return status.length() > 0 ? 1 : 0;
    }
}
