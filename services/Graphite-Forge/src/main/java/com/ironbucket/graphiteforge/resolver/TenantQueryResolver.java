package com.ironbucket.graphiteforge.resolver;

import java.util.List;
import java.util.Map;

public class TenantQueryResolver {

    public List<Map<String, Object>> listTenants() {
        return List.of();
    }

    public List<Map<String, Object>> getAllTenants() {
        return listTenants();
    }

    public Map<String, Object> getTenantById(String id) {
        return Map.of("id", id);
    }

    public Map<String, Object> tenant(String id) {
        return getTenantById(id);
    }
}
