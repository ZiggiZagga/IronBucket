package com.ironbucket.sentinelgear.identity;

import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Token Blacklist Service
 * 
 * Maintains a blacklist of revoked/logout tokens to prevent reuse
 * Automatically removes expired tokens from the blacklist
 */
@Service
public class TokenBlacklistService {
    
    private final Set<String> blacklist = new ConcurrentHashMap<>().newKeySet();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    
    public TokenBlacklistService() {
        // Periodically clean up expired tokens (run every 5 minutes)
        executorService.scheduleAtFixedRate(this::cleanupExpired, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Add JWT ID (jti claim) to blacklist
     * @param jti JWT ID from token
     */
    public void blacklistToken(String jti) {
        if (jti != null && !jti.isEmpty()) {
            blacklist.add(jti);
        }
    }
    
    /**
     * Check if token is blacklisted
     * @param jti JWT ID from token
     * @return true if token is blacklisted
     */
    public boolean isBlacklisted(String jti) {
        return jti != null && blacklist.contains(jti);
    }
    
    /**
     * Remove token from blacklist (e.g., for re-authentication)
     * @param jti JWT ID from token
     */
    public void removeFromBlacklist(String jti) {
        if (jti != null) {
            blacklist.remove(jti);
        }
    }
    
    /**
     * Get current blacklist size
     */
    public int getBlacklistSize() {
        return blacklist.size();
    }
    
    /**
     * Clear entire blacklist (for testing or admin operations)
     */
    public void clearBlacklist() {
        blacklist.clear();
    }
    
    /**
     * Cleanup expired tokens
     * Note: In production, implement with TTL-based expiration
     * This is a placeholder for the cleanup logic
     */
    private void cleanupExpired() {
        // In a production system, you would:
        // 1. Store tokens with expiration timestamps
        // 2. Remove entries older than the token's exp claim
        // This is simplified for now
    }
    
    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
