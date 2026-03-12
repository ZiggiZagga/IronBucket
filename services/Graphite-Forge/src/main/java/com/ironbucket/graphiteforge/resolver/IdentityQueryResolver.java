package com.ironbucket.graphiteforge.resolver;

import java.util.List;
import java.util.Map;

public class IdentityQueryResolver {

    public Map<String, Object> getIdentity(String sub) {
        return Map.of("sub", sub);
    }

    public List<Map<String, Object>> listIdentities(String tenantId) {
        return List.of();
    }

    public Map<String, Object> getIdentityById(String id) {
        return Map.of("id", id);
    }

    public Map<String, Object> identity(String id) {
        return getIdentityById(id);
    }

    public List<String> getUserPermissions(String identityId) {
        return List.of();
    }

    public List<String> permissions(String identityId) {
        return getUserPermissions(identityId);
    }

    public List<Map<String, Object>> getUsers(String tenantId) {
        return listIdentities(tenantId);
    }
}
