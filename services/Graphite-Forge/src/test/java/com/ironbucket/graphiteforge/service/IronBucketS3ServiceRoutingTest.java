package com.ironbucket.graphiteforge.service;

import com.ironbucket.graphiteforge.model.S3Bucket;
import com.ironbucket.graphiteforge.model.S3Object;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronBucketS3ServiceRoutingTest {

    private HttpServer server;
    private IronBucketS3Service service;
    private final AtomicInteger uploadedLength = new AtomicInteger(-1);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);

        server.createContext("/s3/buckets", exchange -> {
            assertAuth(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String body = "Buckets for tenant tenant-a:\n"
                + "  - tenant-a-files (created: 2026-03-13T12:00:00Z)\n"
                + "  - tenant-a-logs (created: 2026-03-13T12:10:00Z)\n";
            writeString(exchange, 200, body);
        });

        server.createContext("/s3/bucket/tenant-a-files", exchange -> {
            assertAuth(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            writeString(exchange, 200, "tenant-a-files");
        });

        server.createContext("/s3/objects/tenant-a-files", exchange -> {
            assertAuth(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            writeString(exchange, 200, "a.txt\nb.txt\n");
        });

        server.createContext("/s3/object/tenant-a-files/a.txt", exchange -> {
            assertAuth(exchange);
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
                exchange.close();
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                uploadedLength.set(body.length);
                writeString(exchange, 200, "etag-123");
                return;
            }

            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        });

        server.start();
        service = new IronBucketS3Service("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void bucketResponsesAreDelegatedThroughGatewayAndClaimspindel() {
        S3Bucket created = service.createBucket("jwt", "tenant-a-files", "tenant-a");
        S3Bucket fetched = service.getBucket("jwt", "tenant-a-files");
        List<S3Bucket> listed = service.listBuckets("jwt");

        assertEquals("CLAIMSPINDEL", created.selectedProvider());
        assertEquals("sentinel-gear-gateway;claimspindel-policy-route", created.routingReason());
        assertEquals("CLAIMSPINDEL", fetched.selectedProvider());
        assertEquals("sentinel-gear-gateway;claimspindel-policy-route", fetched.routingReason());
        assertEquals("tenant-a-files", listed.getFirst().name());
        assertEquals("tenant-a", listed.getFirst().ownerTenant());
    }

    @Test
    void listObjectsAndGetObjectUseGatewayFlowEndpoints() {
        List<S3Object> listed = service.listObjects("jwt", "tenant-a-files", "a");
        S3Object fetched = service.getObject("jwt", "tenant-a-files", "a.txt");

        assertEquals(1, listed.size());
        assertEquals("a.txt", listed.getFirst().key());
        assertEquals("CLAIMSPINDEL", listed.getFirst().selectedProvider());
        assertEquals(5L, fetched.size());
        assertEquals("CLAIMSPINDEL", fetched.selectedProvider());
    }

    @Test
    void uploadAndDeleteObjectDelegateToGatewayFlow() {
        S3Object uploaded = service.uploadObject("jwt", "tenant-a-files", "a.txt", "payload", "text/plain");
        boolean deleted = service.deleteObject("jwt", "tenant-a-files", "a.txt");

        assertEquals(7, uploaded.size());
        assertEquals(7, uploadedLength.get());
        assertTrue(deleted);
    }

    @Test
    void routingDecisionRejectsUnsupportedCapability() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.getBucketRoutingDecision("jwt", "tenant-a", "tenant-a-files", "UNSUPPORTED_CAPABILITY")
        );

        assertTrue(exception.getMessage().contains("Unsupported capability"));
    }

    @Test
    void routingDecisionRejectsTenantBucketMismatch() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.getBucketRoutingDecision("jwt", "tenant-b", "tenant-a-files", "OBJECT_READ")
        );

        assertTrue(exception.getMessage().contains("does not own bucket"));
    }

    @Test
    void routingDecisionNormalizesCapabilityAndReason() {
        var decision = service.getBucketRoutingDecision("jwt", "tenant-a", "tenant-a-files", "object_read");

        assertEquals("OBJECT_READ", decision.requiredCapability());
        assertTrue(decision.reason().contains("capability-validated"));
    }

    @Test
    void exposesProviderCapabilityMatrixAndSupportsCapabilityFiltering() {
        var matrix = service.getProviderCapabilityMatrix("jwt");
        var multipartProviders = service.providersSupportingCapabilities("jwt", List.of("multipart_upload"));

        assertTrue(matrix.stream().anyMatch(profile -> "AWS_S3".equals(profile.provider())));
        assertTrue(matrix.stream().anyMatch(profile -> "LOCAL_FILESYSTEM".equals(profile.provider())));
        assertTrue(multipartProviders.containsAll(Arrays.asList("AWS_S3", "GCS", "AZURE_BLOB")));
        assertTrue(!multipartProviders.contains("LOCAL_FILESYSTEM"));
    }

    @Test
    void capabilityAwareRoutingHonorsDeniedProviders() {
        var decision = service.getCapabilityAwareRoutingDecision(
            "jwt",
            "tenant-a",
            "tenant-a-files",
            List.of("VERSIONING"),
            List.of("AWS_S3")
        );

        assertEquals("GCS", decision.selectedProvider());
        assertTrue(decision.reason().contains("capability-matrix-selection"));
    }

    private void assertAuth(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        assertEquals("Bearer jwt", auth);
    }

    private void writeString(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
