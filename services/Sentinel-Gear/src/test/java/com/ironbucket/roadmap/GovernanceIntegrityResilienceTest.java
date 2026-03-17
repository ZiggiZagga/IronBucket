package com.ironbucket.roadmap;

import com.ironbucket.sentinelgear.GatewayApp;
import com.ironbucket.sentinelgear.security.TamperReplayDetector;
import com.ironbucket.sentinelgear.testing.TestJwtDecoderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(
    classes = {GatewayApp.class, TestJwtDecoderConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "ironbucket.security.presigned.enabled=true",
        "ironbucket.security.presigned.secret=test-presigned-secret"
    }
)
@DisplayName("Governance, Integrity, and Resilience Runtime Contracts")
class GovernanceIntegrityResilienceTest {

    private static final String HEADER_SIGNATURE = "X-IronBucket-Presigned-Signature";
    private static final String HEADER_NONCE = "X-IronBucket-Presigned-Nonce";
    private static final String HEADER_EXPIRES_AT = "X-IronBucket-Presigned-Expires";
    private static final String HEADER_SIGNED_HEADERS = "X-IronBucket-Presigned-SignedHeaders";

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Autowired
    private TamperReplayDetector detector;

    @ParameterizedTest(name = "Tamper/replay negative case blocked: {0}")
    @MethodSource("invalidPresignedCases")
    void invalidPresignedRequestDenied(String caseName, HttpMethod method, String path, PresignedHeaders headers) {
        webTestClient
            .method(method)
            .uri(path)
            .headers(headers::apply)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Valid presigned request reaches downstream when authenticated")
    void validPresignedRequestAccepted() {
        String path = "/tenant-a/object-accept";
        HttpMethod method = HttpMethod.GET;
        String nonce = "nonce-valid-1";
        long expires = Instant.now().plusSeconds(120).getEpochSecond();
        String signedHeaders = "x-actor,x-request-id,x-bucket";
        String payload = canonicalPayload(method, path, "", expires, signedHeaders);
        String signature = detector.sign(payload);

        webTestClient
            .method(method)
            .uri(path)
            .headers(headers -> {
                headers.setBearerAuth("roadmap-token");
                headers.add("X-Actor", "alice@tenant-a");
                headers.add("X-Request-ID", "req-valid");
                headers.add("X-Bucket", "tenant-a");
                headers.add(HEADER_NONCE, nonce);
                headers.add(HEADER_EXPIRES_AT, String.valueOf(expires));
                headers.add(HEADER_SIGNED_HEADERS, signedHeaders);
                headers.add(HEADER_SIGNATURE, signature);
            })
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));
    }

    @Test
    @DisplayName("Replay with same nonce is denied")
    void replayWithSameNonceDenied() {
        String path = "/tenant-a/object-replay";
        HttpMethod method = HttpMethod.GET;
        String nonce = "nonce-replay-1";
        long expires = Instant.now().plusSeconds(120).getEpochSecond();
        String signedHeaders = "x-actor,x-request-id,x-bucket";
        String payload = canonicalPayload(method, path, "", expires, signedHeaders);
        String signature = detector.sign(payload);

        requestWithPresignedHeaders(method, path, nonce, expires, signedHeaders, signature)
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));

        requestWithPresignedHeaders(method, path, nonce, expires, signedHeaders, signature)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @ParameterizedTest(name = "Signature verification contract case {0}")
    @MethodSource("signatureVerificationCases")
    void signatureVerificationContracts(String payload, String providedSignature, boolean expectedValid) {
        String nonce = "nonce-contract-" + Math.abs(payload.hashCode());
        boolean valid = detector.validateSignedRequest(nonce, payload, providedSignature);
        assertEquals(expectedValid, valid);
    }

    @Test
    @DisplayName("Signed header requirements enforced")
    void signedHeaderRequirementsEnforced() {
        String path = "/tenant-a/object-signed-header";
        HttpMethod method = HttpMethod.GET;
        String nonce = "nonce-header-1";
        long expires = Instant.now().plusSeconds(120).getEpochSecond();
        String signedHeaders = "x-actor,x-request-id,x-bucket,x-extra-required";
        String payload = canonicalPayload(method, path, "", expires, signedHeaders);
        String signature = detector.sign(payload);

        webTestClient
            .method(method)
            .uri(path)
            .headers(headers -> {
                headers.setBearerAuth("roadmap-token");
                headers.add("X-Actor", "alice@tenant-a");
                headers.add("X-Request-ID", "req-header");
                headers.add("X-Bucket", "tenant-a");
                headers.add(HEADER_NONCE, nonce);
                headers.add(HEADER_EXPIRES_AT, String.valueOf(expires));
                headers.add(HEADER_SIGNED_HEADERS, signedHeaders);
                headers.add(HEADER_SIGNATURE, signature);
            })
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private WebTestClient.RequestHeadersSpec<?> requestWithPresignedHeaders(
        HttpMethod method,
        String path,
        String nonce,
        long expires,
        String signedHeaders,
        String signature
    ) {
        return webTestClient
            .method(method)
            .uri(path)
            .headers(headers -> {
                headers.setBearerAuth("roadmap-token");
                headers.add("X-Actor", "alice@tenant-a");
                headers.add("X-Request-ID", "req-replay");
                headers.add("X-Bucket", "tenant-a");
                headers.add(HEADER_NONCE, nonce);
                headers.add(HEADER_EXPIRES_AT, String.valueOf(expires));
                headers.add(HEADER_SIGNED_HEADERS, signedHeaders);
                headers.add(HEADER_SIGNATURE, signature);
            });
    }

    private static Stream<Arguments> invalidPresignedCases() {
        return Stream.of(
            Arguments.of("missing-expiry", HttpMethod.GET, "/tenant-a/object-1", PresignedHeaders.missingExpiry()),
            Arguments.of("expired", HttpMethod.GET, "/tenant-a/object-2", PresignedHeaders.expired()),
            Arguments.of("missing-signed-headers", HttpMethod.GET, "/tenant-a/object-3", PresignedHeaders.missingSignedHeaders()),
            Arguments.of("missing-required-header", HttpMethod.GET, "/tenant-a/object-4", PresignedHeaders.missingRequiredHeader()),
            Arguments.of("invalid-signature", HttpMethod.GET, "/tenant-a/object-5", PresignedHeaders.invalidSignature()),
            Arguments.of("blank-nonce", HttpMethod.GET, "/tenant-a/object-6", PresignedHeaders.blankNonce())
        );
    }

    private static Stream<Arguments> signatureVerificationCases() {
        TamperReplayDetector local = new TamperReplayDetector("test-presigned-secret", java.time.Duration.ofMinutes(5), java.time.Clock.systemUTC());
        return Stream.of(
            Arguments.of("payload-a", local.sign("payload-a"), true),
            Arguments.of("payload-b", local.sign("payload-b"), true),
            Arguments.of("payload-c", "bad-signature", false),
            Arguments.of("payload-d", local.sign("payload-a"), false),
            Arguments.of("payload-e", "", false),
            Arguments.of("payload-f", "   ", false)
        );
    }

    private static String canonicalPayload(HttpMethod method, String path, String query, long expires, String signedHeaders) {
        String normalizedQuery = query == null ? "" : query;
        return method.name() + "\n"
            + path + "\n"
            + normalizedQuery + "\n"
            + "expires=" + expires + "\n"
            + "signedHeaders=" + signedHeaders;
    }

    private interface PresignedHeaders {
        void apply(HttpHeaders headers);

        static PresignedHeaders missingExpiry() {
            return headers -> {
                headers.add("X-Actor", "alice");
                headers.add("X-Request-ID", "req-1");
                headers.add("X-Bucket", "tenant-a");
                headers.add(HEADER_NONCE, "nonce-1");
                headers.add(HEADER_SIGNED_HEADERS, "x-actor,x-request-id,x-bucket");
                headers.add(HEADER_SIGNATURE, "sig-1");
            };
        }

        static PresignedHeaders expired() {
            return headers -> {
                headers.add("X-Actor", "alice");
                headers.add("X-Request-ID", "req-2");
                headers.add("X-Bucket", "tenant-a");
                headers.add(HEADER_NONCE, "nonce-2");
                headers.add(HEADER_EXPIRES_AT, String.valueOf(Instant.now().minusSeconds(60).getEpochSecond()));
                headers.add(HEADER_SIGNED_HEADERS, "x-actor,x-request-id,x-bucket");
                headers.add(HEADER_SIGNATURE, "sig-2");
            };
        }

        static PresignedHeaders missingSignedHeaders() {
            return headers -> {
                headers.add("X-Actor", "alice");
                headers.add("X-Request-ID", "req-3");
                headers.add("X-Bucket", "tenant-a");
                headers.add(HEADER_NONCE, "nonce-3");
                headers.add(HEADER_EXPIRES_AT, String.valueOf(Instant.now().plusSeconds(120).getEpochSecond()));
                headers.add(HEADER_SIGNATURE, "sig-3");
            };
        }

        static PresignedHeaders missingRequiredHeader() {
            return headers -> {
                headers.add("X-Actor", "alice");
                headers.add("X-Bucket", "tenant-a");
                headers.add(HEADER_NONCE, "nonce-4");
                headers.add(HEADER_EXPIRES_AT, String.valueOf(Instant.now().plusSeconds(120).getEpochSecond()));
                headers.add(HEADER_SIGNED_HEADERS, "x-actor,x-request-id,x-bucket");
                headers.add(HEADER_SIGNATURE, "sig-4");
            };
        }

        static PresignedHeaders invalidSignature() {
            return headers -> {
                headers.add("X-Actor", "alice");
                headers.add("X-Request-ID", "req-5");
                headers.add("X-Bucket", "tenant-a");
                headers.add(HEADER_NONCE, "nonce-5");
                headers.add(HEADER_EXPIRES_AT, String.valueOf(Instant.now().plusSeconds(120).getEpochSecond()));
                headers.add(HEADER_SIGNED_HEADERS, "x-actor,x-request-id,x-bucket");
                headers.add(HEADER_SIGNATURE, "invalid-signature");
            };
        }

        static PresignedHeaders blankNonce() {
            return headers -> {
                headers.add("X-Actor", "alice");
                headers.add("X-Request-ID", "req-6");
                headers.add("X-Bucket", "tenant-a");
                headers.add(HEADER_EXPIRES_AT, String.valueOf(Instant.now().plusSeconds(120).getEpochSecond()));
                headers.add(HEADER_SIGNED_HEADERS, "x-actor,x-request-id,x-bucket");
                headers.add(HEADER_SIGNATURE, "sig-6");
            };
        }
    }
}
