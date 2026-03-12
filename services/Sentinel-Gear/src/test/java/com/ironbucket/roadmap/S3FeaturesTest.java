package com.ironbucket.roadmap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IronBucket S3 Feature Completeness Test Suite
 * 
 * Tests validate that IronBucket supports the complete S3 API feature set
 * comparable to Graphite-Forge's GraphQL API completeness.
 * 
 * Status: RED - These tests MUST FAIL initially, then we implement features
 * Priority: Varies by phase (P0 MVP, P1 Phase 2, P2 Phase 3)
 * 
 * Marathon Mindset: Complete S3 compatibility, not partial implementation
 */
@DisplayName("IronBucket S3 Feature Completeness")
public class S3FeaturesTest {

    private static final String PROJECT_ROOT = resolveRepoRoot().toString();

    private static Path modulePath(String moduleName, String... parts) {
        Path servicesPath = Paths.get(PROJECT_ROOT, "services", moduleName);
        Path tempPath = Paths.get(PROJECT_ROOT, "temp", moduleName);
        Path basePath = Files.exists(servicesPath) ? servicesPath : tempPath;
        return basePath.resolve(Paths.get("", parts));
    }

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
    
    @BeforeAll
    static void setup() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println(" IronBucket S3 Feature Completeness Test Suite");
        System.out.println(" Testing: Complete S3 API implementation");
        System.out.println(" Status: RED (Features expected to be missing initially)");
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    @Nested
    @DisplayName("Phase 1 MVP - Core S3 Operations (P0 CRITICAL)")
    class Phase1CoreS3Operations {

