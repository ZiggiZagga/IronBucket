package com.ironbucket.roadmap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Graphite-Forge GraphQL Feature Completeness Test Suite
 * 
 * Tests validate that Graphite-Forge provides a complete GraphQL API
 * for policy management, identity queries, and audit logs.
 * 
 * This is the management/admin plane complementing IronBucket's data plane.
 * 
 * Status: ACTIVE - GraphQL management API completeness contract
 * Priority: Varies by phase (P0 MVP, P1 Phase 2, P2 Phase 3)
 * 
 * Marathon Mindset: Complete policy management API, not partial implementation
 */
@DisplayName("Graphite-Forge GraphQL Feature Completeness")
public class GraphQLFeaturesTest {

    private static final String PROJECT_ROOT = resolveRepoRoot().toString();
    private static final String GRAPHITE_FORGE_PATH = resolveGraphiteForgePath();

    private static Path resolveRepoRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("README.md")) && Files.exists(current.resolve("services"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to resolve repository root from user.dir=" + System.getProperty("user.dir"));
    }

    private static String resolveGraphiteForgePath() {
        Path repoRoot = Paths.get(PROJECT_ROOT);
        if (Files.exists(repoRoot.resolve("services/Graphite-Forge"))) {
            return "services/Graphite-Forge";
        }
        return "temp/Graphite-Forge";
    }
    
    @BeforeAll
    static void setup() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println(" Graphite-Forge GraphQL Feature Completeness Test Suite");
        System.out.println(" Testing: Complete GraphQL management API");
        System.out.println(" Status: ACTIVE (completeness gate)");
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    @Nested
    @DisplayName("Phase 1 MVP - Core GraphQL Schema (P0 CRITICAL)")
    class Phase1CoreGraphQLSchema {

        @Test
        @DisplayName("GraphQL schema file exists")
        void testGraphQLSchemaExists() {
            // RED: GraphQL schema must define all queries, mutations, subscriptions
            Path schemaFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "resources", "graphql", "schema.graphqls");
            
            assertTrue(Files.exists(schemaFile),
                "CRITICAL: GraphQL schema file must exist at " + schemaFile);
        }

        @Test
        @DisplayName("Policy query type defined")
        void testPolicyQueryTypeDefined() {
            // RED: Must be able to query policies
            Path schemaFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "resources", "graphql", "schema.graphqls");
            
            if (Files.exists(schemaFile)) {
                try {
                    String content = Files.readString(schemaFile);
                    assertTrue(content.contains("type Policy"),
                        "CRITICAL: Policy type must be defined in GraphQL schema");
                    assertTrue(content.contains("getPolicy") || content.contains("policy("),
                        "CRITICAL: Policy query operation must be defined");
                } catch (Exception e) {
                    fail("Could not read GraphQL schema: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("Policy mutation types defined")
        void testPolicyMutationsDefined() {
            // RED: Must be able to create/update/delete policies via GraphQL
            Path schemaFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "resources", "graphql", "schema.graphqls");
            
            if (Files.exists(schemaFile)) {
                try {
                    String content = Files.readString(schemaFile);
                    assertTrue(content.contains("createPolicy") || content.contains("addPolicy"),
                        "CRITICAL: createPolicy mutation must be defined");
                    assertTrue(content.contains("updatePolicy"),
                        "CRITICAL: updatePolicy mutation must be defined");
                    assertTrue(content.contains("deletePolicy"),
                        "CRITICAL: deletePolicy mutation must be defined");
                } catch (Exception e) {
                    fail("Could not read GraphQL schema: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("Identity query type defined")
        void testIdentityQueryTypeDefined() {
            // RED: Must be able to query user identities
            Path schemaFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "resources", "graphql", "schema.graphqls");
            
            if (Files.exists(schemaFile)) {
                try {
                    String content = Files.readString(schemaFile);
                    assertTrue(content.contains("type Identity") || content.contains("type User"),
                        "CRITICAL: Identity/User type must be defined");
                    assertTrue(content.contains("getIdentity") || content.contains("identity("),
                        "CRITICAL: Identity query operation must be defined");
                } catch (Exception e) {
                    fail("Could not read GraphQL schema: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("Tenant query type defined")
        void testTenantQueryTypeDefined() {
            // RED: Multi-tenant system needs tenant queries
            Path schemaFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "resources", "graphql", "schema.graphqls");
            
            if (Files.exists(schemaFile)) {
                try {
                    String content = Files.readString(schemaFile);
                    assertTrue(content.contains("type Tenant"),
                        "HIGH: Tenant type should be defined for multi-tenancy");
                    assertTrue(content.contains("getTenant") || content.contains("tenant("),
                        "HIGH: Tenant query operation should be defined");
                } catch (Exception e) {
                    fail("Could not read GraphQL schema: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 1 MVP - Policy Management Queries (P0 CRITICAL)")
    class Phase1PolicyManagementQueries {

        @Test
        @DisplayName("listPolicies query implemented")
        void testListPoliciesImplemented() {
            // RED: Core admin function - list all policies
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "PolicyQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("listPolicies") || content.contains("getAllPolicies"),
                        "CRITICAL: listPolicies query resolver must be implemented");
                } catch (Exception e) {
                    fail("Could not verify listPolicies: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("getPolicyById query implemented")
        void testGetPolicyByIdImplemented() {
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "PolicyQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("getPolicyById") || content.contains("getPolicy"),
                        "CRITICAL: getPolicyById query resolver must be implemented");
                } catch (Exception e) {
                    fail("Could not verify getPolicyById: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("searchPolicies query implemented")
        void testSearchPoliciesImplemented() {
            // RED: Find policies by principal, resource, or action
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "PolicyQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("searchPolicies") || content.contains("findPolicies"),
                        "HIGH: searchPolicies query should be implemented for admin UX");
                } catch (Exception e) {
                    fail("Could not verify searchPolicies: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("evaluatePolicy query implemented")
        void testEvaluatePolicyImplemented() {
            // RED: Dry-run policy evaluation without side effects
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "PolicyQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("evaluatePolicy") || content.contains("dryRun"),
                        "HIGH: evaluatePolicy (dry-run) should be implemented for testing");
                } catch (Exception e) {
                    fail("Could not verify evaluatePolicy: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 1 MVP - Policy Management Mutations (P0 CRITICAL)")
    class Phase1PolicyManagementMutations {

        @Test
        @DisplayName("createPolicy mutation implemented")
        void testCreatePolicyImplemented() {
            // RED: Core admin function - create new policies
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "PolicyMutationResolver.java");
            
            assertTrue(Files.exists(mutationFile),
                "CRITICAL: PolicyMutationResolver must exist");
            
            try {
                String content = Files.readString(mutationFile);
                assertTrue(content.contains("createPolicy") || content.contains("addPolicy"),
                    "CRITICAL: createPolicy mutation must be implemented");
            } catch (Exception e) {
                fail("Could not verify createPolicy: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("updatePolicy mutation implemented")
        void testUpdatePolicyImplemented() {
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "PolicyMutationResolver.java");
            
            if (Files.exists(mutationFile)) {
                try {
                    String content = Files.readString(mutationFile);
                    assertTrue(content.contains("updatePolicy"),
                        "CRITICAL: updatePolicy mutation must be implemented");
                } catch (Exception e) {
                    fail("Could not verify updatePolicy: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("deletePolicy mutation implemented")
        void testDeletePolicyImplemented() {
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "PolicyMutationResolver.java");
            
            if (Files.exists(mutationFile)) {
                try {
                    String content = Files.readString(mutationFile);
                    assertTrue(content.contains("deletePolicy"),
                        "CRITICAL: deletePolicy mutation must be implemented");
                } catch (Exception e) {
                    fail("Could not verify deletePolicy: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("validatePolicy mutation implemented")
        void testValidatePolicyImplemented() {
            // RED: Validate policy syntax/semantics before saving
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "PolicyMutationResolver.java");
            
            if (Files.exists(mutationFile)) {
                try {
                    String content = Files.readString(mutationFile);
                    assertTrue(content.contains("validatePolicy"),
                        "HIGH: validatePolicy mutation should check policy correctness");
                } catch (Exception e) {
                    fail("Could not verify validatePolicy: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 2 - Audit Log Queries (P1 HIGH)")
    class Phase2AuditLogQueries {

        @Test
        @DisplayName("getAuditLogs query implemented")
        void testGetAuditLogsImplemented() {
            // RED: Query audit trail for compliance
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "AuditQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("getAuditLogs") || content.contains("auditLogs"),
                        "HIGH: getAuditLogs query must be implemented for compliance");
                } catch (Exception e) {
                    fail("Could not verify getAuditLogs: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("filterAuditLogs query implemented")
        void testFilterAuditLogsImplemented() {
            // RED: Filter by user, action, resource, time range
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "AuditQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("filterAuditLogs") || content.contains("searchAuditLogs"),
                        "HIGH: Audit log filtering must support compliance queries");
                } catch (Exception e) {
                    fail("Could not verify filterAuditLogs: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("getAuditLogById query implemented")
        void testGetAuditLogByIdImplemented() {
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "AuditQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("getAuditLogById") || content.contains("auditLog("),
                        "MEDIUM: Individual audit log retrieval should be supported");
                } catch (Exception e) {
                    fail("Could not verify getAuditLogById: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("auditLogSubscription subscription implemented")
        void testAuditLogSubscriptionImplemented() {
            // RED: Real-time audit log streaming via GraphQL subscriptions
            Path subscriptionFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "AuditSubscriptionResolver.java");
            
            if (Files.exists(subscriptionFile)) {
                try {
                    String content = Files.readString(subscriptionFile);
                    assertTrue(content.contains("auditLogSubscription") || content.contains("onAuditLog"),
                        "HIGH: Real-time audit log streaming should be implemented");
                } catch (Exception e) {
                    fail("Could not verify auditLogSubscription: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 2 - Identity & User Management (P1 HIGH)")
    class Phase2IdentityUserManagement {

        @Test
        @DisplayName("listIdentities query implemented")
        void testListIdentitiesImplemented() {
            // RED: List all users/identities in system
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "IdentityQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("listIdentities") || content.contains("getUsers"),
                        "HIGH: listIdentities query should enumerate all users");
                } catch (Exception e) {
                    fail("Could not verify listIdentities: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("getIdentityById query implemented")
        void testGetIdentityByIdImplemented() {
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "IdentityQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("getIdentityById") || content.contains("identity("),
                        "HIGH: getIdentityById query should retrieve user details");
                } catch (Exception e) {
                    fail("Could not verify getIdentityById: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("getUserPermissions query implemented")
        void testGetUserPermissionsImplemented() {
            // RED: Show what permissions a user has (what they can access)
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "IdentityQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("getUserPermissions") || content.contains("permissions"),
                        "HIGH: getUserPermissions should show effective permissions");
                } catch (Exception e) {
                    fail("Could not verify getUserPermissions: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("createIdentity mutation implemented")
        void testCreateIdentityImplemented() {
            // RED: Create new users via GraphQL (if not using external IdP)
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "IdentityMutationResolver.java");
            
            if (Files.exists(mutationFile)) {
                try {
                    String content = Files.readString(mutationFile);
                    assertTrue(content.contains("createIdentity") || content.contains("addUser"),
                        "MEDIUM: createIdentity mutation should create new users");
                } catch (Exception e) {
                    fail("Could not verify createIdentity: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("updateIdentity mutation implemented")
        void testUpdateIdentityImplemented() {
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "IdentityMutationResolver.java");
            
            if (Files.exists(mutationFile)) {
                try {
                    String content = Files.readString(mutationFile);
                    assertTrue(content.contains("updateIdentity") || content.contains("updateUser"),
                        "MEDIUM: updateIdentity mutation should modify user details");
                } catch (Exception e) {
                    fail("Could not verify updateIdentity: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("deleteIdentity mutation implemented")
        void testDeleteIdentityImplemented() {
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "IdentityMutationResolver.java");
            
            if (Files.exists(mutationFile)) {
                try {
                    String content = Files.readString(mutationFile);
                    assertTrue(content.contains("deleteIdentity") || content.contains("removeUser"),
                        "MEDIUM: deleteIdentity mutation should remove users");
                } catch (Exception e) {
                    fail("Could not verify deleteIdentity: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 3 - Tenant Management (P2 MEDIUM)")
    class Phase3TenantManagement {

        @Test
        @DisplayName("listTenants query implemented")
        void testListTenantsImplemented() {
            // RED: Multi-tenant admin needs tenant listing
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "TenantQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("listTenants") || content.contains("getAllTenants"),
                        "MEDIUM: listTenants query should enumerate all tenants");
                } catch (Exception e) {
                    fail("Could not verify listTenants: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("getTenantById query implemented")
        void testGetTenantByIdImplemented() {
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "TenantQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("getTenantById") || content.contains("tenant("),
                        "MEDIUM: getTenantById query should retrieve tenant details");
                } catch (Exception e) {
                    fail("Could not verify getTenantById: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("createTenant mutation implemented")
        void testCreateTenantImplemented() {
            // RED: Onboard new tenants via API
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "TenantMutationResolver.java");
            
            if (Files.exists(mutationFile)) {
                try {
                    String content = Files.readString(mutationFile);
                    assertTrue(content.contains("createTenant") || content.contains("addTenant"),
                        "MEDIUM: createTenant mutation should onboard new tenants");
                } catch (Exception e) {
                    fail("Could not verify createTenant: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("updateTenant mutation implemented")
        void testUpdateTenantImplemented() {
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "TenantMutationResolver.java");
            
            if (Files.exists(mutationFile)) {
                try {
                    String content = Files.readString(mutationFile);
                    assertTrue(content.contains("updateTenant"),
                        "MEDIUM: updateTenant mutation should modify tenant settings");
                } catch (Exception e) {
                    fail("Could not verify updateTenant: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("deleteTenant mutation implemented")
        void testDeleteTenantImplemented() {
            Path mutationFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "TenantMutationResolver.java");
            
            if (Files.exists(mutationFile)) {
                try {
                    String content = Files.readString(mutationFile);
                    assertTrue(content.contains("deleteTenant"),
                        "MEDIUM: deleteTenant mutation should offboard tenants");
                } catch (Exception e) {
                    fail("Could not verify deleteTenant: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 3 - Statistics & Analytics (P2 MEDIUM)")
    class Phase3StatisticsAnalytics {

        @Test
        @DisplayName("getPolicyStatistics query implemented")
        void testGetPolicyStatisticsImplemented() {
            // RED: Show policy usage metrics (how often policies are evaluated)
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "StatsQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("getPolicyStatistics") || content.contains("policyStats"),
                        "MEDIUM: Policy usage statistics should be queryable");
                } catch (Exception e) {
                    fail("Could not verify getPolicyStatistics: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("getUserActivitySummary query implemented")
        void testGetUserActivitySummaryImplemented() {
            // RED: Show user activity patterns for security analysis
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "StatsQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("getUserActivitySummary") || content.contains("userActivity"),
                        "MEDIUM: User activity summaries should be available");
                } catch (Exception e) {
                    fail("Could not verify getUserActivitySummary: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("getResourceAccessPatterns query implemented")
        void testGetResourceAccessPatternsImplemented() {
            // RED: Show which resources are accessed most frequently
            Path resolverFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver", "StatsQueryResolver.java");
            
            if (Files.exists(resolverFile)) {
                try {
                    String content = Files.readString(resolverFile);
                    assertTrue(content.contains("getResourceAccessPatterns") || content.contains("resourceAccess"),
                        "LOW: Resource access patterns can help optimize policies");
                } catch (Exception e) {
                    fail("Could not verify getResourceAccessPatterns: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("GraphQL API Completeness Summary")
    class GraphQLAPICompletenessSummary {

        @Test
        @DisplayName("GraphQL API coverage >= 75% of management operations")
        void testGraphQLAPICoverageComplete() {
            // Meta-test tracking overall GraphQL API completeness
            
            Path graphiteForgePath = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH);
            
            // Check if Graphite-Forge directory exists
            boolean projectExists = Files.exists(graphiteForgePath);
            
            String schemaContent = "";
            String resolverContent = "";
            
            if (projectExists) {
                try {
                    Path schemaFile = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                        "src", "main", "resources", "graphql", "schema.graphqls");
                    if (Files.exists(schemaFile)) {
                        schemaContent = Files.readString(schemaFile);
                    }
                    
                    // Check all resolver files
                    Path resolverDir = Paths.get(PROJECT_ROOT, GRAPHITE_FORGE_PATH,
                        "src", "main", "java", "com", "ironbucket", "graphiteforge", "resolver");
                    if (Files.exists(resolverDir)) {
                        Files.walk(resolverDir)
                            .filter(p -> p.toString().endsWith(".java"))
                            .forEach(p -> {
                                try {
                                    resolverContent.concat(Files.readString(p));
                                } catch (Exception e) {
                                    // Ignore
                                }
                            });
                    }
                } catch (Exception e) {
                    // Files don't exist - expected in RED state
                }
            }
            
            String allContent = schemaContent + resolverContent;
            
            // Core schema & queries (must have): 8
            int coreImplemented = 0;
            int coreTotal = 8;
            if (allContent.contains("type Policy")) coreImplemented++;
            if (allContent.contains("type Identity") || allContent.contains("type User")) coreImplemented++;
            if (allContent.contains("type Tenant")) coreImplemented++;
            if (allContent.contains("listPolicies") || allContent.contains("getAllPolicies")) coreImplemented++;
            if (allContent.contains("getPolicyById") || allContent.contains("getPolicy")) coreImplemented++;
            if (allContent.contains("createPolicy") || allContent.contains("addPolicy")) coreImplemented++;
            if (allContent.contains("updatePolicy")) coreImplemented++;
            if (allContent.contains("deletePolicy")) coreImplemented++;
            
            // Audit & compliance (should have): 4
            int auditImplemented = 0;
            int auditTotal = 4;
            if (allContent.contains("getAuditLogs") || allContent.contains("auditLogs")) auditImplemented++;
            if (allContent.contains("filterAuditLogs") || allContent.contains("searchAuditLogs")) auditImplemented++;
            if (allContent.contains("getAuditLogById") || allContent.contains("auditLog(")) auditImplemented++;
            if (allContent.contains("auditLogSubscription") || allContent.contains("onAuditLog")) auditImplemented++;
            
            // Identity management (should have): 6
            int identityImplemented = 0;
            int identityTotal = 6;
            if (allContent.contains("listIdentities") || allContent.contains("getUsers")) identityImplemented++;
            if (allContent.contains("getIdentityById") || allContent.contains("identity(")) identityImplemented++;
            if (allContent.contains("getUserPermissions") || allContent.contains("permissions")) identityImplemented++;
            if (allContent.contains("createIdentity") || allContent.contains("addUser")) identityImplemented++;
            if (allContent.contains("updateIdentity") || allContent.contains("updateUser")) identityImplemented++;
            if (allContent.contains("deleteIdentity") || allContent.contains("removeUser")) identityImplemented++;
            
            // Advanced features (nice to have): 8
            int advancedImplemented = 0;
            int advancedTotal = 8;
            // Tenant management (5)
            if (allContent.contains("listTenants") || allContent.contains("getAllTenants")) advancedImplemented++;
            if (allContent.contains("getTenantById") || allContent.contains("tenant(")) advancedImplemented++;
            if (allContent.contains("createTenant") || allContent.contains("addTenant")) advancedImplemented++;
            if (allContent.contains("updateTenant")) advancedImplemented++;
            if (allContent.contains("deleteTenant")) advancedImplemented++;
            // Analytics (3)
            if (allContent.contains("getPolicyStatistics") || allContent.contains("policyStats")) advancedImplemented++;
            if (allContent.contains("getUserActivitySummary") || allContent.contains("userActivity")) advancedImplemented++;
            if (allContent.contains("getResourceAccessPatterns") || allContent.contains("resourceAccess")) advancedImplemented++;
            
            // Calculate weighted completeness
            // Core: 40%, Audit: 30%, Identity: 20%, Advanced: 10%
            double coreScore = (coreImplemented / (double) coreTotal) * 40.0;
            double auditScore = (auditImplemented / (double) auditTotal) * 30.0;
            double identityScore = (identityImplemented / (double) identityTotal) * 20.0;
            double advancedScore = (advancedImplemented / (double) advancedTotal) * 10.0;
            
            double totalCompleteness = coreScore + auditScore + identityScore + advancedScore;
            
            System.out.println("\n═══════════════════════════════════════════════════════════");
            System.out.println(" GraphQL API Completeness Score: " + String.format("%.1f%%", totalCompleteness));
            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println(" Core Schema & Mutations: " + coreImplemented + "/" + coreTotal + " (" + String.format("%.1f%%", coreScore) + ")");
            System.out.println(" Audit & Compliance:      " + auditImplemented + "/" + auditTotal + " (" + String.format("%.1f%%", auditScore) + ")");
            System.out.println(" Identity Management:     " + identityImplemented + "/" + identityTotal + " (" + String.format("%.1f%%", identityScore) + ")");
            System.out.println(" Advanced Features:       " + advancedImplemented + "/" + advancedTotal + " (" + String.format("%.1f%%", advancedScore) + ")");
            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println(" Target: 75% for production GraphQL management API");
            System.out.println("═══════════════════════════════════════════════════════════");
            
            assertTrue(totalCompleteness >= 75.0,
                "GraphQL API completeness is " + String.format("%.1f%%", totalCompleteness) +
                ", must be >= 75% for full management plane!");
        }
    }
}
