package com.ironbucket.sentinelgear.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultSecretResolverTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void resolvesSecretFromKv2Payload() throws Exception {
        startServer(200, "{\"data\":{\"data\":{\"presignedSecret\":\"kv2-secret\"}}}");

        VaultSecretProperties properties = baseProperties();
        VaultSecretResolver resolver = new VaultSecretResolver(properties, new com.fasterxml.jackson.databind.ObjectMapper(), HttpClient.newHttpClient());

        Optional<String> secret = resolver.resolveSecret();

        assertTrue(secret.isPresent());
        assertEquals("kv2-secret", secret.get());
    }

    @Test
    void resolvesSecretFromKv1Payload() throws Exception {
        startServer(200, "{\"data\":{\"presignedSecret\":\"kv1-secret\"}}");

        VaultSecretProperties properties = baseProperties();
        VaultSecretResolver resolver = new VaultSecretResolver(properties, new com.fasterxml.jackson.databind.ObjectMapper(), HttpClient.newHttpClient());

        Optional<String> secret = resolver.resolveSecret();

        assertTrue(secret.isPresent());
        assertEquals("kv1-secret", secret.get());
    }

    @Test
    void returnsEmptyWhenSecretKeyMissing() throws Exception {
        startServer(200, "{\"data\":{\"data\":{\"other\":\"value\"}}}");

        VaultSecretProperties properties = baseProperties();
        VaultSecretResolver resolver = new VaultSecretResolver(properties, new com.fasterxml.jackson.databind.ObjectMapper(), HttpClient.newHttpClient());

        Optional<String> secret = resolver.resolveSecret();

        assertTrue(secret.isEmpty());
    }

    @Test
    void failsWhenVaultReturnsErrorStatus() throws Exception {
        startServer(500, "{\"errors\":[\"boom\"]}");

        VaultSecretProperties properties = baseProperties();
        VaultSecretResolver resolver = new VaultSecretResolver(properties, new com.fasterxml.jackson.databind.ObjectMapper(), HttpClient.newHttpClient());

        assertThrows(IllegalStateException.class, resolver::resolveSecret);
    }

    @Test
    void failsWhenEnabledWithoutToken() {
        VaultSecretProperties properties = new VaultSecretProperties();
        properties.setEnabled(true);
        properties.setUri("http://127.0.0.1:8200");
        properties.setKvPath("secret/data/ironbucket/sentinel-gear");
        properties.setSecretKey("presignedSecret");
        properties.setToken(" ");

        VaultSecretResolver resolver = new VaultSecretResolver(properties, new com.fasterxml.jackson.databind.ObjectMapper(), HttpClient.newHttpClient());

        assertThrows(IllegalStateException.class, resolver::resolveSecret);
    }

    private VaultSecretProperties baseProperties() {
        VaultSecretProperties properties = new VaultSecretProperties();
        properties.setEnabled(true);
        properties.setUri("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("dev-token");
        properties.setKvPath("secret/data/ironbucket/sentinel-gear");
        properties.setSecretKey("presignedSecret");
        return properties;
    }

    private void startServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/secret/data/ironbucket/sentinel-gear", new StaticResponseHandler(statusCode, body));
        server.start();
    }

    private static class StaticResponseHandler implements HttpHandler {
        private final int statusCode;
        private final byte[] body;

        private StaticResponseHandler(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}