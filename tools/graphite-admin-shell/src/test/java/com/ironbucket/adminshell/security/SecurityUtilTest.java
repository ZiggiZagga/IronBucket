package com.ironbucket.adminshell.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireRoleFailsWhenMissing() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "pass", "ROLE_READONLY"));
        assertThrows(AccessDeniedException.class, () -> SecurityUtil.requireRole("ROLE_ADMIN"));
    }

    @Test
    void requireRoleWithForceFailsWhenForceNotProvided() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        assertThrows(AccessDeniedException.class, () -> SecurityUtil.requireRoleWithForce("ROLE_ADMIN", false));
    }
}
