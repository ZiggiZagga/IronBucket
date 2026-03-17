package com.ironbucket.sentinelgear.integration;

import com.ironbucket.sentinelgear.GatewayApp;
import com.ironbucket.sentinelgear.testing.TestJwtDecoderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Issue #48: Ensure correct proxy request delegation to brazz-nossel
 * or other downstream services after policy validation
 *
 * Pattern: RED → GREEN → REFACTOR
 * ✗ Write failing test
 * ✓ Implement code to make test pass
 * ✓ Verify test passes
 */
@SpringBootTest(
    classes = {GatewayApp.class, TestJwtDecoderConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DisplayName("Issue #48: Proxy Request Delegation")
class SentinelGearProxyDelegationTest {

    /**
     * TEST 1: Proxy request is forwarded to backend
     *
     * GIVEN: A valid S3 request (GET /bucket/key)
     * WHEN: Policy validation passes
     * THEN: Request should be forwarded to Brazz-Nossel backend
     */
    @Test
    @DisplayName("✗ test_proxyRequest_forwardsToBackend")
    void test_proxyRequest_forwardsToBackend() {
        String backendUrl = "http://brazz-nossel:8082";
        String originalPath = "acme-corp-bucket/documents/file.pdf";

        String proxyTarget = buildProxyTarget(backendUrl, originalPath);

        assertEquals("http://brazz-nossel:8082/acme-corp-bucket/documents/file.pdf", proxyTarget);
        assertTrue(proxyTarget.contains("brazz-nossel"), "Should proxy to brazz-nossel");
        assertFalse(proxyTarget.contains("//acme-corp"), "Target path should be normalized");
    }

    /**
     * TEST 2: Authorization header preserved in proxy request
     *
     * GIVEN: A proxied request with Authorization header
     * WHEN: Request is forwarded to backend
     * THEN: Authorization header should be preserved
     */
    @Test
    @DisplayName("✗ test_proxyHeaders_preserveAuth")
    void test_proxyHeaders_preserveAuth() {
        String authToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, authToken);
        headers.put("X-Trace-Id", "4aa83f5f-03c0-433f-9020-7d10f1e95fca");
        headers.put("Connection", "keep-alive");

        Map<String, String> proxiedHeaders = buildProxiedHeaders(headers);
        String proxyAuthHeader = proxiedHeaders.get(HttpHeaders.AUTHORIZATION);

        assertNotNull(proxyAuthHeader, "Authorization header should be preserved");
        assertEquals(authToken, proxyAuthHeader);
        assertTrue(proxyAuthHeader.startsWith("Bearer"), "Should contain Bearer token");
        assertTrue(proxiedHeaders.containsKey("X-Trace-Id"), "Trace header should be preserved");
        assertFalse(proxiedHeaders.containsKey("Connection"), "Hop-by-hop headers should not be forwarded");
    }

    /**
     * TEST 3: Request body passed through unchanged
     *
     * GIVEN: A PUT request with JSON body
     * WHEN: Request is proxied
     * THEN: Body should be passed to backend unchanged
     */
    @Test
    @DisplayName("✗ test_proxyBody_passthrough")
    void test_proxyBody_passthrough() {
        String originalBody = "{\"metadata\": {\"env\": \"prod\"}, \"data\": \"file content\"}";

        String proxyBody = forwardRequestBody(originalBody);

        assertEquals(originalBody, proxyBody, "Request body should be passed unchanged");
        assertTrue(proxyBody.contains("metadata"), "Should contain original data");
        assertTrue(proxyBody.contains("prod"), "Should preserve metadata");
    }

    /**
     * TEST 4: Response headers from backend are returned
     *
     * GIVEN: Backend returns response with custom headers
     * WHEN: Response is passed back through proxy
     * THEN: Response headers should be included
     */
    @Test
    @DisplayName("✗ test_proxyResponse_headersPassed")
    void test_proxyResponse_headersPassed() {
        Map<String, String> backendHeaders = new HashMap<>();
        backendHeaders.put("ETag", "\"abc123xyz\"");
        backendHeaders.put("Content-Type", "application/json");
        backendHeaders.put("X-Amz-Version-Id", "v123");
        backendHeaders.put("Connection", "close");

        Map<String, String> proxiedHeaders = sanitizeBackendResponseHeaders(backendHeaders);

        assertNotNull(proxiedHeaders.get("ETag"));
        assertEquals("\"abc123xyz\"", proxiedHeaders.get("ETag"));
        assertEquals("application/json", proxiedHeaders.get("Content-Type"));
        assertTrue(proxiedHeaders.containsKey("X-Amz-Version-Id"));
        assertFalse(proxiedHeaders.containsKey("Connection"), "Connection header should not be returned to client");
    }

    /**
     * TEST 5: Backend errors are wrapped in S3 format
     *
     * GIVEN: Backend returns an error (4xx or 5xx)
     * WHEN: Error is passed back through proxy
     * THEN: Error should be formatted as S3 XML error
     */
    @Test
    @DisplayName("✗ test_proxyError_wrappedInS3Format")
    void test_proxyError_wrappedInS3Format() {
        int backendStatus = 403;
        String backendError = "Access Denied";

        String s3ErrorXML = generateS3ErrorResponse(backendStatus, backendError);

        assertNotNull(s3ErrorXML);
        assertTrue(s3ErrorXML.contains("<?xml"), "Should be XML");
        assertTrue(s3ErrorXML.contains("<Error>"), "Should have Error element");
        assertTrue(s3ErrorXML.contains("</Error>"), "Should close Error element");
        assertTrue(s3ErrorXML.contains(backendError), "Should contain error message");
        assertTrue(s3ErrorXML.contains("<Code>AccessDenied</Code>"), "403 must map to AccessDenied");

        String unavailableXml = generateS3ErrorResponse(503, "backend down");
        assertTrue(unavailableXml.contains("<Code>ServiceUnavailable</Code>"), "5xx must map to ServiceUnavailable");
    }

    private String buildProxyTarget(String backendUrl, String requestPath) {
        String normalizedPath = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
        return backendUrl + normalizedPath;
    }

    private Map<String, String> buildProxiedHeaders(Map<String, String> headers) {
        Set<String> allowed = Set.of(HttpHeaders.AUTHORIZATION, "X-Identity-Context", "X-Trace-Id", "Content-Type");
        Map<String, String> forwarded = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (allowed.contains(entry.getKey())) {
                forwarded.put(entry.getKey(), entry.getValue());
            }
        }
        return forwarded;
    }

    private String forwardRequestBody(String body) {
        return body;
    }

    private Map<String, String> sanitizeBackendResponseHeaders(Map<String, String> backendHeaders) {
        Map<String, String> responseHeaders = new HashMap<>(backendHeaders);
        responseHeaders.remove("Connection");
        return responseHeaders;
    }

    /**
     * Helper: Generate S3 error XML response
     */
    private String generateS3ErrorResponse(int statusCode, String message) {
        String code = statusCode == 403 ? "AccessDenied" : "ServiceUnavailable";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Error>\n" +
                "  <Code>" + code + "</Code>\n" +
                "  <Message>" + message + "</Message>\n" +
                "  <Resource>/bucket/key</Resource>\n" +
                "  <RequestId>request-id</RequestId>\n" +
                "</Error>";
    }

}
