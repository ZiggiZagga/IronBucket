package com.ironbucket.graphiteforge.dgs;

import com.ironbucket.graphiteforge.service.IdentityDirectoryService;
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
 * DGS data fetcher for all Identity queries and mutations defined in schema.graphqls.
 *
 * Schema Identity type: {id, sub, tenantId, username, email, permissions}
 * IdentityDirectoryService stores the same fields in its map — direct match.
 */
@DgsComponent
public class IdentityDataFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityDataFetcher.class);

    private final IdentityDirectoryService identityService;

    @Autowired
    public IdentityDataFetcher(IdentityDirectoryService identityService) {
        this.identityService = identityService;
    }

    // ───────────────────── Queries ─────────────────────

    @DgsQuery(field = "getIdentity")
    public Mono<Map<String, Object>> getIdentity(@InputArgument String sub) {
        return fromBlocking(() -> {
            LOG.info("graphql getIdentity sub={}", sub);
            return identityService.getBySub(sub);
        });
    }

    @DgsQuery(field = "identity")
    public Mono<Map<String, Object>> identity(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql identity id={}", id);
            return identityService.getById(id);
        });
    }

    @DgsQuery(field = "getIdentityById")
    public Mono<Map<String, Object>> getIdentityById(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql getIdentityById id={}", id);
            return identityService.getById(id);
        });
    }

    @DgsQuery(field = "listIdentities")
    public Mono<List<Map<String, Object>>> listIdentities(@InputArgument String tenantId) {
        return fromBlocking(() -> {
            LOG.info("graphql listIdentities tenantId={}", tenantId);
            return identityService.listByTenant(tenantId);
        });
    }

    @DgsQuery(field = "getUserPermissions")
    public Mono<List<String>> getUserPermissions(@InputArgument String identityId) {
        return fromBlocking(() -> {
            LOG.info("graphql getUserPermissions identityId={}", identityId);
            return identityService.getPermissions(identityId);
        });
    }

    // ───────────────────── Mutations ─────────────────────

    @DgsMutation(field = "createIdentity")
    public Mono<Map<String, Object>> createIdentity(@InputArgument Map<String, Object> input) {
        return fromBlocking(() -> {
            LOG.info("graphql createIdentity tenantId={}", input == null ? null : input.get("tenantId"));
            return identityService.createIdentity(normalizeInput(input));
        });
    }

    @DgsMutation(field = "addUser")
    public Mono<Map<String, Object>> addUser(@InputArgument Map<String, Object> input) {
        return fromBlocking(() -> {
            LOG.info("graphql addUser tenantId={}", input == null ? null : input.get("tenantId"));
            return identityService.createIdentity(normalizeInput(input));
        });
    }

    @DgsMutation(field = "updateIdentity")
    public Mono<Map<String, Object>> updateIdentity(
        @InputArgument String id,
        @InputArgument Map<String, Object> input
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql updateIdentity id={}", id);
            return identityService.updateIdentity(id, normalizeInput(input));
        });
    }

    @DgsMutation(field = "updateUser")
    public Mono<Map<String, Object>> updateUser(
        @InputArgument String id,
        @InputArgument Map<String, Object> input
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql updateUser id={}", id);
            return identityService.updateIdentity(id, normalizeInput(input));
        });
    }

    @DgsMutation(field = "deleteIdentity")
    public Mono<Boolean> deleteIdentity(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql deleteIdentity id={}", id);
            return identityService.deleteIdentity(id);
        });
    }

    @DgsMutation(field = "removeUser")
    public Mono<Boolean> removeUser(@InputArgument String id) {
        return fromBlocking(() -> {
            LOG.info("graphql removeUser id={}", id);
            return identityService.deleteIdentity(id);
        });
    }

    // ───────────────────── Helpers ─────────────────────

    /**
     * Normalise GraphQL IdentityInput (schema: tenantId, username, email)
     * to the Map keys expected by IdentityDirectoryService.
     */
    private Map<String, Object> normalizeInput(Map<String, Object> input) {
        if (input == null) return Map.of();
        return input; // IdentityDirectoryService already reads tenantId, username, email from the map
    }

    private <T> Mono<T> fromBlocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
