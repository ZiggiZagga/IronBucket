package com.ironbucket.brazznossel.controller;

import com.ironbucket.brazznossel.model.NormalizedIdentity;
import com.ironbucket.brazznossel.service.S3ProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import software.amazon.awssdk.services.s3.model.CompletedPart;

class S3ControllerTests {

    private WebTestClient client;
    private S3Controller controller;

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

            @Override
            public Mono<String> putObjectTagging(String bucket, String key, Map<String, String> tags, NormalizedIdentity identity) {
                return Mono.just("ok");
            }

            @Override
            public Mono<Map<String, String>> getObjectTagging(String bucket, String key, NormalizedIdentity identity) {
                return Mono.just(Map.of());
            }

            @Override
            public Mono<Void> deleteObjectTagging(String bucket, String key, NormalizedIdentity identity) {
                return Mono.empty();
            }

            @Override
            public Mono<String> getBucketPolicy(String bucket, NormalizedIdentity identity) {
                return Mono.just("{}");
            }

            @Override
            public Mono<String> putBucketPolicy(String bucket, String policyJson, NormalizedIdentity identity) {
                return Mono.just("ok");
            }

            @Override
            public Mono<Void> deleteBucketPolicy(String bucket, NormalizedIdentity identity) {
                return Mono.empty();
            }

            @Override
            public Mono<String> getObjectAcl(String bucket, String key, NormalizedIdentity identity) {
                return Mono.just("READ");
            }

            @Override
            public Mono<String> putObjectAcl(String bucket, String key, String acl, NormalizedIdentity identity) {
                return Mono.just(acl);
            }

            @Override
            public Mono<String> getBucketAcl(String bucket, NormalizedIdentity identity) {
                return Mono.just("READ");
            }

            @Override
            public Mono<String> putBucketAcl(String bucket, String acl, NormalizedIdentity identity) {
                return Mono.just(acl);
            }

            @Override
            public Mono<String> copyObject(String sourceBucket, String sourceKey, String destinationBucket, String destinationKey, NormalizedIdentity identity) {
                return Mono.just("etag-copy");
            }

