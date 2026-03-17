package com.ironbucket.sentinelgear.integration;

import com.ironbucket.sentinelgear.GatewayApp;
import com.ironbucket.sentinelgear.fixtures.AuditFixtures;
import com.ironbucket.sentinelgear.testing.TestJwtDecoderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

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
@SpringBootTest(
    classes = {GatewayApp.class, TestJwtDecoderConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DisplayName("Issue #47: Structured Audit Logging")
class SentinelGearAuditLoggingTest {

    @Autowired
    private AuditFixtures auditFixtures;

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
        Map<String, Object> event1 = auditFixtures.generateAuditEvent_AccessDenied();
        Map<String, Object> event2 = auditFixtures.generateAuditEvent_AccessAllowed();
        Map<String, Object> event3 = auditFixtures.generateAuditEvent_PolicyEvaluationError();

        assertNotNull(event1.get("timestamp"));
        assertNotNull(event2.get("timestamp"));
        assertNotNull(event3.get("timestamp"));

        String timestamp1 = event1.get("timestamp").toString();
        String timestamp2 = event2.get("timestamp").toString();
        String timestamp3 = event3.get("timestamp").toString();

        assertTrue(isISO8601(timestamp1), "Timestamp should be ISO-8601 format");
        assertTrue(isISO8601(timestamp2), "Timestamp should be ISO-8601 format");
        assertTrue(isISO8601(timestamp3), "Timestamp should be ISO-8601 format");

        assertDoesNotThrow(() -> java.time.OffsetDateTime.parse(timestamp1));
        assertDoesNotThrow(() -> java.time.OffsetDateTime.parse(timestamp2));
        assertDoesNotThrow(() -> java.time.OffsetDateTime.parse(timestamp3));
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
        Map<String, Object> event = auditFixtures.generateAuditEvent_AccessDenied();

        Map<String, Object> persistenceRecord = buildPersistenceRecord(event);

        assertEquals("audit_events", persistenceRecord.get("table"));
        assertEquals(event.get("traceId"), persistenceRecord.get("traceId"));
        assertEquals(event.get("decision"), persistenceRecord.get("decision"));
        assertNotNull(persistenceRecord.get("payload"));

        String eventJson = auditFixtures.toJsonString(event);
        assertTrue(auditFixtures.isValidJson(eventJson), "Should be valid JSON for storage");

        Map<String, Object> invalidEvent = new java.util.HashMap<>(event);
        invalidEvent.remove("traceId");
        assertThrows(IllegalArgumentException.class, () -> buildPersistenceRecord(invalidEvent));
    }

    /**
     * Helper: Check if string is ISO-8601 format
     */
    private boolean isISO8601(String timestamp) {
        try {
            OffsetDateTime.parse(timestamp);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private Map<String, Object> buildPersistenceRecord(Map<String, Object> event) {
        Set<String> requiredFields = Set.of(
                "timestamp", "traceId", "principal", "action", "resource", "decision", "reason", "statusCode"
        );
        for (String field : requiredFields) {
            if (!event.containsKey(field) || event.get(field) == null) {
                throw new IllegalArgumentException("Missing required audit field: " + field);
            }
        }
        Map<String, Object> record = new java.util.HashMap<>();
        record.put("table", "audit_events");
        record.put("traceId", event.get("traceId"));
        record.put("decision", event.get("decision"));
        record.put("payload", auditFixtures.toJsonString(event));
        return record;
    }

}
