package com.ironbucket.graphiteforge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class IdentityDirectoryService {

    private final ConcurrentMap<String, Map<String, Object>> identities = new ConcurrentHashMap<>();

    public Map<String, Object> createIdentity(Map<String, Object> input) {
        Objects.requireNonNull(input, "input is required");
        String id = UUID.randomUUID().toString();
        String sub = asString(input.get("sub"));
        String tenantId = asString(input.get("tenantId"));
        String username = asString(input.get("username"));
        String email = asString(input.get("email"));

        Map<String, Object> identity = new ConcurrentHashMap<>();
        identity.put("id", id);
        identity.put("sub", sub == null || sub.isBlank() ? id : sub);
        identity.put("tenantId", tenantId == null || tenantId.isBlank() ? "default" : tenantId);
        identity.put("username", username == null ? "unknown" : username);
        identity.put("email", email);
        identity.put("permissions", normalizePermissions(input.get("permissions")));

        identities.put(id, identity);
        return Map.copyOf(identity);
    }

    public Map<String, Object> updateIdentity(String id, Map<String, Object> input) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(input, "input is required");

        Map<String, Object> existing = identities.get(id);
        if (existing == null) {
            return Map.of("id", id);
        }

        Map<String, Object> updated = new ConcurrentHashMap<>(existing);
        updated.put("id", id);
        if (input.containsKey("sub")) {
            updated.put("sub", asString(input.get("sub")));
        }
        if (input.containsKey("tenantId")) {
            updated.put("tenantId", asString(input.get("tenantId")));
        }
        if (input.containsKey("username")) {
            updated.put("username", asString(input.get("username")));
        }
        if (input.containsKey("email")) {
            updated.put("email", asString(input.get("email")));
        }
        if (input.containsKey("permissions")) {
            updated.put("permissions", normalizePermissions(input.get("permissions")));
        }

        identities.put(id, updated);
        return Map.copyOf(updated);
    }

    public boolean deleteIdentity(String id) {
        return id != null && identities.remove(id) != null;
    }

    public Map<String, Object> getById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Map<String, Object> identity = identities.get(id);
        return identity == null ? null : Map.copyOf(identity);
    }

    public Map<String, Object> getBySub(String sub) {
        if (sub == null || sub.isBlank()) {
            return null;
        }
        return identities.values().stream()
            .filter(identity -> sub.equals(asString(identity.get("sub"))))
            .findFirst()
            .map(Map::copyOf)
            .orElse(null);
    }

    public List<Map<String, Object>> listByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return identities.values().stream()
            .filter(identity -> tenantId.equals(asString(identity.get("tenantId"))))
            .map(Map::copyOf)
            .toList();
    }

    public List<String> getPermissions(String identityId) {
        Map<String, Object> identity = identities.get(identityId);
        if (identity == null) {
            return List.of();
        }
        Object raw = identity.get("permissions");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> normalizePermissions(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (Object value : list) {
            if (value == null) {
                continue;
            }
            String permission = String.valueOf(value).trim();
            if (!permission.isEmpty()) {
                normalized.add(permission);
            }
        }
        return List.copyOf(normalized);
    }
}
