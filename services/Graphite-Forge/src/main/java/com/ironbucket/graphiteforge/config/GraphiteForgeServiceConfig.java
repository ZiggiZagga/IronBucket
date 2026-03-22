package com.ironbucket.graphiteforge.config;

import com.ironbucket.graphiteforge.service.AuditLogService;
import com.ironbucket.graphiteforge.service.IdentityDirectoryService;
import com.ironbucket.graphiteforge.service.PolicyManagementService;
import com.ironbucket.graphiteforge.service.TenantDirectoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares shared service singletons so all DGS data fetchers operate on the
 * same in-memory state within a single runtime (mutations visible to queries).
 */
@Configuration
public class GraphiteForgeServiceConfig {

    @Bean
    public PolicyManagementService policyManagementService() {
        return new PolicyManagementService();
    }

    @Bean
    public IdentityDirectoryService identityDirectoryService() {
        return new IdentityDirectoryService();
    }

    @Bean
    public TenantDirectoryService tenantDirectoryService() {
        return new TenantDirectoryService();
    }

    @Bean
    public AuditLogService auditLogService() {
        return new AuditLogService();
    }
}
