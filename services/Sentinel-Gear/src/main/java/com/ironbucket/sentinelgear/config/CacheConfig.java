package com.ironbucket.sentinelgear.config;

import com.ironbucket.pactumscroll.config.PactumCacheSupport;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache Configuration
 * 
 * Configures Caffeine-based caching for identity and policy data
 * Reduces repeated lookups for the same claims or policies
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return PactumCacheSupport.buildCacheManager(
            10000,
            Duration.ofMinutes(5),
            "identities",      // Cache normalized identities
            "policies",        // Cache policy evaluations
            "claims",          // Cache claim validations
            "jwks"             // Cache JWKS from identity provider
        );
    }
}