            @Override
            public Mono<String> getBucketLocation(String bucket, NormalizedIdentity identity) {
                return Mono.just("us-east-1");
            }
        };

        controller = new S3Controller(stubProxyService);
        client = WebTestClient.bindToController(controller).build();
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

    @Test
    void extendedS3OperationsAreMappedToServiceMethods() {
        Jwt jwt = jwtPrincipal();

        StepVerifier.create(controller.headBucket("tenant-a-files", jwt))
            .expectNext("{}")
            .verifyComplete();

        StepVerifier.create(controller.headObject("tenant-a-files", "a.txt", jwt))
            .expectNext("{}")
            .verifyComplete();

        StepVerifier.create(controller.getObjectRange("tenant-a-files", "a.txt", 0, 4, jwt))
            .expectNextCount(1)
            .verifyComplete();

        StepVerifier.create(controller.getBucketVersioning("tenant-a-files", jwt))
            .expectNext("Enabled")
            .verifyComplete();

        StepVerifier.create(controller.putBucketVersioning("tenant-a-files", "Enabled", jwt))
            .expectNext("ok")
            .verifyComplete();

        StepVerifier.create(controller.listObjectVersions("tenant-a-files", jwt))
            .expectNext("[]")
            .verifyComplete();
    }

    @Test
    void multipartAndObjectVersionOperationsCompleteWithValidPrincipal() {
        Jwt jwt = jwtPrincipal();
        List<CompletedPart> completedParts = List.of(
            CompletedPart.builder().partNumber(1).eTag("etag-1").build()
        );

        StepVerifier.create(controller.initiateMultipartUpload("tenant-a-files", "big.bin", jwt))
            .expectNext("upload-id")
            .verifyComplete();

        StepVerifier.create(controller.uploadPart("tenant-a-files", "big.bin", "upload-id", 1, "part".getBytes(), jwt))
            .expectNext("etag")
            .verifyComplete();

        StepVerifier.create(controller.completeMultipartUpload("tenant-a-files", "big.bin", "upload-id", completedParts, jwt))
            .expectNext("etag")
            .verifyComplete();

        StepVerifier.create(controller.abortMultipartUpload("tenant-a-files", "big.bin", "upload-id", jwt))
            .verifyComplete();

        StepVerifier.create(controller.listMultipartUploads("tenant-a-files", jwt))
            .expectNext("[]")
            .verifyComplete();

        StepVerifier.create(controller.listParts("tenant-a-files", "big.bin", "upload-id", jwt))
            .expectNext("[]")
            .verifyComplete();

        StepVerifier.create(controller.getObjectVersion("tenant-a-files", "big.bin", "v1", jwt))
            .expectNextCount(1)
            .verifyComplete();

        StepVerifier.create(controller.deleteObjectVersion("tenant-a-files", "big.bin", "v1", jwt))
            .verifyComplete();
    }

    @Test
    void tenantExtractionUsesTenantIdAliasesAndFallbacks() {
        AtomicReference<NormalizedIdentity> capturedIdentity = new AtomicReference<>();
        S3Controller tenantAwareController = new S3Controller(new S3ProxyService() {
            @Override
            public Mono<String> listBuckets(NormalizedIdentity identity) {
                capturedIdentity.set(identity);
                return Mono.just("[]");
            }

            @Override public Mono<String> createBucket(String bucket, NormalizedIdentity identity) { return Mono.just("ok"); }
            @Override public Mono<Void> deleteBucket(String bucket, NormalizedIdentity identity) { return Mono.empty(); }
            @Override public Mono<String> listObjects(String bucket, NormalizedIdentity identity) { return Mono.just("[]"); }
            @Override public Mono<byte[]> getObject(String bucket, String key, NormalizedIdentity identity) { return Mono.just(new byte[0]); }
            @Override public Mono<String> headObject(String bucket, String key, NormalizedIdentity identity) { return Mono.just("{}"); }
            @Override public Mono<String> headBucket(String bucket, NormalizedIdentity identity) { return Mono.just("{}"); }
            @Override public Mono<byte[]> getObjectRange(String bucket, String key, long start, long end, NormalizedIdentity identity) { return Mono.just(new byte[0]); }
            @Override public Mono<String> putObject(String bucket, String key, byte[] content, NormalizedIdentity identity) { return Mono.just("ok"); }
            @Override public Mono<Void> deleteObject(String bucket, String key, NormalizedIdentity identity) { return Mono.empty(); }
            @Override public Mono<Void> deleteObjectVersion(String bucket, String key, String versionId, NormalizedIdentity identity) { return Mono.empty(); }
            @Override public Mono<byte[]> getObjectVersion(String bucket, String key, String versionId, NormalizedIdentity identity) { return Mono.just(new byte[0]); }
            @Override public Mono<String> listObjectVersions(String bucket, NormalizedIdentity identity) { return Mono.just("[]"); }
            @Override public Mono<String> initiateMultipartUpload(String bucket, String key, NormalizedIdentity identity) { return Mono.just("upload-id"); }
            @Override public Mono<String> uploadPart(String bucket, String key, String uploadId, int partNumber, byte[] content, NormalizedIdentity identity) { return Mono.just("etag"); }
            @Override public Mono<String> completeMultipartUpload(String bucket, String key, String uploadId, List<CompletedPart> parts, NormalizedIdentity identity) { return Mono.just("etag"); }
            @Override public Mono<Void> abortMultipartUpload(String bucket, String key, String uploadId, NormalizedIdentity identity) { return Mono.empty(); }
            @Override public Mono<String> listMultipartUploads(String bucket, NormalizedIdentity identity) { return Mono.just("[]"); }
            @Override public Mono<String> listParts(String bucket, String key, String uploadId, NormalizedIdentity identity) { return Mono.just("[]"); }
            @Override public Mono<String> getBucketVersioning(String bucket, NormalizedIdentity identity) { return Mono.just("Enabled"); }
            @Override public Mono<String> putBucketVersioning(String bucket, String status, NormalizedIdentity identity) { return Mono.just("ok"); }
            @Override public Mono<String> putObjectTagging(String bucket, String key, Map<String, String> tags, NormalizedIdentity identity) { return Mono.just("ok"); }
            @Override public Mono<Map<String, String>> getObjectTagging(String bucket, String key, NormalizedIdentity identity) { return Mono.just(Map.of()); }
            @Override public Mono<Void> deleteObjectTagging(String bucket, String key, NormalizedIdentity identity) { return Mono.empty(); }
            @Override public Mono<String> getBucketPolicy(String bucket, NormalizedIdentity identity) { return Mono.just("{}"); }
            @Override public Mono<String> putBucketPolicy(String bucket, String policyJson, NormalizedIdentity identity) { return Mono.just("ok"); }
            @Override public Mono<Void> deleteBucketPolicy(String bucket, NormalizedIdentity identity) { return Mono.empty(); }
            @Override public Mono<String> getObjectAcl(String bucket, String key, NormalizedIdentity identity) { return Mono.just("READ"); }
            @Override public Mono<String> putObjectAcl(String bucket, String key, String acl, NormalizedIdentity identity) { return Mono.just(acl); }
            @Override public Mono<String> getBucketAcl(String bucket, NormalizedIdentity identity) { return Mono.just("READ"); }
            @Override public Mono<String> putBucketAcl(String bucket, String acl, NormalizedIdentity identity) { return Mono.just(acl); }
            @Override public Mono<String> copyObject(String sourceBucket, String sourceKey, String destinationBucket, String destinationKey, NormalizedIdentity identity) { return Mono.just("etag-copy"); }
            @Override public Mono<String> getBucketLocation(String bucket, NormalizedIdentity identity) { return Mono.just("us-east-1"); }
        });

        StepVerifier.create(tenantAwareController.listBuckets(jwtWithClaims(Map.of("tenant_id", "tenant-snake"))))
            .expectNext("[]")
            .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals("tenant-snake", capturedIdentity.get().getTenantId());

        StepVerifier.create(tenantAwareController.listBuckets(jwtWithClaims(Map.of("tenantId", "tenant-camel"))))
            .expectNext("[]")
            .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals("tenant-camel", capturedIdentity.get().getTenantId());

        StepVerifier.create(tenantAwareController.listBuckets(jwtWithClaims(Map.of("organization", "org-fallback"))))
            .expectNext("[]")
            .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals("org-fallback", capturedIdentity.get().getTenantId());

        StepVerifier.create(tenantAwareController.listBuckets(jwtWithClaims(Map.of("groups", List.of("org:group-org")))))
            .expectNext("[]")
            .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals("group-org", capturedIdentity.get().getTenantId());

        StepVerifier.create(tenantAwareController.listBuckets(jwtWithClaims(Map.of())))
            .expectNext("[]")
            .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals("default", capturedIdentity.get().getTenantId());
    }

    @Test
    void missingPrincipalReturnsIllegalState() {
        StepVerifier.create(controller.createBucket("tenant-a-files", null))
            .expectError(IllegalStateException.class)
            .verify();
    }

    private Jwt jwtPrincipal() {
        return Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("user-123")
            .claim("preferred_username", "alice")
            .claim("tenant", "tenant-a")
            .claim("roles", List.of("dev"))
            .build();
    }

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("user-123")
            .claim("preferred_username", "alice")
            .claim("roles", List.of("dev"));

        claims.forEach(builder::claim);
        return builder.build();
    }
}
