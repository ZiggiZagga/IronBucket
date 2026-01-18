package com.ironbucket.claimspindel.predicates;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ClaimsRoutePredicateFactory Tests")
class ClaimsRoutePredicateFactoryTests {

    private ClaimsRoutePredicateFactory factory;
    private ClaimsRoutePredicateFactory.Config roleConfig;
    private String hmacSecret;

    @BeforeEach
    void setup() {
        factory = new ClaimsRoutePredicateFactory();
        roleConfig = new ClaimsRoutePredicateFactory.Config("role", "admin");
        hmacSecret = "super-secret-test-key-1234567890123456"; // 32 bytes for HS256
    }

    @Test
    @DisplayName("Matches when expected role is present")
    void matchesWhenRolePresent() throws Exception {
        String token = buildRoleToken("admin");
        MockServerWebExchange exchange = exchangeWithBearer(token);

        assertTrue(factory.apply(roleConfig).test(exchange));
    }

    @Test
    @DisplayName("Rejects when role is missing")
    void rejectsWhenRoleMissing() throws Exception {
        String token = buildRoleToken("viewer");
        MockServerWebExchange exchange = exchangeWithBearer(token);

        assertFalse(factory.apply(roleConfig).test(exchange));
    }

    @Test
    @DisplayName("Rejects when Authorization header is absent")
    void rejectsWhenHeaderMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        assertFalse(factory.apply(roleConfig).test(exchange));
    }

    @Test
    @DisplayName("Rejects malformed JWT")
    void rejectsMalformedJwt() {
        MockServerWebExchange exchange = exchangeWithBearer("not-a-jwt");

        assertFalse(factory.apply(roleConfig).test(exchange));
    }

    @Test
    @DisplayName("Rejects expired JWT")
    void rejectsExpiredJwt() throws Exception {
        String token = buildRoleToken("admin", true);
        MockServerWebExchange exchange = exchangeWithBearer(token);

        assertFalse(factory.apply(roleConfig).test(exchange));
    }

    private MockServerWebExchange exchangeWithBearer(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );
    }

    private String buildRoleToken(String role) throws Exception {
        return buildRoleToken(role, false);
    }

    private String buildRoleToken(String role, boolean expired) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://keycloak.example/realms/dev")
                .audience("sentinel-gear")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of(role)))
                .expirationTime(Date.from(expired ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(3600)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(hmacSecret.getBytes()));
        return jwt.serialize();
    }
}
