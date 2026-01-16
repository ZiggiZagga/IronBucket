package com.ironbucket.sentinelgear.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates test policy requests and responses for testing
 * policy enforcement in Sentinel-Gear
 */
@Component
public class PolicyFixtures {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate a policy request for Alice accessing a bucket
     */
    public Map<String, Object> generatePolicyRequest_Allow() {
        return generatePolicyRequest(
                "alice@acme-corp",
                "s3:PutObject",
                "acme-corp:my-bucket/documents/*",
                "acme-corp",
                List.of("acme-corp:admins"),
                "us-east-1"
        );
    }

    /**
     * Generate a policy request that should be denied
     */
    public Map<String, Object> generatePolicyRequest_Deny() {
        return generatePolicyRequest(
                "bob@evil-corp",
                "s3:PutObject",
                "acme-corp:my-bucket/documents/*",
                "acme-corp",
                List.of("evil-corp:users"),
                "us-west-2"
        );
    }

    /**
     * Generate a policy request for S3 GetObject
     */
    public Map<String, Object> generatePolicyRequest_GetObject() {
        return generatePolicyRequest(
                "alice@acme-corp",
                "s3:GetObject",
                "acme-corp:my-bucket/documents/file.pdf",
                "acme-corp",
                List.of("acme-corp:devs"),
                "us-east-1"
        );
    }

    /**
     * Generate a policy request for DeleteObject
     */
    public Map<String, Object> generatePolicyRequest_DeleteObject() {
        return generatePolicyRequest(
                "alice@acme-corp",
                "s3:DeleteObject",
                "acme-corp:my-bucket/temp/*",
                "acme-corp",
                List.of("acme-corp:admins"),
                "us-east-1"
        );
    }

    /**
     * Generic policy request generator
     */
    private Map<String, Object> generatePolicyRequest(
            String principal,
            String action,
            String resource,
            String tenant,
            List<String> groups,
            String region) {

        Map<String, Object> request = new HashMap<>();
        request.put("principal", principal);
        request.put("action", action);
        request.put("resource", resource);

        Map<String, Object> context = new HashMap<>();
        context.put("region", region);
        context.put("groups", groups);
        context.put("tenantId", tenant);
        context.put("timestamp", System.currentTimeMillis());

        request.put("context", context);
        return request;
    }

    /**
     * Generate a ALLOW policy response
     */
    public Map<String, Object> generatePolicyResponse_Allow() {
        Map<String, Object> response = new HashMap<>();
        response.put("decision", "ALLOW");
        response.put("reason", "user in admin group");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * Generate a DENY policy response
     */
    public Map<String, Object> generatePolicyResponse_Deny() {
        Map<String, Object> response = new HashMap<>();
        response.put("decision", "DENY");
        response.put("reason", "access restricted to prod hours 9-17");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * Generate a DENY response for cross-tenant access
     */
    public Map<String, Object> generatePolicyResponse_DenyCrossTenant() {
        Map<String, Object> response = new HashMap<>();
        response.put("decision", "DENY");
        response.put("reason", "cross-tenant access denied");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * Convert policy object to JSON string
     */
    public String toJsonString(Map<String, Object> obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }

    /**
     * Parse policy response from JSON
     */
    public Map<String, Object> parseResponse(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
}