        @Test
        @DisplayName("CreateBucket operation implemented")
        void testCreateBucketImplemented() {
            // RED: Verify CreateBucket endpoint exists
            Path controllerFile = modulePath("Brazz-Nossel",
                "src", "main", "java", "com", "ironbucket", "brazznossel", "controller", "S3Controller.java");
            
            assertTrue(Files.exists(controllerFile), 
                "CRITICAL: S3Controller must exist");
            
            try {
                String content = Files.readString(controllerFile);
                assertTrue(content.contains("createBucket") || content.contains("CreateBucket"),
                    "CRITICAL: CreateBucket operation must be implemented in S3Controller");
            } catch (Exception e) {
                fail("Could not read S3Controller: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("PutObject operation implemented")
        void testPutObjectImplemented() {
            // RED: Verify PutObject endpoint exists
            Path controllerFile = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "controller", "S3Controller.java");
            
            if (Files.exists(controllerFile)) {
                try {
                    String content = Files.readString(controllerFile);
                    assertTrue(content.contains("putObject") || content.contains("PutObject"),
                        "CRITICAL: PutObject operation must be implemented");
                } catch (Exception e) {
                    fail("Could not verify PutObject: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("GetObject operation implemented")
        void testGetObjectImplemented() {
            // RED: Verify GetObject endpoint exists
            Path controllerFile = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "controller", "S3Controller.java");
            
            if (Files.exists(controllerFile)) {
                try {
                    String content = Files.readString(controllerFile);
                    assertTrue(content.contains("getObject") || content.contains("GetObject"),
                        "CRITICAL: GetObject operation must be implemented");
                } catch (Exception e) {
                    fail("Could not verify GetObject: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("DeleteObject operation implemented")
        void testDeleteObjectImplemented() {
            Path controllerFile = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "controller", "S3Controller.java");
            
            if (Files.exists(controllerFile)) {
                try {
                    String content = Files.readString(controllerFile);
                    assertTrue(content.contains("deleteObject") || content.contains("DeleteObject"),
                        "CRITICAL: DeleteObject operation must be implemented");
                } catch (Exception e) {
                    fail("Could not verify DeleteObject: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("ListBucket (ListObjectsV2) operation implemented")
        void testListBucketImplemented() {
            Path controllerFile = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "controller", "S3Controller.java");
            
            if (Files.exists(controllerFile)) {
                try {
                    String content = Files.readString(controllerFile);
                    assertTrue(content.contains("listObjects") || content.contains("ListObjects"),
                        "CRITICAL: ListObjectsV2 operation must be implemented");
                } catch (Exception e) {
                    fail("Could not verify ListObjects: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("HeadBucket operation implemented")
        void testHeadBucketImplemented() {
            // RED: HeadBucket checks if bucket exists without returning contents
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("headBucket") || content.contains("HeadBucket"),
                        "HIGH: HeadBucket operation should be implemented for S3 compatibility");
                } catch (Exception e) {
                    fail("Could not verify HeadBucket: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("HeadObject operation implemented")
        void testHeadObjectImplemented() {
            // RED: HeadObject gets metadata without downloading object
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("headObject") || content.contains("HeadObject"),
                        "HIGH: HeadObject operation should be implemented for metadata retrieval");
                } catch (Exception e) {
                    fail("Could not verify HeadObject: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 2 - Multipart Upload (P1 HIGH)")
    class Phase2MultipartUpload {

        @Test
        @DisplayName("InitiateMultipartUpload operation implemented")
        void testInitiateMultipartUploadImplemented() {
            // RED: Large file uploads require multipart support
            Path proxyService = modulePath("Brazz-Nossel",
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            assertTrue(Files.exists(proxyService), 
                "S3ProxyService must exist");
            
            try {
                String content = Files.readString(proxyService);
                assertTrue(content.contains("initiateMultipartUpload"),
                    "HIGH: InitiateMultipartUpload must be implemented for large files");
            } catch (Exception e) {
                fail("Could not verify InitiateMultipartUpload: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("UploadPart operation implemented")
        void testUploadPartImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("uploadPart"),
                        "HIGH: UploadPart must be implemented for multipart uploads");
                } catch (Exception e) {
                    fail("Could not verify UploadPart: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("CompleteMultipartUpload operation implemented")
        void testCompleteMultipartUploadImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("completeMultipartUpload"),
                        "HIGH: CompleteMultipartUpload must be implemented");
                } catch (Exception e) {
                    fail("Could not verify CompleteMultipartUpload: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("AbortMultipartUpload operation implemented")
        void testAbortMultipartUploadImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("abortMultipartUpload"),
                        "HIGH: AbortMultipartUpload must be implemented for cleanup");
                } catch (Exception e) {
                    fail("Could not verify AbortMultipartUpload: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("ListMultipartUploads operation implemented")
        void testListMultipartUploadsImplemented() {
            // RED: List in-progress multipart uploads
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("listMultipartUploads"),
                        "MEDIUM: ListMultipartUploads should be implemented for management");
                } catch (Exception e) {
                    fail("Could not verify ListMultipartUploads: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("ListParts operation implemented")
        void testListPartsImplemented() {
            // RED: List uploaded parts for an in-progress multipart upload
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("listParts"),
                        "MEDIUM: ListParts should be implemented for tracking upload progress");
                } catch (Exception e) {
                    fail("Could not verify ListParts: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 2 - Object Versioning (P1 HIGH)")
    class Phase2ObjectVersioning {

        @Test
        @DisplayName("GetBucketVersioning operation implemented")
        void testGetBucketVersioningImplemented() {
            // RED: Check if versioning is enabled on bucket
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("getBucketVersioning") || content.contains("getVersioning"),
                        "HIGH: GetBucketVersioning must be implemented for version control");
                } catch (Exception e) {
                    fail("Could not verify GetBucketVersioning: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("PutBucketVersioning operation implemented")
        void testPutBucketVersioningImplemented() {
            // RED: Enable/suspend versioning on bucket
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("putBucketVersioning") || content.contains("setVersioning"),
                        "HIGH: PutBucketVersioning must be implemented to enable versioning");
                } catch (Exception e) {
                    fail("Could not verify PutBucketVersioning: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("GetObjectVersion operation implemented")
        void testGetObjectVersionImplemented() {
            // RED: Retrieve specific version of object
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("versionId") || content.contains("getObjectVersion"),
                        "HIGH: GetObject with versionId parameter must be supported");
                } catch (Exception e) {
                    fail("Could not verify GetObjectVersion: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("DeleteObjectVersion operation implemented")
        void testDeleteObjectVersionImplemented() {
            // RED: Delete specific version (not just add delete marker)
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("deleteObjectVersion") || 
                             (content.contains("deleteObject") && content.contains("versionId")),
                        "HIGH: DeleteObject with versionId must permanently delete versions");
                } catch (Exception e) {
                    fail("Could not verify DeleteObjectVersion: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("ListObjectVersions operation implemented")
        void testListObjectVersionsImplemented() {
            // RED: List all versions of objects in bucket
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("listObjectVersions") || content.contains("listVersions"),
                        "HIGH: ListObjectVersions must show all versions and delete markers");
                } catch (Exception e) {
                    fail("Could not verify ListObjectVersions: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 3 - Object Tagging (P2 MEDIUM)")
    class Phase3ObjectTagging {

        @Test
        @DisplayName("PutObjectTagging operation implemented")
        void testPutObjectTaggingImplemented() {
            // RED: Add tags to objects for organization/billing
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("putObjectTagging") || content.contains("setObjectTags"),
                        "MEDIUM: PutObjectTagging should be implemented for metadata management");
                } catch (Exception e) {
                    fail("Could not verify PutObjectTagging: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("GetObjectTagging operation implemented")
        void testGetObjectTaggingImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("getObjectTagging") || content.contains("getObjectTags"),
                        "MEDIUM: GetObjectTagging should retrieve object tags");
                } catch (Exception e) {
                    fail("Could not verify GetObjectTagging: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("DeleteObjectTagging operation implemented")
        void testDeleteObjectTaggingImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("deleteObjectTagging") || content.contains("removeObjectTags"),
                        "MEDIUM: DeleteObjectTagging should remove all tags from object");
                } catch (Exception e) {
                    fail("Could not verify DeleteObjectTagging: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 3 - Bucket Policies (P2 MEDIUM)")
    class Phase3BucketPolicies {

        @Test
        @DisplayName("GetBucketPolicy operation implemented")
        void testGetBucketPolicyImplemented() {
            // RED: Retrieve S3-style bucket policy (different from IronBucket policies)
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("getBucketPolicy"),
                        "MEDIUM: GetBucketPolicy should retrieve AWS S3-compatible bucket policies");
                } catch (Exception e) {
                    fail("Could not verify GetBucketPolicy: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("PutBucketPolicy operation implemented")
        void testPutBucketPolicyImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("putBucketPolicy"),
                        "MEDIUM: PutBucketPolicy should set AWS S3-compatible bucket policies");
                } catch (Exception e) {
                    fail("Could not verify PutBucketPolicy: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("DeleteBucketPolicy operation implemented")
        void testDeleteBucketPolicyImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("deleteBucketPolicy"),
                        "MEDIUM: DeleteBucketPolicy should remove bucket policies");
                } catch (Exception e) {
                    fail("Could not verify DeleteBucketPolicy: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 3 - Object ACLs (P2 MEDIUM)")
    class Phase3ObjectACLs {

        @Test
        @DisplayName("GetObjectAcl operation implemented")
        void testGetObjectAclImplemented() {
            // RED: Get ACL for specific object
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("getObjectAcl") || content.contains("getAcl"),
                        "MEDIUM: GetObjectAcl should retrieve object access control lists");
                } catch (Exception e) {
                    fail("Could not verify GetObjectAcl: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("PutObjectAcl operation implemented")
        void testPutObjectAclImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("putObjectAcl") || content.contains("setAcl"),
                        "MEDIUM: PutObjectAcl should set object access control lists");
                } catch (Exception e) {
                    fail("Could not verify PutObjectAcl: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("GetBucketAcl operation implemented")
        void testGetBucketAclImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("getBucketAcl"),
                        "MEDIUM: GetBucketAcl should retrieve bucket access control lists");
                } catch (Exception e) {
                    fail("Could not verify GetBucketAcl: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("PutBucketAcl operation implemented")
        void testPutBucketAclImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("putBucketAcl"),
                        "MEDIUM: PutBucketAcl should set bucket access control lists");
                } catch (Exception e) {
                    fail("Could not verify PutBucketAcl: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("Phase 3 - Advanced Operations (P3 LOW)")
    class Phase3AdvancedOperations {

        @Test
        @DisplayName("CopyObject operation implemented")
        void testCopyObjectImplemented() {
            // RED: Server-side copy between buckets/keys
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("copyObject"),
                        "LOW: CopyObject should support server-side copy without download/upload");
                } catch (Exception e) {
                    fail("Could not verify CopyObject: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("GetBucketLocation operation implemented")
        void testGetBucketLocationImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("getBucketLocation") || content.contains("getLocation"),
                        "LOW: GetBucketLocation should return bucket region");
                } catch (Exception e) {
                    fail("Could not verify GetBucketLocation: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("ListBuckets operation implemented")
        void testListBucketsImplemented() {
            // RED: List all buckets accessible to user
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("listBuckets"),
                        "MEDIUM: ListBuckets should enumerate all accessible buckets");
                } catch (Exception e) {
                    fail("Could not verify ListBuckets: " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("DeleteBucket operation implemented")
        void testDeleteBucketImplemented() {
            Path proxyService = Paths.get(PROJECT_ROOT, "temp", "Brazz-Nossel", 
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            if (Files.exists(proxyService)) {
                try {
                    String content = Files.readString(proxyService);
                    assertTrue(content.contains("deleteBucket"),
                        "MEDIUM: DeleteBucket should remove empty buckets");
                } catch (Exception e) {
                    fail("Could not verify DeleteBucket: " + e.getMessage());
                }
            }
        }
    }

    @Nested
    @DisplayName("S3 API Completeness Summary")
    class S3APICompletenessSummary {

        @Test
        @DisplayName("S3 API coverage >= 80% of core operations")
        void testS3APICoverageComplete() {
            // This is a meta-test tracking overall S3 API completeness
            // Dynamically checks which operations are actually implemented
            
            Path controllerFile = modulePath("Brazz-Nossel",
                "src", "main", "java", "com", "ironbucket", "brazznossel", "controller", "S3Controller.java");
            Path proxyService = modulePath("Brazz-Nossel",
                "src", "main", "java", "com", "ironbucket", "brazznossel", "service", "S3ProxyService.java");
            
            String controllerContent = "";
            String proxyContent = "";
            
            try {
                if (Files.exists(controllerFile)) {
                    controllerContent = Files.readString(controllerFile);
                }
                if (Files.exists(proxyService)) {
                    proxyContent = Files.readString(proxyService);
                }
            } catch (Exception e) {
                // Files don't exist yet - that's expected in RED state
            }
            
            String allContent = controllerContent + proxyContent;
            
            // Core operations (must have): 7
            int coreImplemented = 0;
            int coreTotal = 7;
            if (allContent.contains("createBucket") || allContent.contains("CreateBucket")) coreImplemented++;
            if (allContent.contains("putObject") || allContent.contains("PutObject")) coreImplemented++;
            if (allContent.contains("getObject") || allContent.contains("GetObject")) coreImplemented++;
            if (allContent.contains("deleteObject") || allContent.contains("DeleteObject")) coreImplemented++;
            if (allContent.contains("listObjects") || allContent.contains("ListObjects")) coreImplemented++;
            if (allContent.contains("headBucket") || allContent.contains("HeadBucket")) coreImplemented++;
            if (allContent.contains("headObject") || allContent.contains("HeadObject")) coreImplemented++;
            
            // Multipart operations (should have): 6
            int multipartImplemented = 0;
            int multipartTotal = 6;
            if (allContent.contains("initiateMultipartUpload")) multipartImplemented++;
            if (allContent.contains("uploadPart")) multipartImplemented++;
            if (allContent.contains("completeMultipartUpload")) multipartImplemented++;
            if (allContent.contains("abortMultipartUpload")) multipartImplemented++;
            if (allContent.contains("listMultipartUploads")) multipartImplemented++;
            if (allContent.contains("listParts")) multipartImplemented++;
            
            // Versioning operations (should have): 5
            int versioningImplemented = 0;
            int versioningTotal = 5;
            if (allContent.contains("getBucketVersioning") || allContent.contains("getVersioning")) versioningImplemented++;
            if (allContent.contains("putBucketVersioning") || allContent.contains("setVersioning")) versioningImplemented++;
            if (allContent.contains("versionId") || allContent.contains("getObjectVersion")) versioningImplemented++;
            if (allContent.contains("deleteObjectVersion") || 
                (allContent.contains("deleteObject") && allContent.contains("versionId"))) versioningImplemented++;
            if (allContent.contains("listObjectVersions") || allContent.contains("listVersions")) versioningImplemented++;
            
            // Advanced features (nice to have): 11
            int advancedImplemented = 0;
            int advancedTotal = 11;
            // Tagging (3)
            if (allContent.contains("putObjectTagging") || allContent.contains("setObjectTags")) advancedImplemented++;
            if (allContent.contains("getObjectTagging") || allContent.contains("getObjectTags")) advancedImplemented++;
            if (allContent.contains("deleteObjectTagging") || allContent.contains("removeObjectTags")) advancedImplemented++;
            // Policies (3)
            if (allContent.contains("getBucketPolicy")) advancedImplemented++;
            if (allContent.contains("putBucketPolicy")) advancedImplemented++;
            if (allContent.contains("deleteBucketPolicy")) advancedImplemented++;
            // ACLs (4)
            if (allContent.contains("getObjectAcl") || allContent.contains("getAcl")) advancedImplemented++;
            if (allContent.contains("putObjectAcl") || allContent.contains("setAcl")) advancedImplemented++;
            if (allContent.contains("getBucketAcl")) advancedImplemented++;
            if (allContent.contains("putBucketAcl")) advancedImplemented++;
            // Other (1) - counted separately below
            if (allContent.contains("copyObject")) advancedImplemented++;
            
            // Calculate weighted completeness
            // Core: 50%, Multipart: 25%, Versioning: 15%, Advanced: 10%
            double coreScore = (coreImplemented / (double) coreTotal) * 50.0;
            double multipartScore = (multipartImplemented / (double) multipartTotal) * 25.0;
            double versioningScore = (versioningImplemented / (double) versioningTotal) * 15.0;
            double advancedScore = (advancedImplemented / (double) advancedTotal) * 10.0;
            
            double totalCompleteness = coreScore + multipartScore + versioningScore + advancedScore;
            
            System.out.println("\n═══════════════════════════════════════════════════════════");
            System.out.println(" S3 API Completeness Score: " + String.format("%.1f%%", totalCompleteness));
            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println(" Core Operations:        " + coreImplemented + "/" + coreTotal + " (" + String.format("%.1f%%", coreScore) + ")");
            System.out.println(" Multipart Upload:       " + multipartImplemented + "/" + multipartTotal + " (" + String.format("%.1f%%", multipartScore) + ")");
            System.out.println(" Versioning:             " + versioningImplemented + "/" + versioningTotal + " (" + String.format("%.1f%%", versioningScore) + ")");
            System.out.println(" Advanced Features:      " + advancedImplemented + "/" + advancedTotal + " (" + String.format("%.1f%%", advancedScore) + ")");
            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println(" Target: 80% for production S3 compatibility");
            System.out.println("═══════════════════════════════════════════════════════════");
            
            assertTrue(totalCompleteness >= 80.0, 
                "S3 API completeness is " + String.format("%.1f%%", totalCompleteness) + 
                ", must be >= 80% for full S3 compatibility!");
        }
    }
}
