package com.ironbucket.roadmap;

import com.ironbucket.sentinelgear.GatewayApp;
import com.ironbucket.sentinelgear.testing.TestJwtDecoderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
@DisplayName("GraphQL Gateway Runtime Contracts")
class GraphQLFeaturesTest {

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("GraphQL endpoint route is reachable")
    void graphqlRouteRegistered() {
        webTestClient
            .get()
            .uri("/graphql")
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));
    }

    @ParameterizedTest(name = "GraphQL endpoint public path: {0} {1}")
    @MethodSource("graphqlPublicPaths")
    void graphqlEndpointsArePublic(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));
    }

    @ParameterizedTest(name = "GraphQL endpoint reachable with JWT: {0} {1}")
    @MethodSource("graphqlPublicPaths")
    void graphqlEndpointsReachRoutingLayerWithJwt(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .headers(headers -> headers.setBearerAuth("roadmap-token"))
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));
    }

    @ParameterizedTest(name = "Non-GraphQL admin path must be protected: {0} {1}")
    @MethodSource("nonGraphqlProtectedPaths")
    void nonGraphqlAdminPathsProtected(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .exchange()
            .expectStatus()
            .value(status -> assertTrue(status == 401 || status == 403));
    }

    @ParameterizedTest(name = "Authenticated non-GraphQL admin path passes security: {0} {1}")
    @MethodSource("nonGraphqlProtectedPaths")
    void authenticatedNonGraphqlPathsPassSecurity(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .headers(headers -> headers.setBearerAuth("roadmap-token"))
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));
    }

    private static Stream<Arguments> graphqlPublicPaths() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/graphql"),
            Arguments.of(HttpMethod.POST, "/graphql"),
            Arguments.of(HttpMethod.GET, "/graphql/schema"),
            Arguments.of(HttpMethod.POST, "/graphql/query"),
            Arguments.of(HttpMethod.OPTIONS, "/graphql"),
            Arguments.of(HttpMethod.HEAD, "/graphql")
        );
    }

    private static Stream<Arguments> nonGraphqlProtectedPaths() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/policies"),
            Arguments.of(HttpMethod.POST, "/policies"),
            Arguments.of(HttpMethod.PUT, "/policies/123"),
            Arguments.of(HttpMethod.DELETE, "/policies/123"),
            Arguments.of(HttpMethod.GET, "/tenants"),
            Arguments.of(HttpMethod.GET, "/audit/events")
        );
    }
}
