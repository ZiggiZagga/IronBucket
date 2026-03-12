package com.ironbucket.graphiteforge.resolver;

import java.util.HashMap;
import java.util.Map;

public class TenantMutationResolver {

    public Map<String, Object> createTenant(Map<String, Object> input) {
        return new HashMap<>(input);
    }

    public Map<String, Object> addTenant(Map<String, Object> input) {
        return createTenant(input);
    }

    public Map<String, Object> updateTenant(String id, Map<String, Object> input) {
        Map<String, Object> updated = new HashMap<>(input);
        updated.put("id", id);
        return updated;
    }

    public boolean deleteTenant(String id) {
        return id != null && !id.isBlank();
    }
}
