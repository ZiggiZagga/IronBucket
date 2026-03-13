package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.service.IdentityDirectoryService;
import com.ironbucket.graphiteforge.service.TenantDirectoryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityTenantResolverStateTest {

    @Test
    void identityResolversShareStateForCrudLifecycle() {
        IdentityDirectoryService identityService = new IdentityDirectoryService();
        IdentityMutationResolver mutationResolver = new IdentityMutationResolver(identityService);
        IdentityQueryResolver queryResolver = new IdentityQueryResolver(identityService);

        Map<String, Object> created = mutationResolver.createIdentity(Map.of(
            "sub", "alice-sub",
            "tenantId", "tenant-a",
            "username", "alice",
            "email", "alice@example.com",
            "permissions", List.of("s3:GetObject", "s3:PutObject")
        ));

        String id = String.valueOf(created.get("id"));
        assertNotNull(id);

        Map<String, Object> byId = queryResolver.getIdentityById(id);
        Map<String, Object> bySub = queryResolver.getIdentity("alice-sub");
        List<Map<String, Object>> byTenant = queryResolver.listIdentities("tenant-a");

        assertEquals("alice", byId.get("username"));
        assertEquals(id, bySub.get("id"));
        assertEquals(1, byTenant.size());
        assertEquals(2, queryResolver.getUserPermissions(id).size());

        Map<String, Object> updated = mutationResolver.updateIdentity(id, Map.of("username", "alice-updated"));
        assertEquals("alice-updated", updated.get("username"));

        assertTrue(mutationResolver.deleteIdentity(id));
        assertFalse(mutationResolver.deleteIdentity(id));
        assertTrue(queryResolver.listIdentities("tenant-a").isEmpty());
    }

    @Test
    void tenantResolversShareStateForCrudLifecycle() {
        TenantDirectoryService tenantService = new TenantDirectoryService();
        TenantMutationResolver mutationResolver = new TenantMutationResolver(tenantService);
        TenantQueryResolver queryResolver = new TenantQueryResolver(tenantService);

        Map<String, Object> created = mutationResolver.createTenant(Map.of(
            "name", "Tenant A",
            "status", "ACTIVE"
        ));

        String id = String.valueOf(created.get("id"));
        assertNotNull(id);

        Map<String, Object> fetched = queryResolver.getTenantById(id);
        assertEquals("Tenant A", fetched.get("name"));
        assertEquals(1, queryResolver.listTenants().size());

        Map<String, Object> updated = mutationResolver.updateTenant(id, Map.of("status", "SUSPENDED"));
        assertEquals("SUSPENDED", updated.get("status"));

        assertTrue(mutationResolver.deleteTenant(id));
        assertFalse(mutationResolver.deleteTenant(id));
        assertTrue(queryResolver.listTenants().isEmpty());
    }
}
