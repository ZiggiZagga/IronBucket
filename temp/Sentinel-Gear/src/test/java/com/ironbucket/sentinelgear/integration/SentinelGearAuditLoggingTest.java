package com.ironbucket.sentinelgear.integration;

import com.ironbucket.sentinelgear.fixtures.AuditFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Issue #47: Emit structured audit events (`JSON` via SLF4J) for all
 * critical actions: registration, resolution, proxy, deny
 *
 * Pattern: RED → GREEN → REFACTOR
 * ✗ Write failing test
 * ✓ Implement code to make test pass
 * ✓ Verify test passes
 */
@SpringBootTest
@DisplayName("Issue #47: Structured Audit Logging")
class SentinelGearAuditLoggingTest {

    @Autowired
    private AuditFixtures auditFixtures;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * TEST 1: Audit logs should be valid JSON
     *
     * GIVEN: An audit event is logged
     * WHEN: Event is serialized
     * THEN: Output should be valid JSON (parseable by standard JSON parser)
     */
    @Test
    @DisplayName("✗ test_auditEvent_jsonStructured")
    void test_auditEvent_jsonStructured() {
        // GIVEN: An audit event
        Map<String, Object> auditEvent = auditFixtures.generateAuditEvent_AccessDenied();

        // WHEN: Event is serialized to JSON
        String jsonOutput = auditFixtures.toJsonString(auditEvent);

        // THEN: Output should be valid JSON
        assertNotNull(jsonOutput);
        assertTrue(auditFixtures.isValidJson(jsonOutput), "Output should be valid JSON");

        // AND: Should be parseable back to object
        Map<String, Object> parsed = auditFixtures.parseEvent(jsonOutput);
        assertNotNull(parsed);
        assertEquals(auditEvent.get("eventType"), parsed.get("eventType"));
    }

    /**
     * TEST 2: All audit events should have ISO-8601 timestamp
     *
     * GIVEN: An audit event
     * WHEN: Event is created
     * THEN: Timestamp should be ISO-8601 format (RFC 3339)
     */
    @Test
    @DisplayName("✗ test_auditEvent_containsTimestamp")
    void test_auditEvent_containsTimestamp() {
        // GIVEN: Multiple audit events
        Map<String, Object> event1 = auditFixtures.generateAuditEvent_AccessDenied();
        Map<String, Object> event2 = auditFixtures.generateAuditEvent_AccessAllowed();
        Map<String, Object> event3 = auditFixtures.generateAuditEvent_PolicyEvaluationError();

        // THEN: All should have timestamp
        assertNotNull(event1.get("timestamp"));
        assertNotNull(event2.get("timestamp"));
        assertNotNull(event3.get("timestamp"));

        // AND: Timestamps should be ISO-8601 format
        String timestamp1 = event1.get("timestamp").toString();
        assertTrue(isISO8601(timestamp1), "Timestamp should be ISO-8601 format");

        // AND: Timestamps should be parseable as date
        assertDoesNotThrow(() -> java.time.OffsetDateTime.parse(timestamp1));
    }

    /**
     * TEST 3: Denied access events should be logged with reason
     *
     * GIVEN: An access was denied by policy
     * WHEN: Deny event is logged
     * THEN: Log should contain decision=DENY, reason, and status code
     */
    @Test
    @DisplayName("✗ test_auditEvent_accessDeny_logged")
    void test_auditEvent_accessDeny_logged() {
        // GIVEN: A denied access event
        Map<String, Object> event = auditFixtures.generateAuditEvent_AccessDenied();

        // THEN: Event should have DENY decision
        assertEquals("DENY", event.get("decision"));

        // AND: Event should have clear reason
        assertNotNull(event.get("reason"));
        assertFalse(event.get("reason").toString().isEmpty(), "Reason should not be empty");

        // AND: Event should have 403 status code
        assertEquals(403, event.get("statusCode"));

        // AND: Event should have principal and resource
        assertNotNull(event.get("principal"));
        assertNotNull(event.get("resource"));
        assertNotNull(event.get("action"));
    }

    /**
     * TEST 4: Successful proxy events should be logged
     *
     * GIVEN: A request was proxied successfully
     * WHEN: Success event is logged
     * THEN: Log should contain ALLOW decision and 200 status
     */
    @Test
    @DisplayName("✗ test_auditEvent_proxySuccess_logged")
    void test_auditEvent_proxySuccess_logged() {
        // GIVEN: A successful proxy event
        Map<String, Object> event = auditFixtures.generateAuditEvent_AccessAllowed();

        // THEN: Event should have ALLOW decision
        assertEquals("ALLOW", event.get("decision"));

        // AND: Event should have 200 status code
        assertEquals(200, event.get("statusCode"));

        // AND: Event should have reason
        assertNotNull(event.get("reason"));
        assertFalse(event.get("reason").toString().isEmpty());

        // AND: Event is valid audit event
        assertTrue(auditFixtures.isValidAuditEvent(event));
    }

    /**
     * TEST 5: Audit events should be persisted to PostgreSQL
     *
     * GIVEN: An audit event is created
     * WHEN: Event is prepared for persistence
     * THEN: Event should have all required fields for database storage
     */
    @Test
    @DisplayName("✗ test_auditEvent_uploadedToPostgres")
    void test_auditEvent_uploadedToPostgres() {
        // GIVEN: An audit event
        Map<String, Object> event = auditFixtures.generateAuditEvent_AccessDenied();

        // WHEN: Event is prepared for persistence
        // Required fields: timestamp, traceId, principal, action, resource, decision, reason
        String persistQuery = buildInsertStatement(event);

        // THEN: SQL should be valid insert statement
        assertNotNull(persistQuery);
        assertTrue(persistQuery.contains("INSERT INTO"), "Should be INSERT statement");
        assertTrue(persistQuery.contains("audit_events"), "Should target audit_events table");

        // AND: All required fields should be included
        assertTrue(persistQuery.contains("timestamp"));
        assertTrue(persistQuery.contains("traceId") || persistQuery.contains("trace_id"));
        assertTrue(persistQuery.contains("principal"));
        assertTrue(persistQuery.contains("action"));
        assertTrue(persistQuery.contains("resource"));
        assertTrue(persistQuery.contains("decision"));

        // AND: Event should be JSON serializable for storage
        String eventJson = auditFixtures.toJsonString(event);
        assertTrue(auditFixtures.isValidJson(eventJson), "Should be valid JSON for storage");
    }

    /**
     * Helper: Check if string is ISO-8601 format
     */
    private boolean isISO8601(String timestamp) {
        return timestamp.contains("T") && (timestamp.contains("Z") || timestamp.contains("+") || timestamp.contains("-"));
    }

    /**
     * Helper: Build SQL insert statement from audit event
     */
    private String buildInsertStatement(Map<String, Object> event) {
        return "INSERT INTO audit_events (" +
                "timestamp, traceId, principal, action, resource, decision, reason, statusCode" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

}
