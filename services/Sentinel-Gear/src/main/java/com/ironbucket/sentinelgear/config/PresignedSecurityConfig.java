package com.ironbucket.sentinelgear.config;

import com.ironbucket.sentinelgear.security.TamperReplayDetector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(PresignedSecurityProperties.class)
public class PresignedSecurityConfig {

    @Bean
    public TamperReplayDetector tamperReplayDetector(PresignedSecurityProperties properties) {
        Duration nonceTtl = properties.getNonceTtl();
        if (nonceTtl == null || nonceTtl.isZero() || nonceTtl.isNegative()) {
            throw new IllegalStateException("ironbucket.security.presigned.nonce-ttl must be positive");
        }

        if (properties.isEnabled() && (properties.getSecret() == null || properties.getSecret().isBlank())) {
            throw new IllegalStateException("IRONBUCKET_SECURITY_PRESIGNED_SECRET is required when presigned security is enabled");
        }

        String secret = properties.isEnabled() ? properties.getSecret() : "presigned-security-disabled";
        return new TamperReplayDetector(secret, nonceTtl, Clock.systemUTC());
    }
}
