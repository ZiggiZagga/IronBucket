package com.ironbucket.sentinelgear.integration;

import com.ironbucket.sentinelgear.GatewayApp;
import com.ironbucket.sentinelgear.testing.TestJwtDecoderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.netty.http.client.HttpClient;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
    classes = {GatewayApp.class, TestJwtDecoderConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "server.ssl.enabled=true",
        "server.ssl.key-store=classpath:certs/test-keystore.p12",
        "server.ssl.key-store-password=changeit",
        "server.ssl.key-store-type=PKCS12",
        "server.ssl.key-alias=sentinel-test",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
    }
)
@DisplayName("Sentinel-Gear TLS End-to-End")
class SentinelGearTlsE2ETest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("HTTPS health endpoint responds over TLS")
    void httpsHealthEndpointResponds() throws Exception {
        io.netty.handler.ssl.SslContext sslContext = io.netty.handler.ssl.SslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build();

        HttpClient secureClient = HttpClient.create().secure(spec -> spec.sslContext(
            sslContext
        ));

        WebClient httpsClient = WebClient.builder()
            .baseUrl("https://localhost:" + port)
            .clientConnector(new ReactorClientHttpConnector(secureClient))
            .build();

        Integer status = httpsClient.get()
            .uri("/")
            .exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> response.statusCode().value()))
            .block(Duration.ofSeconds(5));

        assertTrue(status != null && status < 500);
    }

    @Test
    @DisplayName("Plain HTTP request to TLS port is rejected")
    void plainHttpRejectedOnTlsPort() {
        WebClient httpClient = WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();

        assertThrows(WebClientRequestException.class, () -> httpClient.get()
            .uri("http://localhost:" + port + "/actuator/health")
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(5)));
    }

}