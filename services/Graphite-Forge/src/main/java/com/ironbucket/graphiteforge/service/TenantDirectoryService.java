package com.ironbucket.graphiteforge.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TenantDirectoryService {

    private final ConcurrentMap<String, Map<String, Object>> tenants = new ConcurrentHashMap<>();

    public Map<String, Object> createTenant(Map<String, Object> input) {
        Objects.requireNonNull(input, "input is required");
        String id = UUID.randomUUID().toString();
        Map<String, Object> tenant = new ConcurrentHashMap<>();
        tenant.put("id", id);
        tenant.put("name", valueOrDefault(input.get("name"), "unnamed"));
        tenant.put("status", valueOrDefault(input.get("status"), "ACTIVE"));
        tenants.put(id, tenant);
        return Map.copyOf(tenant);
    }

    public Map<String, Object> updateTenant(String id, Map<String, Object> input) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(input, "input is required");

        Map<String, Object> existing = tenants.get(id);
        if (existing == null) {
            return Map.of("id", id);
        }

        Map<String, Object> updated = new ConcurrentHashMap<>(existing);
        updated.put("id", id);
        if (input.containsKey("name")) {
            updated.put("name", valueOrDefault(input.get("name"), "unnamed"));
        }
        if (input.containsKey("status")) {
            updated.put("status", valueOrDefault(input.get("status"), "ACTIVE"));
        }

        tenants.put(id, updated);
        return Map.copyOf(updated);
    }

    public boolean deleteTenant(String id) {
        return id != null && tenants.remove(id) != null;
    }

    public List<Map<String, Object>> listTenants() {
        return tenants.values().stream().map(Map::copyOf).toList();
    }

    public Map<String, Object> getTenantById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Map<String, Object> tenant = tenants.get(id);
        return tenant == null ? null : Map.copyOf(tenant);
    }

    private static String valueOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
