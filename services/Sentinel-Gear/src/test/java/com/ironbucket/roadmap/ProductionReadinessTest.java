package com.ironbucket.roadmap;

import com.ironbucket.sentinelgear.config.PresignedSecurityConfig;
import com.ironbucket.sentinelgear.config.VaultSecretResolver;
import com.ironbucket.sentinelgear.filter.PresignedRequestSecurityFilter;
import com.ironbucket.sentinelgear.filter.RequestCorrelationFilter;
import com.ironbucket.sentinelgear.security.TamperReplayDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
    }
)
@DisplayName("Production Readiness Runtime Contracts")
class ProductionReadinessTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @ParameterizedTest(name = "Bean present: {0}")
    @MethodSource("requiredBeans")
    void requiredRuntimeBeansPresent(String name, Class<?> type) {
        Object bean = applicationContext.getBean(type);
        assertNotNull(bean, () -> "Required bean missing: " + name);
    }

    @ParameterizedTest(name = "Property present: {0}")
    @MethodSource("requiredProperties")
    void requiredSecurityPropertiesBound(String property) {
        assertNotNull(environment.getProperty(property), () -> "Property not bound: " + property);
    }

    @ParameterizedTest(name = "Public endpoint not auth-blocked: {0} {1}")
    @MethodSource("publicEndpoints")
    void publicEndpointsRemainReachable(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));
    }

    @ParameterizedTest(name = "Protected endpoint requires auth: {0} {1}")
    @MethodSource("protectedEndpoints")
    void protectedEndpointsRequireAuthentication(HttpMethod method, String path) {
        webTestClient
            .method(method)
            .uri(path)
            .exchange()
            .expectStatus()
            .value(status -> assertTrue(status == 401 || status == 403));
    }

    @ParameterizedTest(name = "Authenticated request reaches backend layer: {0} {1}")
    @MethodSource("protectedEndpoints")
    void authenticatedRequestsPassSecurityGate(HttpMethod method, String path) {
        webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt())
            .method(method)
            .uri(path)
            .exchange()
            .expectStatus()
            .value(status -> assertFalse(status == 401 || status == 403));
    }

    @Test
    @DisplayName("Vault dependency classes are on classpath")
    void vaultClassesOnClasspath() throws Exception {
        assertNotNull(Class.forName("org.springframework.vault.core.VaultTemplate"));
        assertNotNull(Class.forName("org.springframework.cloud.vault.config.VaultConfigDataLoader"));
    }

    @Test
    @DisplayName("TLS config is runtime-bindable")
    void tlsConfigRuntimeBindable() {
        assertNotNull(environment.getProperty("server.ssl.enabled"));
        assertNotNull(environment.getProperty("server.ssl.key-store-type"));
    }

    private static Stream<Arguments> requiredBeans() {
        return Stream.of(
            Arguments.of("PresignedSecurityConfig", PresignedSecurityConfig.class),
            Arguments.of("VaultSecretResolver", VaultSecretResolver.class),
            Arguments.of("TamperReplayDetector", TamperReplayDetector.class),
            Arguments.of("RequestCorrelationFilter", RequestCorrelationFilter.class),
            Arguments.of("PresignedRequestSecurityFilter", PresignedRequestSecurityFilter.class)
        );
    }

    private static Stream<String> requiredProperties() {
        return Stream.of(
            "server.ssl.enabled",
            "server.ssl.key-store-type",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "ironbucket.security.presigned.enabled",
            "ironbucket.security.presigned.nonce-ttl",
            "spring.cloud.vault.enabled",
            "spring.cloud.vault.kv.backend",
            "spring.cloud.vault.kv.default-context",
            "ironbucket.security.vault.enabled",
            "ironbucket.security.vault.kv-path",
            "ironbucket.security.vault.secret-key",
            "ironbucket.security.vault.timeout"
        );
    }

    private static Stream<Arguments> publicEndpoints() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/actuator/health-check"),
            Arguments.of(HttpMethod.GET, "/actuator/prometheus"),
            Arguments.of(HttpMethod.GET, "/graphql"),
            Arguments.of(HttpMethod.POST, "/graphql")
        );
    }

    private static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/bucket-a/object-a"),
            Arguments.of(HttpMethod.PUT, "/bucket-a/object-a"),
            Arguments.of(HttpMethod.DELETE, "/bucket-a/object-a"),
            Arguments.of(HttpMethod.POST, "/bucket-a"),
            Arguments.of(HttpMethod.GET, "/admin/policies"),
            Arguments.of(HttpMethod.GET, "/tenant/audit/logs")
        );
    }
}
