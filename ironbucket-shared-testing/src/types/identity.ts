/**
 * Normalized Identity Model
 * 
 * Represents a standardized user identity across all IronBucket components.
 * Created after JWT validation and claim normalization.
 */

/**
 * Normalized identity representation for all IronBucket components
 */
export interface NormalizedIdentity {
  // Core identity
  userId: string;                        // sub claim
  username: string;                      // preferred_username or email
  issuer: string;                        // iss claim
  issuedAt: number;                      // iat (Unix timestamp)
  expiresAt: number;                     // exp (Unix timestamp)
  
  // Role & Permission Context
  roles: string[];                       // All roles (realm + resource)
  realmRoles: string[];                  // Keycloak realm_access.roles
  resourceRoles: Map<string, string[]>;  // Resource-specific roles
  
  // Organizational Context
  tenant: string;                        // Tenant isolation key
  region?: string;                       // Geographic isolation
  groups: string[];                      // Organizational groups
  
  // User Metadata
  email?: string;                        // Email address
  firstName?: string;                    // given_name
  lastName?: string;                     // family_name
  fullName?: string;                     // Computed: firstName + lastName
  
  // Enrichment Context (added by Sentinel-Gear)
  ipAddress?: string;                    // Source IP for policies
  userAgent?: string;                    // Source User-Agent
  requestId?: string;                    // Unique request trace ID
  
  // Service Account Flag
  isServiceAccount: boolean;              // true if this is a service account
  
  // Raw JWT Claims (for policy evaluation)
  rawClaims: Record<string, any>;        // Original JWT claims
  
  // Metadata
  createdAt: number;                     // When this normalized identity was created
}

/**
 * Claim Normalization Result
 */
export interface ClaimNormalizationResult {
  success: boolean;
  error?: string;
  identity?: NormalizedIdentity;
}

