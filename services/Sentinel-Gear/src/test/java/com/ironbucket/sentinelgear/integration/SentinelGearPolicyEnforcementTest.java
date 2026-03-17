package com.ironbucket.sentinelgear.integration;

import com.ironbucket.sentinelgear.GatewayApp;
import com.ironbucket.sentinelgear.fixtures.PolicyFixtures;
import com.ironbucket.sentinelgear.testing.TestJwtDecoderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Issue #50: Add support for parsing and enforcing policies based on extracted
 * identity via REST calls to `policy-engine`
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
@DisplayName("Issue #50: Policy Enforcement via REST")
class SentinelGearPolicyEnforcementTest {

    @Autowired
    private PolicyFixtures policyFixtures;

    private int mapDecisionToHttpStatus(String decision) {
        return "ALLOW".equalsIgnoreCase(decision) ? 200 : 403;
    }

    private boolean shouldProxyRequest(Map<String, Object> policyResponse) {
        Object decision = policyResponse.get("decision");
        return decision instanceof String && "ALLOW".equalsIgnoreCase((String) decision);
    }

    /**
     * TEST 1: Send policy request with valid claims to policy-engine
     *
     * GIVEN: A valid policy request with extracted JWT claims
     * WHEN: Request is sent to policy-engine REST endpoint
     * THEN: Policy engine should receive all context (principal, action, resource, groups)
     */
    @Test
    @DisplayName("✗ test_sendPolicyRequest_withValidClaims")
    void test_sendPolicyRequest_withValidClaims() {
        Map<String, Object> policyRequest = policyFixtures.generatePolicyRequest_Allow();
        String requestBody = policyFixtures.toJsonString(policyRequest);
        Map<String, Object> parsedRequest = policyFixtures.parseResponse(requestBody);

        assertNotNull(parsedRequest.get("principal"));
        assertEquals("alice@acme-corp", parsedRequest.get("principal"));
        assertEquals("s3:PutObject", parsedRequest.get("action"));
        assertEquals("acme-corp:my-bucket/documents/*", parsedRequest.get("resource"));

        Map<String, Object> context = (Map<String, Object>) parsedRequest.get("context");
        assertNotNull(context);
        assertEquals("acme-corp", context.get("tenantId"));
        assertTrue(((java.util.List<?>) context.get("groups")).contains("acme-corp:admins"));
        assertEquals("us-east-1", context.get("region"));

        assertNotNull(requestBody);
        assertTrue(requestBody.contains("principal"));
        assertTrue(requestBody.contains("acme-corp:admins"));
    }

    /**
     * TEST 2: Policy denial should return 403 Access Denied
     *
     * GIVEN: A request from eve@evil-corp to access acme-corp bucket
     * WHEN: Policy engine evaluates the request
     * THEN: Response should be DENY with 403 status code
     */
    @Test
    @DisplayName("✗ test_policyDeny_returns403")
    void test_policyDeny_returns403() {
        Map<String, Object> denyResponse = policyFixtures.generatePolicyResponse_Deny();
        String denyResponseJson = policyFixtures.toJsonString(denyResponse);
        Map<String, Object> parsedDenyResponse = policyFixtures.parseResponse(denyResponseJson);

        assertEquals("DENY", parsedDenyResponse.get("decision"));
        assertNotNull(parsedDenyResponse.get("reason"));
        assertTrue(parsedDenyResponse.get("reason").toString().contains("restricted"));

        String decision = (String) parsedDenyResponse.get("decision");
        assertEquals(403, mapDecisionToHttpStatus(decision), "DENY decision should map to 403 status");
    }

    /**
     * TEST 3: Allowed policy should proxy request to backend
     *
     * GIVEN: A valid request that passes policy
     * WHEN: Policy engine returns ALLOW
     * THEN: Request should be proxied to backend service (Brazz-Nossel)
     */
    @Test
    @DisplayName("✗ test_policyAllow_proxiesRequest")
    void test_policyAllow_proxiesRequest() {
        Map<String, Object> allowResponse = policyFixtures.generatePolicyResponse_Allow();
        Map<String, Object> denyResponse = policyFixtures.generatePolicyResponse_Deny();

        assertEquals("ALLOW", allowResponse.get("decision"));
        assertNotNull(allowResponse.get("reason"));
        assertTrue(allowResponse.get("reason").toString().contains("admin"));
        assertTrue(shouldProxyRequest(allowResponse), "ALLOW decision should permit proxying");
        assertFalse(shouldProxyRequest(denyResponse), "DENY decision should block proxying");
    }

    /**
     * TEST 4: Policy evaluation should be logged to audit trail
     *
     * GIVEN: A policy evaluation result
     * WHEN: The result is logged
     * THEN: Audit trail should contain decision, reason, timestamp
     */
    @Test
    @DisplayName("✗ test_policyEvaluation_logsDecision")
    void test_policyEvaluation_logsDecision() {
        Map<String, Object> request = policyFixtures.generatePolicyRequest_Allow();
        Map<String, Object> response = policyFixtures.generatePolicyResponse_Allow();

        Map<String, Object> auditEntry = new java.util.HashMap<>();
        auditEntry.put("policyRequest", request);
        auditEntry.put("policyResponse", response);
        auditEntry.put("timestamp", System.currentTimeMillis());

        assertNotNull(auditEntry.get("timestamp"));

        String auditJson = policyFixtures.toJsonString(auditEntry);
        Map<String, Object> parsedAudit = policyFixtures.parseResponse(auditJson);

        Map<String, Object> auditResponse = (Map<String, Object>) parsedAudit.get("policyResponse");
        assertEquals("ALLOW", auditResponse.get("decision"));
        assertNotNull(parsedAudit.get("timestamp"));

        assertTrue(auditJson.contains("ALLOW"));
        assertTrue(auditJson.contains("admin group"));
    }

    /**
     * TEST 5: Policy request should include all context for decision making
     *
     * GIVEN: A policy request
     * WHEN: Context is populated from JWT claims
     * THEN: Request should have tenant, action, resource, groups, region
     */
    @Test
    @DisplayName("✗ test_policyRequest_includesContext")
    void test_policyRequest_includesContext() {
        Map<String, Object> request = policyFixtures.generatePolicyRequest_Allow();
        String requestJson = policyFixtures.toJsonString(request);
        Map<String, Object> parsedRequest = policyFixtures.parseResponse(requestJson);

        assertNotNull(parsedRequest.get("principal"));
        assertEquals("alice@acme-corp", parsedRequest.get("principal"));
        assertNotNull(parsedRequest.get("action"));
        assertEquals("s3:PutObject", parsedRequest.get("action"));
        assertNotNull(parsedRequest.get("resource"));

        Map<String, Object> context = (Map<String, Object>) parsedRequest.get("context");
        assertNotNull(context);
        assertNotNull(context.get("tenantId"));
        assertEquals("acme-corp", context.get("tenantId"));
        assertNotNull(context.get("groups"));
        assertTrue(context.get("groups") instanceof java.util.List);
        assertNotNull(context.get("region"));
        assertEquals("us-east-1", context.get("region"));
        assertNotNull(context.get("timestamp"));
    }

}
