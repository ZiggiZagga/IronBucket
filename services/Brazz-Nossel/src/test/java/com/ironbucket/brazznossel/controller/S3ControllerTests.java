package com.ironbucket.brazznossel.controller;

import com.ironbucket.brazznossel.model.NormalizedIdentity;
import com.ironbucket.brazznossel.service.S3ProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import software.amazon.awssdk.services.s3.model.CompletedPart;

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
            public Mono<String> createBucket(String bucket, NormalizedIdentity identity) {
                return Mono.just("ok");
            }

            @Override
            public Mono<Void> deleteBucket(String bucket, NormalizedIdentity identity) {
                return Mono.empty();
            }

            @Override
            public Mono<String> listObjects(String bucket, NormalizedIdentity identity) {
                return Mono.just("[]");
            }

            @Override
            public Mono<byte[]> getObject(String bucket, String key, NormalizedIdentity identity) {
                return Mono.just(new byte[0]);
            }

            @Override
            public Mono<String> headObject(String bucket, String key, NormalizedIdentity identity) {
                return Mono.just("{}");
            }

            @Override
            public Mono<String> headBucket(String bucket, NormalizedIdentity identity) {
                return Mono.just("{}");
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
            public Mono<Void> deleteObjectVersion(String bucket, String key, String versionId, NormalizedIdentity identity) {
                return Mono.empty();
            }

            @Override
            public Mono<byte[]> getObjectVersion(String bucket, String key, String versionId, NormalizedIdentity identity) {
                return Mono.just(new byte[0]);
            }

            @Override
            public Mono<String> listObjectVersions(String bucket, NormalizedIdentity identity) {
                return Mono.just("[]");
            }

            @Override
            public Mono<String> initiateMultipartUpload(String bucket, String key, NormalizedIdentity identity) {
                return Mono.just("upload-id");
            }

            @Override
            public Mono<String> uploadPart(String bucket, String key, String uploadId, int partNumber, byte[] content, NormalizedIdentity identity) {
                return Mono.just("etag");
            }

            @Override
            public Mono<String> completeMultipartUpload(String bucket, String key, String uploadId, List<CompletedPart> parts, NormalizedIdentity identity) {
                return Mono.just("etag");
            }

            @Override
            public Mono<Void> abortMultipartUpload(String bucket, String key, String uploadId, NormalizedIdentity identity) {
                return Mono.empty();
            }

            @Override
            public Mono<String> listMultipartUploads(String bucket, NormalizedIdentity identity) {
                return Mono.just("[]");
            }

            @Override
            public Mono<String> listParts(String bucket, String key, String uploadId, NormalizedIdentity identity) {
                return Mono.just("[]");
            }

            @Override
            public Mono<String> getBucketVersioning(String bucket, NormalizedIdentity identity) {
                return Mono.just("Enabled");
            }

            @Override
            public Mono<String> putBucketVersioning(String bucket, String status, NormalizedIdentity identity) {
                return Mono.just("ok");
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
