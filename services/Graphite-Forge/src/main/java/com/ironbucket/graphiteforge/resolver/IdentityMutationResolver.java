package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.service.IdentityDirectoryService;

import java.util.Map;

public class IdentityMutationResolver {

    private final IdentityDirectoryService identityDirectoryService;

    public IdentityMutationResolver() {
        this(new IdentityDirectoryService());
    }

    public IdentityMutationResolver(IdentityDirectoryService identityDirectoryService) {
        this.identityDirectoryService = identityDirectoryService;
    }

    public Map<String, Object> createIdentity(Map<String, Object> input) {
        return identityDirectoryService.createIdentity(input);
    }

    public Map<String, Object> addUser(Map<String, Object> input) {
        return createIdentity(input);
    }

    public Map<String, Object> updateIdentity(String id, Map<String, Object> input) {
        return identityDirectoryService.updateIdentity(id, input);
    }

    public Map<String, Object> updateUser(String id, Map<String, Object> input) {
        return updateIdentity(id, input);
    }

    public boolean deleteIdentity(String id) {
        return identityDirectoryService.deleteIdentity(id);
    }

    public boolean removeUser(String id) {
        return deleteIdentity(id);
    }
}
