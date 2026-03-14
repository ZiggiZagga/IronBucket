package com.ironbucket.sentinelgear.config;

import com.ironbucket.sentinelgear.security.TamperReplayDetector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(PresignedSecurityProperties.class)
public class PresignedSecurityConfig {

    @Bean
    public TamperReplayDetector tamperReplayDetector(
        PresignedSecurityProperties properties,
        ObjectProvider<VaultSecretResolver> vaultSecretResolverProvider
    ) {
        return createDetector(properties, vaultSecretResolverProvider.getIfAvailable());
    }

    TamperReplayDetector createDetector(PresignedSecurityProperties properties, VaultSecretResolver vaultSecretResolver) {
        Duration nonceTtl = properties.getNonceTtl();
        if (nonceTtl == null || nonceTtl.isZero() || nonceTtl.isNegative()) {
            throw new IllegalStateException("ironbucket.security.presigned.nonce-ttl must be positive");
        }

        String secret = normalizeSecret(properties.getSecret());
        if (properties.isEnabled() && secret == null && vaultSecretResolver != null) {
            secret = vaultSecretResolver.resolveSecret().map(PresignedSecurityConfig::normalizeSecret).orElse(null);
        }

        if (properties.isEnabled() && secret == null) {
            throw new IllegalStateException("IRONBUCKET_SECURITY_PRESIGNED_SECRET or Vault-backed presigned secret is required when presigned security is enabled");
        }

        secret = properties.isEnabled() ? secret : "presigned-security-disabled";
        return new TamperReplayDetector(secret, nonceTtl, Clock.systemUTC());
    }

    private static String normalizeSecret(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
