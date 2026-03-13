package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.service.TenantDirectoryService;

import java.util.List;
import java.util.Map;

public class TenantQueryResolver {

    private final TenantDirectoryService tenantDirectoryService;

    public TenantQueryResolver() {
        this(new TenantDirectoryService());
    }

    public TenantQueryResolver(TenantDirectoryService tenantDirectoryService) {
        this.tenantDirectoryService = tenantDirectoryService;
    }

    public List<Map<String, Object>> listTenants() {
        return tenantDirectoryService.listTenants();
    }

    public List<Map<String, Object>> getAllTenants() {
        return listTenants();
    }

    public Map<String, Object> getTenantById(String id) {
        return tenantDirectoryService.getTenantById(id);
    }

    public Map<String, Object> tenant(String id) {
        return getTenantById(id);
    }
}
