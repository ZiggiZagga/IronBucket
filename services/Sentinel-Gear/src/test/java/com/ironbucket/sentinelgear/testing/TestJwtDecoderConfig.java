package com.ironbucket.sentinelgear.testing;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

@TestConfiguration
public class TestJwtDecoderConfig {

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder() {
        return token -> Mono.just(
            Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("sub", "test-user")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
        );
    }
}