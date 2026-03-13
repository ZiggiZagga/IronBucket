package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.service.TenantDirectoryService;

import java.util.Map;

public class TenantMutationResolver {

    private final TenantDirectoryService tenantDirectoryService;

    public TenantMutationResolver() {
        this(new TenantDirectoryService());
    }

    public TenantMutationResolver(TenantDirectoryService tenantDirectoryService) {
        this.tenantDirectoryService = tenantDirectoryService;
    }

    public Map<String, Object> createTenant(Map<String, Object> input) {
        return tenantDirectoryService.createTenant(input);
    }

    public Map<String, Object> addTenant(Map<String, Object> input) {
        return createTenant(input);
    }

    public Map<String, Object> updateTenant(String id, Map<String, Object> input) {
        return tenantDirectoryService.updateTenant(id, input);
    }

    public boolean deleteTenant(String id) {
        return tenantDirectoryService.deleteTenant(id);
    }
}
