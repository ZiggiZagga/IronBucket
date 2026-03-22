package com.ironbucket.pactumscroll.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

public final class PactumCacheSupport {

    private PactumCacheSupport() {
    }

    public static CacheManager buildCacheManager(long maxEntries, Duration ttl, String... cacheNames) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(cacheNames);
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(maxEntries)
            .expireAfterWrite(ttl.toMillis(), TimeUnit.MILLISECONDS)
            .recordStats());
        return cacheManager;
    }
}
