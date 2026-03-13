package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.service.IdentityDirectoryService;

import java.util.List;
import java.util.Map;

public class IdentityQueryResolver {

    private final IdentityDirectoryService identityDirectoryService;

    public IdentityQueryResolver() {
        this(new IdentityDirectoryService());
    }

    public IdentityQueryResolver(IdentityDirectoryService identityDirectoryService) {
        this.identityDirectoryService = identityDirectoryService;
    }

    public Map<String, Object> getIdentity(String sub) {
        return identityDirectoryService.getBySub(sub);
    }

    public List<Map<String, Object>> listIdentities(String tenantId) {
        return identityDirectoryService.listByTenant(tenantId);
    }

    public Map<String, Object> getIdentityById(String id) {
        return identityDirectoryService.getById(id);
    }

    public Map<String, Object> identity(String id) {
        return getIdentityById(id);
    }

    public List<String> getUserPermissions(String identityId) {
        return identityDirectoryService.getPermissions(identityId);
    }

    public List<String> permissions(String identityId) {
        return getUserPermissions(identityId);
    }

    public List<Map<String, Object>> getUsers(String tenantId) {
        return listIdentities(tenantId);
    }
}
