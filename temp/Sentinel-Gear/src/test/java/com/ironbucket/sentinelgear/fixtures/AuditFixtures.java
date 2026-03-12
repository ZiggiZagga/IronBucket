package com.ironbucket.sentinelgear.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates test audit events for testing structured audit logging
 */
@Component
public class AuditFixtures {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate an ACCESS_DENIED audit event
     */
    public Map<String, Object> generateAuditEvent_AccessDenied() {
        return generateAuditEvent(
                "ACCESS_DENIED",
                "alice@acme-corp",
                "s3:PutObject",
                "acme-corp:my-bucket/secret/*",
                "DENY",
                "policy: resource restricted",
                403
        );
    }

    /**
     * Generate an ACCESS_ALLOWED audit event
     */
    public Map<String, Object> generateAuditEvent_AccessAllowed() {
        return generateAuditEvent(
                "ACCESS_ALLOWED",
                "alice@acme-corp",
                "s3:GetObject",
                "acme-corp:my-bucket/documents/file.pdf",
                "ALLOW",
                "user in admin group",
                200
        );
    }

    /**
     * Generate a POLICY_EVALUATION_ERROR audit event
     */
    public Map<String, Object> generateAuditEvent_PolicyEvaluationError() {
        return generateAuditEvent(
                "POLICY_EVALUATION_ERROR",
                "alice@acme-corp",
                "s3:DeleteObject",
                "acme-corp:my-bucket/temp/*",
                "ERROR",
                "policy engine timeout",
                503
        );
    }

    /**
     * Generic audit event generator
     */
    private Map<String, Object> generateAuditEvent(
            String eventType,
            String principal,
            String action,
            String resource,
            String decision,
            String reason,
            int statusCode) {

        Map<String, Object> event = new HashMap<>();
        event.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        event.put("traceId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("principal", principal);
        event.put("action", action);
        event.put("resource", resource);
        event.put("decision", decision);
        event.put("reason", reason);
        event.put("statusCode", statusCode);
        event.put("sourceIp", "192.168.1.100");
        event.put("userAgent", "aws-cli/2.0");

        return event;
    }

    /**
     * Convert audit event to JSON string (for storage/logging)
     */
    public String toJsonString(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert audit event to JSON", e);
        }
    }

    /**
     * Parse audit event from JSON
     */
    public Map<String, Object> parseEvent(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse audit event JSON", e);
        }
    }

    /**
     * Validate that audit event has required fields
     */
    public boolean isValidAuditEvent(Map<String, Object> event) {
        return event.containsKey("timestamp")
                && event.containsKey("traceId")
                && event.containsKey("eventType")
                && event.containsKey("principal")
                && event.containsKey("action")
                && event.containsKey("resource")
                && event.containsKey("decision");
    }

    /**
     * Validate that JSON is valid JSON (can be parsed)
     */
    public boolean isValidJson(String json) {
        try {
            objectMapper.readValue(json, Object.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
