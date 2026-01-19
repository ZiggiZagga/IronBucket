# IronBucket Complete Test Report

**Generated:** Mon Jan 19 00:02:17 UTC 2026  
**Duration:** 105s  
**Report ID:** 20260119_000032

---

## Executive Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | 11 |
| **Passed** | **7** ✅ |
| **Failed** | **4** ❌ |
| **Skipped** | 0 |
| **Success Rate** | **63%** |

---

## Test Results

- ❌ Maven_Sentinel-Gear
- ✅ Maven_Brazz-Nossel
- ✅ Maven_Claimspindel
- ✅ Maven_Buzzle-Vane
- ❌ Maven_Pactum-Scroll
- ❌ Infrastructure_Tests
- ❌ E2E_Alice_Bob_Scenario
- ✅ Observability_Loki
- ✅ Observability_Tempo
- ✅ Observability_Grafana
- ✅ Observability_Loki_Labels

---

## Failed Tests Details

### Maven_Sentinel-Gear

**Log:** `/workspaces/IronBucket/test-results/logs/Maven_Sentinel-Gear_20260119_000032.log`

```
[[31;1mERROR[0m] [1;31m  GovernanceIntegrityResilienceTest.testTamperAndReplayDetection Add tamper/replay detection that rejects forged payloads and raises high-priority alerts ==> expected: <true> but was: <false>[m
[[31;1mERROR[0m] [1;31m  GovernanceIntegrityResilienceTest.testVersioningAndDeleteMarkersPreserved Add e2e that migrates buckets with versioning enabled and asserts delete markers and versions are preserved ==> expected: <true> but was: <false>[m
[[31;1mERROR[0m] [1;31m  GraphQLFeaturesTest.testCreatePolicyImplemented CRITICAL: PolicyMutationResolver must exist ==> expected: <true> but was: <false>[m
[[31;1mERROR[0m] [1;31m  GraphQLFeaturesTest.testGraphQLAPICoverageComplete GraphQL API completeness is 0.0%, must be >= 75% for full management plane! ==> expected: <true> but was: <false>[m
[[31;1mERROR[0m] [1;31m  GraphQLFeaturesTest.testGraphQLSchemaExists CRITICAL: GraphQL schema file must exist at /workspaces/IronBucket/Sentinel-Gear/temp/Graphite-Forge/src/main/resources/graphql/schema.graphqls ==> expected: <true> but was: <false>[m
[[31;1mERROR[0m] [1;31m  S3FeaturesTest.testCreateBucketImplemented CRITICAL: S3Controller must exist ==> expected: <true> but was: <false>[m
[[31;1mERROR[0m] [1;31m  S3FeaturesTest.testInitiateMultipartUploadImplemented S3ProxyService must exist ==> expected: <true> but was: <false>[m
[[31;1mERROR[0m] [1;31m  S3FeaturesTest.testS3APICoverageComplete S3 API completeness is 0.0%, must be >= 80% for full S3 compatibility! ==> expected: <true> but was: <false>[m
[[31;1mERROR[0m] [1;31mTests run: 131, Failures: 28, Errors: 0, Skipped: 0[m
[[31;1mERROR[0m] Failed to execute goal [32morg.apache.maven.plugins:maven-surefire-plugin:3.5.4:test[0m [1m(default-test)[0m on project [36msentinelgear[0m: [31;1mThere are test failures.[m
[[31;1mERROR[0m] [31;1m[m
[[31;1mERROR[0m] [31;1mSee /workspaces/IronBucket/Sentinel-Gear/target/surefire-reports for the individual test results.[m
[[31;1mERROR[0m] [31;1mSee dump files (if any exist) [date].dump, [date]-jvmRun[N].dump and [date].dumpstream.[0m[m
[[31;1mERROR[0m] [0m-> [1m[Help 1][0m[m
[[31;1mERROR[0m] 
[[31;1mERROR[0m] To see the full stack trace of the errors, re-run Maven with the '[1m-e[0m' switch
[[31;1mERROR[0m] Re-run Maven using the '[1m-X[0m' switch to enable verbose output
[[31;1mERROR[0m] 
[[31;1mERROR[0m] For more information about the errors and possible solutions, please read the following articles:
[[31;1mERROR[0m] [1m[Help 1][0m http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
```

### Maven_Pactum-Scroll

**Log:** `/workspaces/IronBucket/test-results/logs/Maven_Pactum-Scroll_20260119_000032.log`

```
[[31;1mERROR[0m] The goal you specified requires a project to execute but there is no POM in this directory (/workspaces/IronBucket/Pactum-Scroll). Please verify you invoked Maven from the correct directory. -> [1m[Help 1][0m[m
[[31;1mERROR[0m] 
[[31;1mERROR[0m] To see the full stack trace of the errors, re-run Maven with the '[1m-e[0m' switch
[[31;1mERROR[0m] Re-run Maven using the '[1m-X[0m' switch to enable verbose output
[[31;1mERROR[0m] 
[[31;1mERROR[0m] For more information about the errors and possible solutions, please read the following articles:
[[31;1mERROR[0m] [1m[Help 1][0m http://cwiki.apache.org/confluence/display/MAVEN/MissingProjectException
```

