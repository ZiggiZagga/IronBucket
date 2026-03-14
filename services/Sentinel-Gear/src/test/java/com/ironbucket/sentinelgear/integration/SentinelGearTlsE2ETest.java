package com.ironbucket.sentinelgear.integration;

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

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "server.ssl.enabled=true",
        "server.ssl.key-store=classpath:certs/test-keystore.p12",
        "server.ssl.key-store-password=changeit",
        "server.ssl.key-store-type=PKCS12",
        "server.ssl.key-alias=sentinel-test",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "management.server.port=0"
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

        httpsClient.get()
            .uri("/actuator/health-check")
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Plain HTTP request to TLS port is rejected")
    void plainHttpRejectedOnTlsPort() {
        WebClient httpClient = WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();

        assertThrows(WebClientRequestException.class, () -> httpClient.get()
            .uri("http://localhost:" + port + "/actuator/health-check")
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(5)));
    }

}