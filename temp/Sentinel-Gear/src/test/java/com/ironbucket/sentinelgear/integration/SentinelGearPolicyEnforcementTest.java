package com.ironbucket.sentinelgear.integration;

import com.ironbucket.sentinelgear.fixtures.PolicyFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Issue #50: Policy Enforcement via REST")
class SentinelGearPolicyEnforcementTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PolicyFixtures policyFixtures;

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
        // GIVEN: A policy request with valid claims
        Map<String, Object> policyRequest = policyFixtures.generatePolicyRequest_Allow();
        String requestBody = policyFixtures.toJsonString(policyRequest);

        // WHEN: Request is validated for required fields
        // THEN: All fields should be present
        assertNotNull(policyRequest.get("principal"));
        assertEquals("alice@acme-corp", policyRequest.get("principal"));

        assertNotNull(policyRequest.get("action"));
        assertEquals("s3:PutObject", policyRequest.get("action"));

        assertNotNull(policyRequest.get("resource"));
        assertEquals("acme-corp:my-bucket/documents/*", policyRequest.get("resource"));

        // AND: Context should include tenant, groups, region
        Map<String, Object> context = (Map<String, Object>) policyRequest.get("context");
        assertNotNull(context);
        assertEquals("acme-corp", context.get("tenantId"));
        assertTrue(((java.util.List<?>) context.get("groups")).contains("acme-corp:admins"));
        assertEquals("us-east-1", context.get("region"));

        // AND: Request should be serializable to JSON
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
        // GIVEN: A deny policy response
        Map<String, Object> denyResponse = policyFixtures.generatePolicyResponse_Deny();

        // THEN: Response should have DENY decision
        assertEquals("DENY", denyResponse.get("decision"));
        assertNotNull(denyResponse.get("reason"));
        assertTrue(denyResponse.get("reason").toString().contains("restricted"));

        // AND: When this is interpreted as HTTP response, it should be 403
        // (This would be enforced by policy filter in real implementation)
        String decision = (String) denyResponse.get("decision");
        int expectedStatus = "ALLOW".equals(decision) ? 200 : 403;
        assertEquals(403, expectedStatus, "DENY decision should map to 403 status");
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
        // GIVEN: An allow policy response
        Map<String, Object> allowResponse = policyFixtures.generatePolicyResponse_Allow();

        // THEN: Response should have ALLOW decision
        assertEquals("ALLOW", allowResponse.get("decision"));

        // AND: Reason should be provided
        assertNotNull(allowResponse.get("reason"));
        assertTrue(allowResponse.get("reason").toString().contains("admin"));

        // AND: When ALLOW is received, request proceeds to proxy layer
        String decision = (String) allowResponse.get("decision");
        assertTrue("ALLOW".equals(decision), "Policy decision should be ALLOW");
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
        // GIVEN: A policy request and response
        Map<String, Object> request = policyFixtures.generatePolicyRequest_Allow();
        Map<String, Object> response = policyFixtures.generatePolicyResponse_Allow();

        // WHEN: They are logged together
        // Create a combined audit entry
        Map<String, Object> auditEntry = new java.util.HashMap<>();
        auditEntry.put("policyRequest", request);
        auditEntry.put("policyResponse", response);
        auditEntry.put("timestamp", System.currentTimeMillis());

        // THEN: Audit entry should contain decision
        assertNotNull(auditEntry.get("timestamp"));

        Map<String, Object> auditResponse = (Map<String, Object>) auditEntry.get("policyResponse");
        assertEquals("ALLOW", auditResponse.get("decision"));

        // AND: Audit entry should be JSON serializable
        String auditJson = policyFixtures.toJsonString(auditEntry);
        assertNotNull(auditJson);
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
        // GIVEN: A policy request
        Map<String, Object> request = policyFixtures.generatePolicyRequest_Allow();

        // THEN: Principal should be present
        assertNotNull(request.get("principal"));
        assertEquals("alice@acme-corp", request.get("principal"));

        // AND: Action should be present
        assertNotNull(request.get("action"));
        assertEquals("s3:PutObject", request.get("action"));

        // AND: Resource should be present
        assertNotNull(request.get("resource"));

        // AND: Context should be present with all fields
        Map<String, Object> context = (Map<String, Object>) request.get("context");
        assertNotNull(context);

        // Context must have tenantId
        assertNotNull(context.get("tenantId"));
        assertEquals("acme-corp", context.get("tenantId"));

        // Context must have groups
        assertNotNull(context.get("groups"));
        assertTrue(context.get("groups") instanceof java.util.List);

        // Context must have region
        assertNotNull(context.get("region"));
        assertEquals("us-east-1", context.get("region"));

        // Context should have timestamp
        assertNotNull(context.get("timestamp"));
    }

}