### Infrastructure_Tests

**Log:** `/workspaces/IronBucket/test-results/logs/Infrastructure_Tests_20260119_000032.log`

```
sh: LAST_CAT: unknown operand
sh: LAST_CAT: unknown operand
[0;32m✅ Markdown Report generated: /tmp/ironbucket-complete-test-suite/reports/README.md[0m

[0;35m═══════════════════════════════════════════════════════════════[0m

[0;36mCOMPLETE TEST SUITE FINISHED[0m

  [0;32m✅ Total Tests: 18[0m
  [0;32m✅ Passed: 16[0m
  [0;31m❌ Failed: 2[0m

[0;36mReports Available:[0m
  📊 HTML: /tmp/ironbucket-complete-test-suite/reports/index.html
  📝 Markdown: /tmp/ironbucket-complete-test-suite/reports/README.md

[0;36mAll Data:[0m
  📁 /tmp/ironbucket-complete-test-suite/

[0;35m═══════════════════════════════════════════════════════════════[0m
```

### E2E_Alice_Bob_Scenario

**Log:** `/workspaces/IronBucket/test-results/logs/E2E_Alice_Bob_Scenario_20260119_000032.log`

```
[0;34m╔══════════════════════════════════════════════════════════════════╗[0m
[0;34m║                                                                  ║[0m
[0;34m║           E2E TEST: Alice & Bob Multi-Tenant Scenario            ║[0m
[0;34m║                                                                  ║[0m
[0;34m║  Proving: IronBucket is PRODUCTION READY                        ║[0m
[0;34m║                                                                  ║[0m
[0;34m╚══════════════════════════════════════════════════════════════════╝[0m

[0;34m=== PHASE 1: Infrastructure Verification ===[0m

Checking Keycloak (OIDC Provider)...
```

---

## Test Phases

### Phase 1: Maven Backend Tests
- **Purpose:** Validate unit and integration tests for all microservices
- **Modules Tested:** Sentinel-Gear, Brazz-Nossel, Claimspindel, Buzzle-Vane, Pactum-Scroll
- **Framework:** Maven + JUnit

### Phase 2: Infrastructure & Service Tests
- **Purpose:** Validate service discovery, health endpoints, connectivity
- **Tests:** Gateway, Keycloak, MinIO, Eureka, Health probes
- **Framework:** Shell scripts in test-client container

### Phase 3: E2E Alice-Bob Multi-Tenant Scenario
- **Purpose:** Prove production-ready multi-tenant isolation
- **Tests:** Authentication, Authorization, File Upload, Security policies
- **Framework:** Bash + Keycloak + S3

### Phase 4: Observability Stack Validation
- **Purpose:** Validate logging, tracing, metrics collection
- **Components:** Loki, Tempo, Grafana, Promtail, OTEL Collector
- **Framework:** Container-based health checks

### Phase 5: Artifact Collection
- **Purpose:** Collect observability data for analysis
- **Artifacts:** Logs, traces, metrics, service logs

---

## Observability Artifacts

All artifacts available in: `/workspaces/IronBucket/test-results/artifacts/`

| Artifact | File | Status |
|----------|------|--------|
| Loki Labels | loki-labels.json | ✅ Collected |
| Tempo Traces | tempo-traces.json | ✅ Collected |
| Gateway Metrics | gateway-metrics.json | ✅ Collected |
| Service Logs | *-logs.txt | 4 files |

---

## Architecture Validation

✅ **Service Discovery:** All services registered in Eureka  
✅ **Health Endpoints:** All services expose /actuator/health  
✅ **API Gateway:** Sentinel-Gear routing all traffic  
✅ **Authentication:** Keycloak OIDC provider operational  
✅ **Storage:** MinIO S3-compatible storage ready  
✅ **Observability:** Loki, Tempo, Grafana, Mimir active  
✅ **Logging:** Promtail collecting logs from all containers  
✅ **Tracing:** OTEL Collector exporting traces to Tempo  
✅ **Metrics:** OTEL Collector exporting metrics to Mimir  

---

## How to Access Results

### View This Report
```bash
cat /workspaces/IronBucket/test-results/reports/COMPLETE-TEST-REPORT-20260119_000032.md
```

### View Test Logs
```bash
ls -la /workspaces/IronBucket/test-results/logs/
```

### View Observability Artifacts
```bash
ls -la /workspaces/IronBucket/test-results/artifacts/
cat /workspaces/IronBucket/test-results/artifacts/loki-labels.json
```

### Re-run Tests
```bash
cd /workspaces/IronBucket
bash run-all-tests-complete.sh
```

---

## Conclusion

⚠️ **4 test(s) failed**

Review failed test logs above for details.
All logs available in: `/workspaces/IronBucket/test-results/logs/`

---

**Report Generated:** Mon Jan 19 00:02:17 UTC 2026  
**Total Duration:** 105s  
**Report Location:** `/workspaces/IronBucket/test-results/reports/COMPLETE-TEST-REPORT-20260119_000032.md`

