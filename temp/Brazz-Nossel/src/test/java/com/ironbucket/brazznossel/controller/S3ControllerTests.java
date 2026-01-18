package com.ironbucket.brazznossel.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class S3ControllerTests {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        // Use no-arg constructor which doesn't require S3ProxyService
        // These tests only test /dev and /admin endpoints which don't use S3ProxyService
        client = WebTestClient.bindToController(new S3Controller()).build();
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
