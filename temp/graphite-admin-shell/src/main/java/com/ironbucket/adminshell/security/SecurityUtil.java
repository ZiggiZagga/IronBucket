package com.ironbucket.adminshell.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static void requireRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Unauthenticated session");
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean allowed = authorities.stream().anyMatch(a -> a.getAuthority().equals(role));
        if (!allowed) {
            throw new AccessDeniedException("Missing required role: " + role);
        }
    }

    public static void requireRoleWithForce(String role, boolean force) {
        requireRole(role);
        if (!force) {
            throw new AccessDeniedException("Destructive commands require --force acknowledgement");
        }
    }
}
