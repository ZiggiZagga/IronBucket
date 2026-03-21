package com.ironbucket.sentinelgear.testing;

import java.time.Instant;
import java.util.Collections;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * Test configuration providing a mock ReactiveJwtDecoder for unit tests.
 * Creates JWTs with HS256 algorithm (not alg:none) to ensure proper security chain vetting.
 * 
 * This decoder accepts any token and returns a properly-formed JWT with realistic claims.
 * Use this for testing security filter behavior without requiring a live Keycloak instance.
 */
@TestConfiguration
public class TestJwtDecoderConfig {

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder() {
        return token -> Mono.just(
            Jwt.withTokenValue(token)
                .header("alg", "HS256")  // Proper JWT algorithm (not "none")
                .header("typ", "JWT")
                .subject("test-user")
                .claim("sub", "test-user")
                .claim("scope", "read write")
                .claim("groups", Collections.singletonList("devrole"))
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(3600))
                .issuer("https://steel-hammer-keycloak:7081/realms/dev")
                .audience(Collections.singletonList("ironbucket"))
                .build()
        );
    }
}