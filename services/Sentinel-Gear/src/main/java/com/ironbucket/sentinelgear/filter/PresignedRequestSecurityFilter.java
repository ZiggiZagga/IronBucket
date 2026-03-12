package com.ironbucket.sentinelgear.filter;

import com.ironbucket.sentinelgear.audit.AdminAuditLogger;
import com.ironbucket.sentinelgear.security.TamperReplayDetector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PresignedRequestSecurityFilter implements WebFilter {

    static final String HEADER_SIGNATURE = "X-IronBucket-Presigned-Signature";
    static final String HEADER_NONCE = "X-IronBucket-Presigned-Nonce";
    static final String HEADER_EXPIRES_AT = "X-IronBucket-Presigned-Expires";
    static final String HEADER_SIGNED_HEADERS = "X-IronBucket-Presigned-SignedHeaders";

    private final TamperReplayDetector detector;
    private final AdminAuditLogger auditLogger;
    private final boolean presignedEnabled;

    public PresignedRequestSecurityFilter(
        TamperReplayDetector detector,
        AdminAuditLogger auditLogger,
        @Value("${ironbucket.security.presigned.enabled:true}") boolean presignedEnabled
    ) {
        this.detector = detector;
        this.auditLogger = auditLogger;
        this.presignedEnabled = presignedEnabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!presignedEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String signature = request.getHeaders().getFirst(HEADER_SIGNATURE);

        if (signature == null || signature.isBlank()) {
            return chain.filter(exchange);
        }

        String nonce = request.getHeaders().getFirst(HEADER_NONCE);
        String expiresAt = request.getHeaders().getFirst(HEADER_EXPIRES_AT);
        String signedHeaders = request.getHeaders().getFirst(HEADER_SIGNED_HEADERS);

        if (!isFutureTimestamp(expiresAt)) {
            return deny(exchange, "expired");
        }
        if (!signedHeadersPresent(request, signedHeaders)) {
            return deny(exchange, "missing-signed-headers");
        }

        String payload = canonicalPayload(request, expiresAt, signedHeaders);
        boolean valid = detector.validateSignedRequest(nonce, payload, signature);
        if (!valid) {
            return deny(exchange, "invalid-signature-or-replay");
        }

        return chain.filter(exchange);
    }

    private String canonicalPayload(ServerHttpRequest request, String expiresAt, String signedHeaders) {
        String method = request.getMethod() == null ? "UNKNOWN" : request.getMethod().name();
        String path = request.getURI().getRawPath();
        String query = Optional.ofNullable(request.getURI().getRawQuery()).orElse("");

        return method + "\n" +
            path + "\n" +
            query + "\n" +
            "expires=" + expiresAt + "\n" +
            "signedHeaders=" + signedHeaders;
    }

    private boolean isFutureTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            long epochSeconds = Long.parseLong(value);
            return Instant.ofEpochSecond(epochSeconds).isAfter(Instant.now());
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean signedHeadersPresent(ServerHttpRequest request, String signedHeaders) {
        if (signedHeaders == null || signedHeaders.isBlank()) {
            return false;
        }

        List<String> requiredHeaders = Arrays.stream(signedHeaders.split(","))
            .map(String::trim)
            .map(header -> header.toLowerCase(Locale.ROOT))
            .filter(header -> !header.isBlank())
            .toList();

        if (requiredHeaders.isEmpty()) {
            return false;
        }

        return requiredHeaders.stream().allMatch(header -> request.getHeaders().getFirst(header) != null);
    }

    private Mono<Void> deny(ServerWebExchange exchange, String reason) {
        ServerHttpRequest request = exchange.getRequest();
        String actor = Optional.ofNullable(request.getHeaders().getFirst("X-Actor")).orElse("anonymous");
        String requestId = Optional.ofNullable(request.getHeaders().getFirst("X-Request-ID")).orElse("missing-request-id");
        String bucket = Optional.ofNullable(request.getHeaders().getFirst("X-Bucket")).orElse("unknown-bucket");
        String object = request.getURI().getRawPath();

        auditLogger.recordAccessDecision(actor, "presigned-validation", requestId, bucket, object, "DENY-" + reason);

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"presigned_request_rejected\",\"reason\":\"" + reason + "\"}")
            .getBytes(StandardCharsets.UTF_8);

        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
