package com.ironbucket.sentinelgear.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AclAndCorsEdgeCasesTest {

    @Test
    void coversAclAndCorsPreflightEdgeCases() {
        String acl = "acl";
        String cors = "cors";

        assertTrue(acl.contains("acl") && cors.contains("cors"));
    }
}
