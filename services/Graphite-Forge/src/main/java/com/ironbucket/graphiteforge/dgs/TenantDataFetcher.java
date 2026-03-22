package com.ironbucket.graphiteforge.dgs;

import com.ironbucket.graphiteforge.service.TenantDirectoryService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * DGS data fetcher for all Tenant queries and mutations defined in schema.graphqls.
 *
 * Schema Tenant type: {id, name, status}
 * TenantDirectoryService stores these same fields — direct match.
 */
@DgsComponent
public class TenantDataFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataFetcher.class);

    private final TenantDirectoryService tenantService;

    @Autowired
    public TenantDataFetcher(TenantDirectoryService tenantService) {
        this.tenantService = tenantService;
    }

    // ───────────────────── Queries ─────────────────────

    @DgsQuery(field = "getTenant")
    public Mono<Map<String, Object>> getTenant(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql getTenant id={}", id);
            return tenantService.getTenantById(id);
        });
    }

    @DgsQuery(field = "tenant")
    public Mono<Map<String, Object>> tenant(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql tenant id={}", id);
            return tenantService.getTenantById(id);
        });
    }

    @DgsQuery(field = "getTenantById")
    public Mono<Map<String, Object>> getTenantById(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql getTenantById id={}", id);
            return tenantService.getTenantById(id);
        });
    }

    @DgsQuery(field = "listTenants")
    public Mono<List<Map<String, Object>>> listTenants() {
        return fromBlocking(() -> {
            LOG.info("graphql listTenants");
            return tenantService.listTenants();
        });
    }

    // ───────────────────── Mutations ─────────────────────

    @DgsMutation(field = "createTenant")
    public Mono<Map<String, Object>> createTenant(@InputArgument Map<String, Object> input) {
        return fromBlocking(() -> {
            LOG.info("graphql createTenant name={}", input == null ? null : input.get("name"));
            return tenantService.createTenant(input == null ? Map.of() : input);
        });
    }

    @DgsMutation(field = "addTenant")
    public Mono<Map<String, Object>> addTenant(@InputArgument Map<String, Object> input) {
        return fromBlocking(() -> {
            LOG.info("graphql addTenant name={}", input == null ? null : input.get("name"));
            return tenantService.createTenant(input == null ? Map.of() : input);
        });
    }

    @DgsMutation(field = "updateTenant")
    public Mono<Map<String, Object>> updateTenant(
        @InputArgument String id,
        @InputArgument Map<String, Object> input
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql updateTenant id={}", id);
            return tenantService.updateTenant(id, input == null ? Map.of() : input);
        });
    }

    @DgsMutation(field = "deleteTenant")
    public Mono<Boolean> deleteTenant(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql deleteTenant id={}", id);
            return tenantService.deleteTenant(id);
        });
    }

    // ───────────────────── Helpers ─────────────────────

    private <T> Mono<T> fromBlocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
