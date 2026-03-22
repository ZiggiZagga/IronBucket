package com.ironbucket.pactumscroll.identity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical normalized identity representation shared across all IronBucket services.
 * Created after JWT validation and claim normalization in Sentinel-Gear.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedIdentity {

    // ── Core identity ─────────────────────────────────────────────────────────
    private String userId;
    private String username;
    private String issuer;
    private long issuedAt;
    private long expiresAt;

    // ── Role & Permission Context ─────────────────────────────────────────────
    @Builder.Default private List<String> roles = new ArrayList<>();
    @Builder.Default private List<String> realmRoles = new ArrayList<>();
    @Builder.Default private Map<String, List<String>> resourceRoles = new HashMap<>();

    // ── Organizational Context ────────────────────────────────────────────────
    /** Canonical tenant identifier (use this everywhere). */
    private String tenant;
    private String region;
    @Builder.Default private List<String> groups = new ArrayList<>();

    // ── Service Access ────────────────────────────────────────────────────────
    /** Services the user is allowed to access (e.g. "s3", "audit", "policy"). */
    @Builder.Default private List<String> services = new ArrayList<>();

    // ── User Metadata ─────────────────────────────────────────────────────────
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;

    // ── Enrichment Context ────────────────────────────────────────────────────
    private String ipAddress;
    private String userAgent;
    @Builder.Default private String requestId = UUID.randomUUID().toString();

    // ── Service Account Flag ──────────────────────────────────────────────────
    private boolean serviceAccount;

    // ── Raw JWT Claims ────────────────────────────────────────────────────────
    @Builder.Default private Map<String, Object> rawClaims = new HashMap<>();

    // ── Metadata ──────────────────────────────────────────────────────────────
    @Builder.Default private long createdAt = System.currentTimeMillis();

    // ── Compatibility aliases ─────────────────────────────────────────────────
    /** Alias for {@link #getTenant()} — kept for call-site compatibility. */
    public String getTenantId() { return tenant; }
    public void setTenantId(String tenantId) { this.tenant = tenantId; }

    /** Alias for {@link #getUsername()} — kept for call-site compatibility. */
    public String getPreferredUsername() { return username; }
    public void setPreferredUsername(String preferredUsername) { this.username = preferredUsername; }

    // ── Convenience helpers ───────────────────────────────────────────────────
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasService(String service) {
        return services != null && services.contains(service);
    }

    public boolean hasGroup(String group) {
        return groups != null && groups.contains(group);
    }

    // Defensive unmodifiable views for collection getters beyond Lombok defaults
    public List<String> getRoles() {
        return roles == null ? List.of() : Collections.unmodifiableList(roles);
    }

    public List<String> getRealmRoles() {
        return realmRoles == null ? List.of() : Collections.unmodifiableList(realmRoles);
    }

    public Map<String, List<String>> getResourceRoles() {
        return resourceRoles == null ? Map.of() : Collections.unmodifiableMap(resourceRoles);
    }

    public List<String> getGroups() {
        return groups == null ? List.of() : Collections.unmodifiableList(groups);
    }

    public List<String> getServices() {
        return services == null ? List.of() : Collections.unmodifiableList(services);
    }

    public Map<String, Object> getRawClaims() {
        return rawClaims == null ? Map.of() : Collections.unmodifiableMap(rawClaims);
    }

    @Override
    public String toString() {
        return "NormalizedIdentity{userId='" + userId + "', username='" + username +
               "', tenant='" + tenant + "', serviceAccount=" + serviceAccount +
               ", roles=" + roles + '}';
    }
}
