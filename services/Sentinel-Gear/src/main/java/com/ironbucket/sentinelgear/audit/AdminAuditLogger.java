package com.ironbucket.sentinelgear.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class AdminAuditLogger {

    public Map<String, Object> recordAdminAction(String operator, String attestation, String action) {
        return recordAccessDecision(
            operator,
            attestation,
            UUID.randomUUID().toString(),
            "admin-control-plane",
            action,
            "ALLOW"
        );
    }

    public Map<String, Object> recordAccessDecision(
        String actor,
        String attestation,
        String requestId,
        String bucket,
        String object,
        String decision
    ) {
        requireText(actor, "actor");
        requireText(attestation, "attestation");
        requireText(requestId, "requestId");
        requireText(bucket, "bucket");
        requireText(object, "object");
        requireText(decision, "decision");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("actor", actor);
        event.put("operator", actor);
        event.put("attestation", attestation);
        event.put("requestId", requestId);
        event.put("bucket", bucket);
        event.put("object", object);
        event.put("decision", decision);
        event.put("timestamp", Instant.now().toString());
        event.put("audit", true);
        return Map.copyOf(event);
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
