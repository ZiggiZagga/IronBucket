package com.ironbucket.sentinelgear.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Component
@EnableConfigurationProperties(VaultSecretProperties.class)
public class VaultSecretResolver {

    private final VaultSecretProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public VaultSecretResolver(VaultSecretProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newHttpClient());
    }

    VaultSecretResolver(VaultSecretProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public Optional<String> resolveSecret() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        if (isBlank(properties.getToken())) {
            throw new IllegalStateException("ironbucket.security.vault.token is required when Vault integration is enabled");
        }

        String kvPath = trimSlashes(properties.getKvPath());
        String endpoint = trimTrailingSlash(properties.getUri()) + "/v1/" + kvPath;
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(resolveTimeout(properties.getTimeout()))
            .header("X-Vault-Token", properties.getToken())
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Unable to fetch secret from Vault endpoint " + endpoint, ex);
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Vault returned HTTP " + response.statusCode() + " for " + endpoint);
        }

        return extractSecret(response.body(), properties.getSecretKey());
    }

    private Optional<String> extractSecret(String json, String secretKey) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return Optional.empty();
            }

            JsonNode kv2 = data.path("data");
            JsonNode candidate = kv2.isMissingNode() || kv2.isNull() ? data.path(secretKey) : kv2.path(secretKey);
            if (candidate.isMissingNode() || candidate.isNull()) {
                return Optional.empty();
            }

            String value = candidate.asText();
            return isBlank(value) ? Optional.empty() : Optional.of(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Vault response JSON could not be parsed", ex);
        }
    }

    private static Duration resolveTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return Duration.ofSeconds(2);
        }
        return timeout;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String trimSlashes(String value) {
        if (value == null) {
            return "";
        }
        String out = value;
        while (out.startsWith("/")) {
            out = out.substring(1);
        }
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}