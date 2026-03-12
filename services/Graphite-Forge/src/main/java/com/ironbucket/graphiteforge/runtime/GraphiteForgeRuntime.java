package com.ironbucket.graphiteforge.runtime;

import com.ironbucket.graphiteforge.resolver.AuditQueryResolver;
import com.ironbucket.graphiteforge.resolver.AuditSubscriptionResolver;
import com.ironbucket.graphiteforge.resolver.IdentityMutationResolver;
import com.ironbucket.graphiteforge.resolver.IdentityQueryResolver;
import com.ironbucket.graphiteforge.resolver.PolicyMutationResolver;
import com.ironbucket.graphiteforge.resolver.PolicyQueryResolver;
import com.ironbucket.graphiteforge.resolver.PolicyResolver;
import com.ironbucket.graphiteforge.resolver.S3BucketResolver;
import com.ironbucket.graphiteforge.resolver.S3ObjectResolver;
import com.ironbucket.graphiteforge.resolver.StatsQueryResolver;
import com.ironbucket.graphiteforge.resolver.TenantMutationResolver;
import com.ironbucket.graphiteforge.resolver.TenantQueryResolver;
import com.ironbucket.graphiteforge.service.AuditLogService;
import com.ironbucket.graphiteforge.service.IronBucketS3Service;
import com.ironbucket.graphiteforge.service.PolicyManagementService;

/**
 * Central runtime wiring for Graphite-Forge.
 *
 * This keeps all resolvers on shared in-memory services so mutations and
 * subsequent queries observe the same runtime state.
 */
public class GraphiteForgeRuntime {

    private final PolicyManagementService policyManagementService = new PolicyManagementService();
    private final IronBucketS3Service s3Service = new IronBucketS3Service();
    private final AuditLogService auditLogService = new AuditLogService();

    private final PolicyQueryResolver policyQueryResolver = new PolicyQueryResolver(policyManagementService);
    private final PolicyMutationResolver policyMutationResolver = new PolicyMutationResolver(policyManagementService);
    private final PolicyResolver policyResolver = new PolicyResolver(policyManagementService);

    private final S3BucketResolver s3BucketResolver = new S3BucketResolver(s3Service);
    private final S3ObjectResolver s3ObjectResolver = new S3ObjectResolver(s3Service);

    private final AuditQueryResolver auditQueryResolver = new AuditQueryResolver(auditLogService);
    private final AuditSubscriptionResolver auditSubscriptionResolver = new AuditSubscriptionResolver(auditLogService);

    private final IdentityQueryResolver identityQueryResolver = new IdentityQueryResolver();
    private final IdentityMutationResolver identityMutationResolver = new IdentityMutationResolver();
    private final TenantQueryResolver tenantQueryResolver = new TenantQueryResolver();
    private final TenantMutationResolver tenantMutationResolver = new TenantMutationResolver();
    private final StatsQueryResolver statsQueryResolver = new StatsQueryResolver(policyManagementService, auditLogService);

    public static GraphiteForgeRuntime create() {
        return new GraphiteForgeRuntime();
    }

    public PolicyQueryResolver policyQueryResolver() {
        return policyQueryResolver;
    }

    public PolicyMutationResolver policyMutationResolver() {
        return policyMutationResolver;
    }

    public PolicyResolver policyResolver() {
        return policyResolver;
    }

    public S3BucketResolver s3BucketResolver() {
        return s3BucketResolver;
    }

    public S3ObjectResolver s3ObjectResolver() {
        return s3ObjectResolver;
    }

    public AuditQueryResolver auditQueryResolver() {
        return auditQueryResolver;
    }

    public AuditSubscriptionResolver auditSubscriptionResolver() {
        return auditSubscriptionResolver;
    }

    public IdentityQueryResolver identityQueryResolver() {
        return identityQueryResolver;
    }

    public IdentityMutationResolver identityMutationResolver() {
        return identityMutationResolver;
    }

    public TenantQueryResolver tenantQueryResolver() {
        return tenantQueryResolver;
    }

    public TenantMutationResolver tenantMutationResolver() {
        return tenantMutationResolver;
    }

    public StatsQueryResolver statsQueryResolver() {
        return statsQueryResolver;
    }
}
