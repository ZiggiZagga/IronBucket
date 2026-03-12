package com.ironbucket.graphiteforge.resolver;

import java.util.HashMap;
import java.util.Map;

public class IdentityMutationResolver {

    public Map<String, Object> createIdentity(Map<String, Object> input) {
        return new HashMap<>(input);
    }

    public Map<String, Object> addUser(Map<String, Object> input) {
        return createIdentity(input);
    }

    public Map<String, Object> updateIdentity(String id, Map<String, Object> input) {
        Map<String, Object> updated = new HashMap<>(input);
        updated.put("id", id);
        return updated;
    }

    public Map<String, Object> updateUser(String id, Map<String, Object> input) {
        return updateIdentity(id, input);
    }

    public boolean deleteIdentity(String id) {
        return id != null && !id.isBlank();
    }

    public boolean removeUser(String id) {
        return deleteIdentity(id);
    }
}
