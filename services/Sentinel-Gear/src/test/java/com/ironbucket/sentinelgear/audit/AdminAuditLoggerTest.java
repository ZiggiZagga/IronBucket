package com.ironbucket.sentinelgear.audit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAuditLoggerTest {

    @Test
    void recordsCompleteAuditableAccessEvent() {
        AdminAuditLogger logger = new AdminAuditLogger();

        Map<String, Object> event = logger.recordAccessDecision(
            "alice",
            "attestation-token",
            "req-123",
            "tenant-bucket",
            "object.txt",
            "DENY"
        );

        assertEquals("alice", event.get("actor"));
        assertEquals("req-123", event.get("requestId"));
        assertEquals("tenant-bucket", event.get("bucket"));
        assertEquals("object.txt", event.get("object"));
        assertEquals("DENY", event.get("decision"));
        assertTrue((Boolean) event.get("audit"));
        assertTrue(event.containsKey("timestamp"));
    }

    @Test
    void rejectsBlankActor() {
        AdminAuditLogger logger = new AdminAuditLogger();

        assertThrows(IllegalArgumentException.class, () -> logger.recordAccessDecision(
            " ",
            "attestation-token",
            "req-123",
            "tenant-bucket",
            "object.txt",
            "DENY"
        ));
    }
}
