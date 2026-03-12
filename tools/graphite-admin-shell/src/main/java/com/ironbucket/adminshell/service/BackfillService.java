package com.ironbucket.adminshell.service;

import java.time.Duration;

public interface BackfillService {
    BackfillStatus startBackfill(String tenantId, String bucket, boolean dryRun, Duration throttle);
}
