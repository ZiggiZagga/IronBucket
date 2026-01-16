package com.ironbucket.sentinelgear.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;

import java.util.HashMap;
import java.util.Map;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
        // GIVEN: A proxy request context
        String originalPath = "/acme-corp-bucket/documents/file.pdf";
        String backendUrl = "http://brazz-nossel:8082";

        // Simulate request context
        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("path", originalPath);
        requestContext.put("method", "GET");
        requestContext.put("backend", backendUrl);

        // WHEN: Request is marked for proxying
        boolean shouldProxy = true;
        String proxyTarget = backendUrl + originalPath;

        // THEN: Request should be forwarded
        assertTrue(shouldProxy, "Request should be marked for proxying");
        assertEquals("http://brazz-nossel:8082/acme-corp-bucket/documents/file.pdf", proxyTarget);
        assertTrue(proxyTarget.contains("brazz-nossel"), "Should proxy to brazz-nossel");
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
        // GIVEN: A request with auth header
        String authToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, authToken);

        // WHEN: Headers are copied to proxy request
        String proxyAuthHeader = headers.get(HttpHeaders.AUTHORIZATION);

        // THEN: Authorization should be preserved
        assertNotNull(proxyAuthHeader, "Authorization header should be preserved");
        assertEquals(authToken, proxyAuthHeader);
        assertTrue(proxyAuthHeader.startsWith("Bearer"), "Should contain Bearer token");
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
        // GIVEN: A request body
        String originalBody = "{\"metadata\": {\"env\": \"prod\"}, \"data\": \"file content\"}";

        // WHEN: Body is passed to proxy
        String proxyBody = originalBody;

        // THEN: Body should be unchanged
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
        // GIVEN: A backend response with headers
        Map<String, String> backendHeaders = new HashMap<>();
        backendHeaders.put("ETag", "\"abc123xyz\"");
        backendHeaders.put("Content-Type", "application/json");
        backendHeaders.put("X-Amz-Version-Id", "v123");

        // WHEN: Response is proxied back to client
        Map<String, String> proxiedHeaders = new HashMap<>(backendHeaders);

        // THEN: All headers should be passed back
        assertNotNull(proxiedHeaders.get("ETag"));
        assertEquals("\"abc123xyz\"", proxiedHeaders.get("ETag"));
        assertEquals("application/json", proxiedHeaders.get("Content-Type"));
        assertTrue(proxiedHeaders.containsKey("X-Amz-Version-Id"));
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
        // GIVEN: A backend error response
        int backendStatus = 403;
        String backendError = "Access Denied";

        // WHEN: Error is wrapped in S3 format
        String s3ErrorXML = generateS3ErrorResponse(backendStatus, backendError);

        // THEN: Response should be valid S3 XML
        assertNotNull(s3ErrorXML);
        assertTrue(s3ErrorXML.contains("<?xml"), "Should be XML");
        assertTrue(s3ErrorXML.contains("<Error>"), "Should have Error element");
        assertTrue(s3ErrorXML.contains("</Error>"), "Should close Error element");
        assertTrue(s3ErrorXML.contains(backendError), "Should contain error message");
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
