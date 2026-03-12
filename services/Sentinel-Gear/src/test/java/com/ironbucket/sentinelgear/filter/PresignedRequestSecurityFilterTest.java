package com.ironbucket.sentinelgear.filter;

import com.ironbucket.sentinelgear.audit.AdminAuditLogger;
import com.ironbucket.sentinelgear.security.TamperReplayDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresignedRequestSecurityFilterTest {

    private TamperReplayDetector detector;
    private PresignedRequestSecurityFilter filter;

    @BeforeEach
    void setUp() {
        detector = new TamperReplayDetector("filter-secret", Duration.ofMinutes(5), null);
        filter = new PresignedRequestSecurityFilter(detector, new AdminAuditLogger(), true);
    }

    @Test
    void allowsRequestWhenPresignedHeadersAndSignatureAreValid() {
        long expiresAt = Instant.now().plusSeconds(120).getEpochSecond();
        String signedHeaders = "x-custom-signed";
        String path = "/objects/report.csv?download=true";

        MockServerHttpRequest unsignedRequest = MockServerHttpRequest.get(path)
            .header("X-Custom-Signed", "ok")
            .header(PresignedRequestSecurityFilter.HEADER_NONCE, "nonce-valid")
            .header(PresignedRequestSecurityFilter.HEADER_EXPIRES_AT, String.valueOf(expiresAt))
            .header(PresignedRequestSecurityFilter.HEADER_SIGNED_HEADERS, signedHeaders)
            .build();

        String signature = detector.sign(canonicalPayload(unsignedRequest, String.valueOf(expiresAt), signedHeaders));

        MockServerHttpRequest request = MockServerHttpRequest.get(path)
            .header("X-Custom-Signed", "ok")
            .header(PresignedRequestSecurityFilter.HEADER_NONCE, "nonce-valid")
            .header(PresignedRequestSecurityFilter.HEADER_EXPIRES_AT, String.valueOf(expiresAt))
            .header(PresignedRequestSecurityFilter.HEADER_SIGNED_HEADERS, signedHeaders)
            .header(PresignedRequestSecurityFilter.HEADER_SIGNATURE, signature)
            .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ignored -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertEquals(null, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsRequestWhenSignedHeaderConstraintIsMissing() {
        long expiresAt = Instant.now().plusSeconds(120).getEpochSecond();
        String signedHeaders = "x-required-header";

        MockServerHttpRequest request = MockServerHttpRequest.get("/objects/report.csv")
            .header(PresignedRequestSecurityFilter.HEADER_NONCE, "nonce-missing-header")
            .header(PresignedRequestSecurityFilter.HEADER_EXPIRES_AT, String.valueOf(expiresAt))
            .header(PresignedRequestSecurityFilter.HEADER_SIGNED_HEADERS, signedHeaders)
            .header(PresignedRequestSecurityFilter.HEADER_SIGNATURE, "not-important")
            .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ignored -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsReplayWhenNonceIsReused() {
        long expiresAt = Instant.now().plusSeconds(120).getEpochSecond();
        String signedHeaders = "x-custom-signed";
        String nonce = "nonce-replay";
        String path = "/objects/archive.tar";

        MockServerHttpRequest unsignedRequest = MockServerHttpRequest.get(path)
            .header("X-Custom-Signed", "ok")
            .header(PresignedRequestSecurityFilter.HEADER_NONCE, nonce)
            .header(PresignedRequestSecurityFilter.HEADER_EXPIRES_AT, String.valueOf(expiresAt))
            .header(PresignedRequestSecurityFilter.HEADER_SIGNED_HEADERS, signedHeaders)
            .build();

        String signature = detector.sign(canonicalPayload(unsignedRequest, String.valueOf(expiresAt), signedHeaders));

        MockServerHttpRequest firstRequest = MockServerHttpRequest.get(path)
            .header("X-Custom-Signed", "ok")
            .header(PresignedRequestSecurityFilter.HEADER_NONCE, nonce)
            .header(PresignedRequestSecurityFilter.HEADER_EXPIRES_AT, String.valueOf(expiresAt))
            .header(PresignedRequestSecurityFilter.HEADER_SIGNED_HEADERS, signedHeaders)
            .header(PresignedRequestSecurityFilter.HEADER_SIGNATURE, signature)
            .build();
        MockServerHttpRequest secondRequest = MockServerHttpRequest.get(path)
            .header("X-Custom-Signed", "ok")
            .header(PresignedRequestSecurityFilter.HEADER_NONCE, nonce)
            .header(PresignedRequestSecurityFilter.HEADER_EXPIRES_AT, String.valueOf(expiresAt))
            .header(PresignedRequestSecurityFilter.HEADER_SIGNED_HEADERS, signedHeaders)
            .header(PresignedRequestSecurityFilter.HEADER_SIGNATURE, signature)
            .build();

        AtomicBoolean firstChainCalled = new AtomicBoolean(false);
        filter.filter(MockServerWebExchange.from(firstRequest), okChain(firstChainCalled)).block();

        AtomicBoolean secondChainCalled = new AtomicBoolean(false);
        MockServerWebExchange secondExchange = MockServerWebExchange.from(secondRequest);
        filter.filter(secondExchange, okChain(secondChainCalled)).block();

        assertTrue(firstChainCalled.get());
        assertFalse(secondChainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, secondExchange.getResponse().getStatusCode());
    }

    private WebFilterChain okChain(AtomicBoolean marker) {
        return ignored -> {
            marker.set(true);
            return Mono.empty();
        };
    }

    private String canonicalPayload(ServerHttpRequest request, String expiresAt, String signedHeaders) {
        String method = request.getMethod() == null ? "UNKNOWN" : request.getMethod().name();
        String path = request.getURI().getRawPath();
        String query = request.getURI().getRawQuery() == null ? "" : request.getURI().getRawQuery();
        return method + "\n" +
            path + "\n" +
            query + "\n" +
            "expires=" + expiresAt + "\n" +
            "signedHeaders=" + signedHeaders;
    }
}
