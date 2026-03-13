package com.ironbucket.graphiteforge.service;

import com.ironbucket.graphiteforge.exception.BucketNotFoundException;
import com.ironbucket.graphiteforge.exception.IronBucketServiceException;
import com.ironbucket.graphiteforge.model.ProviderRoutingDecision;
import com.ironbucket.graphiteforge.model.S3Bucket;
import com.ironbucket.graphiteforge.model.S3Object;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IronBucketS3Service {

    private static final String ROUTED_PROVIDER = "CLAIMSPINDEL";
    private static final String ROUTED_REASON = "sentinel-gear-gateway;claimspindel-policy-route";
    private static final Pattern CREATED_PATTERN = Pattern.compile("\\(created:\\s*([^\\)]+)\\)");

    private final WebClient webClient;
    private final String gatewayBaseUrl;

    public IronBucketS3Service() {
        this(resolveGatewayBaseUrl());
    }

    public IronBucketS3Service(String gatewayBaseUrl) {
        this(
            WebClient.builder()
                .baseUrl(normalizeBaseUrl(gatewayBaseUrl))
                .build(),
            normalizeBaseUrl(gatewayBaseUrl)
        );
    }

    IronBucketS3Service(WebClient webClient, String gatewayBaseUrl) {
        this.webClient = webClient;
        this.gatewayBaseUrl = normalizeBaseUrl(gatewayBaseUrl);
    }

    public List<S3Bucket> listBuckets(String jwtToken) {
        String response = webClient.get()
            .uri("/s3/buckets")
            .header("Authorization", authorizationHeader(jwtToken))
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return parseBuckets(response == null ? "" : response);
    }

    public S3Bucket getBucket(String jwtToken, String bucketName) {
        return listBuckets(jwtToken).stream()
            .filter(existingBucket -> existingBucket.name().equals(bucketName))
            .findFirst()
            .orElseThrow(() -> new BucketNotFoundException(bucketName));
    }

    public S3Bucket createBucket(String jwtToken, String bucketName, String ownerTenant) {
        webClient.post()
            .uri("/s3/bucket/{bucket}", bucketName)
            .header("Authorization", authorizationHeader(jwtToken))
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return new S3Bucket(
            bucketName,
            Instant.now(),
            ownerTenant,
            ROUTED_PROVIDER,
            ROUTED_REASON
        );
    }

    public boolean deleteBucket(String jwtToken, String bucketName) {
        try {
            webClient.delete()
                .uri("/s3/bucket/{bucket}", bucketName)
                .header("Authorization", authorizationHeader(jwtToken))
                .retrieve()
                .toBodilessEntity()
                .block();
            return true;
        } catch (RuntimeException runtimeException) {
            return false;
        }
    }

    public List<S3Object> listObjects(String jwtToken, String bucketName, String prefix) {
        String body = webClient.get()
            .uri("/s3/objects/{bucket}", bucketName)
            .header("Authorization", authorizationHeader(jwtToken))
            .retrieve()
            .bodyToMono(String.class)
            .block();

        if (body == null || body.isBlank()) {
            return List.of();
        }

        Instant now = Instant.now();
        return body.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .filter(line -> prefix == null || prefix.isBlank() || line.startsWith(prefix))
            .map(key -> new S3Object(
                key,
                bucketName,
                0L,
                now,
                "application/octet-stream",
                Map.of(),
                ROUTED_PROVIDER,
                ROUTED_REASON
            ))
            .toList();
    }

    public S3Object getObject(String jwtToken, String bucketName, String objectKey) {
        byte[] content = webClient.get()
            .uri("/s3/object/{bucket}/{key}", bucketName, objectKey)
            .header("Authorization", authorizationHeader(jwtToken))
            .retrieve()
            .bodyToMono(byte[].class)
            .block();

        byte[] data = content == null ? new byte[0] : content;
        return new S3Object(
            objectKey,
            bucketName,
            data.length,
            Instant.now(),
            "application/octet-stream",
            Map.of(),
            ROUTED_PROVIDER,
            ROUTED_REASON
        );
    }

    public S3Object uploadObject(String jwtToken, String bucketName, String objectKey, long size, String contentType) {
        int payloadSize = Math.max(0, (int) Math.min(size, 1_048_576L));
        byte[] payload = new byte[payloadSize];

        webClient.post()
            .uri("/s3/object/{bucket}/{key}", bucketName, objectKey)
            .header("Authorization", authorizationHeader(jwtToken))
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return new S3Object(
            objectKey,
            bucketName,
            size,
            Instant.now(),
            contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
            Map.of(),
            ROUTED_PROVIDER,
            ROUTED_REASON
        );
    }

    public S3Object uploadObject(String jwtToken, String bucketName, String objectKey, String content, String contentType) {
        byte[] payload = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);

        webClient.post()
            .uri("/s3/object/{bucket}/{key}", bucketName, objectKey)
            .header("Authorization", authorizationHeader(jwtToken))
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return new S3Object(
            objectKey,
            bucketName,
            payload.length,
            Instant.now(),
            contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
            Map.of(),
            ROUTED_PROVIDER,
            ROUTED_REASON
        );
    }

    public boolean deleteObject(String jwtToken, String bucketName, String objectKey) {
        webClient.delete()
            .uri("/s3/object/{bucket}/{key}", bucketName, objectKey)
            .header("Authorization", authorizationHeader(jwtToken))
            .retrieve()
            .toBodilessEntity()
            .block();
        return true;
    }

    public String getPresignedUrl(String jwtToken, String bucketName, String objectKey, int expiresIn) {
        return gatewayBaseUrl + "/s3/object/" + encodePath(bucketName) + "/" + encodePath(objectKey)
            + "?expires=" + expiresIn;
    }

    public void setTenantDefaultProvider(String tenantId, String provider) {
        // Routing is enforced by Brazz-Nossel. This setter is kept for API compatibility.
    }

    public void setBucketOverrideProvider(String tenantId, String bucketName, String provider) {
        // Routing is enforced by Brazz-Nossel. This setter is kept for API compatibility.
    }

    public ProviderRoutingDecision getBucketRoutingDecision(
        String jwtToken,
        String tenantId,
        String bucketName,
        String requiredCapability
    ) {
        return new ProviderRoutingDecision(
            tenantId,
            bucketName,
            requiredCapability == null ? "OBJECT_READ" : requiredCapability.trim().toUpperCase(Locale.ROOT),
            ROUTED_PROVIDER,
            ROUTED_REASON
        );
    }

    private String tenantForBucket(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            return "default";
        }
        int separator = bucketName.indexOf('-');
        if (separator > 0) {
            return bucketName.substring(0, separator);
        }
        return "default";
    }

    private static String resolveGatewayBaseUrl() {
        String fromEnv = System.getenv("S3_GATEWAY_BASE_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        String sentinelEnv = System.getenv("SENTINEL_GEAR_URL");
        if (sentinelEnv != null && !sentinelEnv.isBlank()) {
            return sentinelEnv;
        }

        String legacyFromEnv = System.getenv("BRAZZ_NOSSEL_BASE_URL");
        if (legacyFromEnv != null && !legacyFromEnv.isBlank()) {
            return legacyFromEnv;
        }

        return "http://steel-hammer-sentinel-gear:8080";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://steel-hammer-sentinel-gear:8080";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String authorizationHeader(String jwtToken) {
        if (jwtToken == null || jwtToken.isBlank()) {
            throw new IronBucketServiceException("jwtToken is required for gateway calls");
        }

        String token = jwtToken.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return token;
        }
        return "Bearer " + token;
    }

    private String encodePath(String pathSegment) {
        return UriUtils.encodePathSegment(pathSegment, StandardCharsets.UTF_8);
    }

    private List<S3Bucket> parseBuckets(String responseBody) {
        if (responseBody.isBlank()) {
            return List.of();
        }

        List<String> lines = responseBody.lines().map(String::trim).toList();
        String responseTenant = lines.stream()
            .filter(line -> line.toLowerCase(Locale.ROOT).startsWith("buckets for tenant "))
            .findFirst()
            .map(this::extractTenantFromHeading)
            .orElse(null);

        return lines.stream()
            .filter(line -> line.startsWith("-"))
            .map(line -> line.substring(1).trim())
            .map(line -> toBucket(line, responseTenant))
            .toList();
    }

    private String extractTenantFromHeading(String heading) {
        String prefix = "Buckets for tenant ";
        if (!heading.startsWith(prefix)) {
            return "default";
        }

        String tenant = heading.substring(prefix.length()).trim();
        if (tenant.endsWith(":")) {
            tenant = tenant.substring(0, tenant.length() - 1);
        }
        return tenant.isBlank() ? "default" : tenant;
    }

    private S3Bucket toBucket(String rawBucketLine, String responseTenant) {
        Matcher createdMatcher = CREATED_PATTERN.matcher(rawBucketLine);
        Instant creationDate = Instant.now();
        String bucketName = rawBucketLine;

        if (createdMatcher.find()) {
            String timestamp = createdMatcher.group(1);
            try {
                creationDate = Instant.parse(timestamp);
            } catch (RuntimeException ignored) {
                creationDate = Instant.now();
            }
            bucketName = rawBucketLine.substring(0, createdMatcher.start()).trim();
        }

        return new S3Bucket(
            bucketName,
            creationDate,
            responseTenant == null || responseTenant.isBlank() ? tenantForBucket(bucketName) : responseTenant,
            ROUTED_PROVIDER,
            ROUTED_REASON
        );
    }
}
