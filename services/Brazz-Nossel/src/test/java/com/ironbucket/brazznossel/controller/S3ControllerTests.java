package com.ironbucket.brazznossel.controller;

import com.ironbucket.brazznossel.model.NormalizedIdentity;
import com.ironbucket.brazznossel.service.S3ProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class S3ControllerTests {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        S3ProxyService stubProxyService = new S3ProxyService() {
            @Override
            public Mono<String> listBuckets(NormalizedIdentity identity) {
                return Mono.just("[]");
            }

            @Override
            public Mono<byte[]> getObject(String bucket, String key, NormalizedIdentity identity) {
                return Mono.just(new byte[0]);
            }

            @Override
            public Mono<byte[]> getObjectRange(String bucket, String key, long start, long end, NormalizedIdentity identity) {
                return Mono.just(new byte[0]);
            }

            @Override
            public Mono<String> putObject(String bucket, String key, byte[] content, NormalizedIdentity identity) {
                return Mono.just("ok");
            }

            @Override
            public Mono<Void> deleteObject(String bucket, String key, NormalizedIdentity identity) {
                return Mono.empty();
            }

            @Override
            public Mono<String> initiateMultipartUpload(String bucket, String key, NormalizedIdentity identity) {
                return Mono.just("upload-id");
            }
        };

        client = WebTestClient.bindToController(new S3Controller(stubProxyService)).build();
    }

    @Test
    void helloDevEndpointReturnsGreeting() {
        client.get()
                .uri("/s3/dev")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    // Make sure we are not getting an empty or unexpected payload
                    if (body == null || body.isBlank()) {
                        throw new AssertionError("Expected greeting body");
                    }
                    if (!body.contains("Hello brazznossel dev user")) {
                        throw new AssertionError("Unexpected greeting: " + body);
                    }
                });
    }

    @Test
    void helloAdminEndpointReturnsGreeting() {
        client.get()
                .uri("/s3/admin")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    if (body == null || body.isBlank()) {
                        throw new AssertionError("Expected greeting body");
                    }
                    if (!body.contains("Hello brazznossel admin user")) {
                        throw new AssertionError("Unexpected greeting: " + body);
                    }
                });
    }

    @Test
    void unknownPathReturnsNotFound() {
        client.get()
                .uri("/s3/does-not-exist")
                .exchange()
                .expectStatus().isNotFound();
    }
}
