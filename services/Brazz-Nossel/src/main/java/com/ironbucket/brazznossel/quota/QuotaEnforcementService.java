package com.ironbucket.brazznossel.quota;

public class QuotaEnforcementService {

    public boolean enforceTenantQuota(String tenantId, long projectedBytes, long limitBytes) {
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        return throttle(projectedBytes > limitBytes);
    }

    public boolean throttle(boolean shouldThrottle) {
        return !shouldThrottle;
    }
}
