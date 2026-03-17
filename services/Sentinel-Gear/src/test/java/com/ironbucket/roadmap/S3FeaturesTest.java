package com.ironbucket.roadmap;

import com.ironbucket.sentinelgear.GatewayApp;
import com.ironbucket.sentinelgear.testing.TestJwtDecoderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    classes = {GatewayApp.class, TestJwtDecoderConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
    }
)
@DisplayName("S3 Data Plane Runtime Contracts")
class S3FeaturesTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @ParameterizedTest(name = "Unauthenticated S3 path blocked: {0} {1}")
    @MethodSource("s3Paths")
    void unauthenticatedRequestsBlocked(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .exchange()
            .expectStatus()
            .value(status -> assertTrue(status == 401 || status == 403));
    }

    @ParameterizedTest(name = "Authenticated S3 path passes auth gate: {0} {1}")
    @MethodSource("s3Paths")
    void authenticatedRequestsReachRoutingLayer(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .headers(headers -> headers.setBearerAuth("roadmap-token"))
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));
    }

    @ParameterizedTest(name = "Path-style bucket contract protected: {0} {1}")
    @MethodSource("pathStyleBucketPaths")
    void pathStyleBucketContractsProtected(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .exchange()
            .expectStatus()
            .value(status -> assertTrue(status == 401 || status == 403));
    }

    @ParameterizedTest(name = "Authenticated path-style bucket contract routes: {0} {1}")
    @MethodSource("pathStyleBucketPaths")
    void authenticatedPathStyleBucketContractsRoute(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .headers(headers -> headers.setBearerAuth("roadmap-token"))
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));
    }

    private static Stream<Arguments> s3Paths() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/tenant-a-bucket/object-1"),
            Arguments.of(HttpMethod.PUT, "/tenant-a-bucket/object-2"),
            Arguments.of(HttpMethod.DELETE, "/tenant-a-bucket/object-3"),
            Arguments.of(HttpMethod.HEAD, "/tenant-a-bucket/object-4"),
            Arguments.of(HttpMethod.POST, "/tenant-a-bucket?uploads"),
            Arguments.of(HttpMethod.PUT, "/tenant-a-bucket/object-5?partNumber=1&uploadId=u1"),
            Arguments.of(HttpMethod.POST, "/tenant-a-bucket/object-6?uploadId=u2"),
            Arguments.of(HttpMethod.DELETE, "/tenant-a-bucket/object-7?uploadId=u3"),
            Arguments.of(HttpMethod.GET, "/tenant-a-bucket?list-type=2"),
            Arguments.of(HttpMethod.GET, "/tenant-a-bucket?versions")
        );
    }

    private static Stream<Arguments> pathStyleBucketPaths() {
        return Stream.of(
            Arguments.of(HttpMethod.PUT, "/tenant-z-bucket"),
            Arguments.of(HttpMethod.HEAD, "/tenant-z-bucket"),
            Arguments.of(HttpMethod.GET, "/tenant-z-bucket"),
            Arguments.of(HttpMethod.PUT, "/tenant-z-bucket?versioning"),
            Arguments.of(HttpMethod.GET, "/tenant-z-bucket?versioning"),
            Arguments.of(HttpMethod.PUT, "/tenant-z-bucket?lifecycle"),
            Arguments.of(HttpMethod.GET, "/tenant-z-bucket?lifecycle"),
            Arguments.of(HttpMethod.PUT, "/tenant-z-bucket?tagging"),
            Arguments.of(HttpMethod.GET, "/tenant-z-bucket?tagging"),
            Arguments.of(HttpMethod.DELETE, "/tenant-z-bucket?tagging")
        );
    }
}
