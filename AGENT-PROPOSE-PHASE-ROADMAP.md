# IronBucket Coder Agent - Propose Phase: Production-Readiness Roadmap

**Date**: January 19, 2026  
**Agent**: AI Architecture, Implementation & Production-Readiness Agent  
**Phase**: PROPOSE (Implementation Planning)  
**Status**: 🔴 IN PROGRESS

---

## Executive Summary

Three critical security blockers prevent production deployment. This roadmap prioritizes correctness and security over speed using test-first methodology. **Estimated total timeline: 4-5 weeks** with dedicated 2-3 person team.

---

## P0 Critical Blockers (Must Fix Before Production)

### 1️⃣ **Network Isolation: Kubernetes NetworkPolicies**

**Current State**: ❌ No network policies deployed. All services can communicate with all other services.

**Risk**: 
- Compromised service can access all other services
- No segmentation between frontend/backend/internal
- Data exfiltration possible

**Objective**: Deploy deny-all default with explicit allow policies between services.

#### Implementation Plan

**Phase 1A: Network Policy Infrastructure (2 days)**
- [ ] Create `k8s/network-policies/` directory structure
- [ ] Define 3 network tiers:
  - `tier-ingress`: External traffic only
  - `tier-service`: Inter-service communication
  - `tier-infrastructure`: System services (PostgreSQL, Keycloak, MinIO)
- [ ] Create base NetworkPolicy template (egress/ingress rules)

**Test-First (Write tests BEFORE code):**
```bash
# Test: Verify policies are correctly templated
- [ ] Validate NetworkPolicy YAML syntax
- [ ] Test: Verify deny-all ingress is default
- [ ] Test: Verify explicit allows work (port + protocol)
```

**Phase 1B: Service-to-Service Policies (2 days)**
- [ ] Sentinel-Gear (8080) ← Ingress only
  ```yaml
  Ingress:
    - from: ingress-controller (port 8080)
  Egress:
    - to: Claimspindel (8081)
    - to: Brazz-Nossel (8082)
    - to: Buzzle-Vane (8083)
    - to: Keycloak (8080)
  ```

- [ ] Claimspindel (8081) ← Internal only
  ```yaml
  Ingress:
    - from: Sentinel-Gear
  Egress:
    - to: PostgreSQL (5432)
    - to: Buzzle-Vane (8083)
  ```

- [ ] Brazz-Nossel (8082) ← Internal only
  ```yaml
  Ingress:
    - from: Sentinel-Gear
  Egress:
    - to: MinIO (9000)
    - to: PostgreSQL (5432)
    - to: Buzzle-Vane (8083)
  ```

- [ ] Buzzle-Vane (8083) ← Internal only (Eureka)
  ```yaml
  Ingress:
    - from: Sentinel-Gear
    - from: Claimspindel
    - from: Brazz-Nossel
  Egress:
    - none (read-only registry access)
  ```

**Test-First:**
```bash
# Test: Verify each service can only communicate with allowed targets
- [ ] mvn test: SentinelGearNetworkPolicyTest
  - Can reach Claimspindel:8081? ✓
  - Can reach Brazz-Nossel:8082? ✓
  - Can reach PostgreSQL:5432? ✗ (should fail)
  
- [ ] mvn test: ClaimspindelNetworkPolicyTest
  - Can reach PostgreSQL:5432? ✓
  - Can reach Sentinel-Gear:8080? ✗ (should fail)
  
- [ ] mvn test: BrazzNosselNetworkPolicyTest
  - Can reach MinIO:9000? ✓
  - Can reach external services? ✗ (should fail)
```

**Phase 1C: Infrastructure Service Policies (1 day)**
- [ ] PostgreSQL (5432) ← Claimspindel + Brazz-Nossel only
- [ ] MinIO (9000) ← Brazz-Nossel only
- [ ] Keycloak (8080) ← Sentinel-Gear only

**Test-First:**
```bash
- [ ] Integration test: Verify PostgreSQL rejects connections from Sentinel-Gear
- [ ] Integration test: Verify MinIO rejects connections from Claimspindel
```

**Effort**: 5 days | **Expertise**: Kubernetes networking | **Complexity**: Medium

---

### 2️⃣ **Credential Management: HashiCorp Vault Integration**

**Current State**: ❌ Hardcoded defaults everywhere.
```java
// Anti-pattern examples found:
HMAC_SECRET = "development-secret-key"
JDBC_URL = "jdbc:postgresql://localhost:5432/ironbucket"
MINIO_ACCESS_KEY = "minioadmin"
KEYCLOAK_CLIENT_SECRET = hardcoded in config
```

**Risk**:
- Credentials exposed in git history
- No credential rotation capability
- Same credentials in dev/staging/prod
- Audit trail missing

**Objective**: All secrets from Vault, no hardcoded values in code/config.

#### Implementation Plan

**Phase 2A: Vault Infrastructure Setup (1 day)**
- [ ] Deploy Vault in Docker Compose (dev environment)
  ```bash
  # test-docker-vault.sh
  - Start Vault in dev mode
  - Initialize auth methods (JWT, AppRole)
  - Create secret paths for each service
  ```

- [ ] Configure Vault secrets structure:
  ```
  secret/
    data/
      ironbucket/
        sentinel-gear/
          - oidc-client-secret
          - hmac-key
          - jwt-signing-key
        brazz-nossel/
          - minio-access-key
          - minio-secret-key
          - s3-credentials
        claimspindel/
          - database-user
          - database-password
          - audit-db-credentials
        shared/
          - postgres-admin-password
          - minio-admin-password
          - keycloak-admin-password
  ```

**Test-First:**
```bash
- [ ] mvn test: VaultConnectionTest
  - Can connect to Vault? ✓
  - Can authenticate via JWT? ✓
  - Can read secrets? ✓

- [ ] mvn test: VaultSecretRotationTest
  - Can rotate API keys? ✓
  - Services pick up new secrets? ✓
```

**Phase 2B: Spring Boot Vault Integration (2 days)**
- [ ] Add Vault dependency to each service:
  ```xml
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-vault-config</artifactId>
  </dependency>
  ```

- [ ] Sentinel-Gear vault configuration:
  ```yaml
  # application.yml
  spring:
    cloud:
      vault:
        uri: ${VAULT_ADDR:http://localhost:8200}
        token: ${VAULT_TOKEN}
        authentication: JWT
        jwt:
          role: sentinel-gear
  ```

- [ ] Refactor JWT/OIDC configuration:
  ```java
  // BEFORE (hardcoded):
  private static final String HMAC_SECRET = "dev-secret";
  
  // AFTER (from Vault):
  @Value("${vault.hmac.secret}")
  private String hmacSecret;
  ```

- [ ] Apply same pattern to Claimspindel, Brazz-Nossel

**Test-First:**
```bash
- [ ] mvn test: SentinelGearVaultIntegrationTest
  - Loads HMAC secret from Vault? ✓
  - JWT validation uses Vault secret? ✓
  - Secret rotation works? ✓

- [ ] mvn test: BrazzNosselVaultIntegrationTest
  - Loads MinIO credentials from Vault? ✓
  - S3 operations use correct credentials? ✓
```

**Phase 2C: Database Secrets Management (1.5 days)**
- [ ] Remove hardcoded JDBC credentials
- [ ] Configure PostgreSQL auth via Vault:
  ```yaml
  spring:
    datasource:
      url: ${vault.database.jdbc-url}
      username: ${vault.database.username}
      password: ${vault.database.password}
  ```

- [ ] Implement credential rotation for database users
  ```java
  @Scheduled(fixedDelay = 3600000) // 1 hour
  public void rotateDbCredentials() {
    // Fetch new credentials from Vault
    // Update DataSource
    // Log rotation event
  }
  ```

**Test-First:**
```bash
- [ ] mvn test: DatabaseCredentialsTest
  - Connects to PostgreSQL with Vault credentials? ✓
  - Credential rotation doesn't break connections? ✓
  - Audit logging works? ✓
```

**Phase 2D: CI/CD Integration (1 day)**
- [ ] GitHub Actions: Vault JWT authentication
  ```yaml
  # .github/workflows/deploy.yml
  - name: Authenticate to Vault
    uses: hashicorp/vault-action@v2
    with:
      method: jwt
      role: github-actions
      jwtGithubAudience: vault.ironbucket.local
  ```

- [ ] Inject secrets into deployment environment
- [ ] Verify no credentials logged in CI output

**Test-First:**
```bash
- [ ] CI test: Verify CI can authenticate to Vault
- [ ] CI test: Verify secrets not in logs
- [ ] CI test: Verify deployment with correct secrets succeeds
```

**Effort**: 5.5 days | **Expertise**: Spring Vault, HashiCorp Vault | **Complexity**: High

---

### 3️⃣ **TLS/mTLS Everywhere**

**Current State**: ❌ All HTTP, no encryption or mutual authentication.
```
Client → Sentinel-Gear (HTTP 8080)
Sentinel-Gear → Claimspindel (HTTP 8081)
Claimspindel → PostgreSQL (plaintext TCP 5432)
Brazz-Nossel → MinIO (HTTP 9000)
```

**Risk**:
- Network traffic unencrypted (MITM attacks)
- No service authentication (spoofing possible)
- Data in transit exposed
- Compliance violations (PCI-DSS, HIPAA, etc.)

**Objective**: 
- External: TLS 1.3 for Sentinel-Gear ingress
- Internal: mTLS for service-to-service communication
- Database: TLS for PostgreSQL connections

#### Implementation Plan

**Phase 3A: Certificate Infrastructure (1.5 days)**
- [ ] Create `k8s/tls/` directory structure
- [ ] Generate root CA (self-signed for dev)
  ```bash
  # scripts/generate-ca.sh
  openssl genrsa -out ca.key 4096
  openssl req -x509 -new -nodes -key ca.key -days 3650 -out ca.crt
  ```

- [ ] Create certificate templates for each service:
  ```bash
  # Each service gets:
  - sentinel-gear.crt / sentinel-gear.key (external TLS)
  - sentinel-gear-client.crt (mTLS client certificate)
  
  # Same for Claimspindel, Brazz-Nossel, Buzzle-Vane
  ```

- [ ] Store certificates in Kubernetes Secrets:
  ```bash
  kubectl create secret tls sentinel-gear-tls \
    --cert=sentinel-gear.crt \
    --key=sentinel-gear.key
  ```

**Test-First:**
```bash
- [ ] mvn test: CertificateGenerationTest
  - Certificates are valid? ✓
  - CA chain verifiable? ✓
  - All services have certs? ✓

- [ ] Integration test: CertificateExpiryTest
  - Check certificates expire in >90 days? ✓
  - Alert if <30 days remaining? ✓
```

**Phase 3B: Sentinel-Gear TLS Termination (1.5 days)**
- [ ] Configure Spring Boot TLS:
  ```yaml
  # application.yml
  server:
    ssl:
      enabled: true
      key-store-type: PKCS12
      key-store: classpath:keystore.p12
      key-store-password: ${KEYSTORE_PASSWORD}
      key-alias: sentinel-gear
  ```

- [ ] Update ingress to use HTTPS:
  ```yaml
  # k8s/ingress.yaml
  apiVersion: networking.k8s.io/v1
  kind: Ingress
  spec:
    tls:
    - hosts:
      - sentinel-gear.ironbucket.local
      secretName: sentinel-gear-tls
  ```

- [ ] Redirect HTTP → HTTPS:
  ```java
  @Configuration
  public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
      http.redirectToHttps();
      return http.build();
    }
  }
  ```

**Test-First:**
```bash
- [ ] mvn test: SentinelGearTLSTest
  - HTTPS endpoint available? ✓
  - HTTP requests redirected? ✓
  - Valid certificate presented? ✓
  - TLS 1.3 enabled? ✓

- [ ] Integration test: ClientCertificateValidationTest
  - Self-signed cert accepted in test env? ✓
  - Invalid cert rejected? ✓
```

**Phase 3C: Service-to-Service mTLS (2 days)**
- [ ] Claimspindel → PostgreSQL (TLS + client certificate)
  ```java
  // BrazzNosselS3Config.java
  @Configuration
  public class PostgresSSLConfig {
    @Bean
    public DataSource dataSource() {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl("jdbc:postgresql://postgres:5432/ironbucket?sslmode=require&sslcert=...");
      return new HikariDataSource(config);
    }
  }
  ```

- [ ] Brazz-Nossel → MinIO (TLS + client certificate)
  ```java
  // BrazzNosselS3Config.java
  @Bean
  public MinioClient minioClient() {
    return MinioClient.builder()
      .endpoint("https://minio:9000")
      .credentials(accessKey, secretKey)
      .build();
  }
  ```

- [ ] Service-to-Service via Spring Cloud OpenFeign with mTLS:
  ```java
  @Configuration
  public class MTLSConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
      return builder
        .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(
          createHttpClientWithMTLS()
        ))
        .build();
    }
    
    private CloseableHttpClient createHttpClientWithMTLS() {
      // Load client certificate from Kubernetes secret
      // Configure trust store with CA
      // Return HTTP client with mTLS
    }
  }
  ```

**Test-First:**
```bash
- [ ] mvn test: ServiceToServiceMTLSTest
  - Sentinel-Gear can call Claimspindel via mTLS? ✓
  - Connection rejected with invalid cert? ✓
  - Certificate validation works? ✓

- [ ] mvn test: DatabaseTLSConnectionTest
  - Claimspindel connects to PostgreSQL via TLS? ✓
  - Database operations work over encrypted connection? ✓

- [ ] mvn test: S3EncryptedConnectionTest
  - Brazz-Nossel connects to MinIO via HTTPS? ✓
  - S3 operations work over encrypted connection? ✓
```

**Phase 3D: Certificate Rotation & Monitoring (1 day)**
- [ ] Implement cert-manager for automatic renewal
  ```yaml
  # k8s/cert-manager.yaml
  apiVersion: cert-manager.io/v1
  kind: Certificate
  metadata:
    name: sentinel-gear-cert
  spec:
    secretName: sentinel-gear-tls
    renewBefore: 720h # 30 days
  ```

- [ ] Add certificate expiry monitoring:
  ```java
  @Component
  public class CertificateExpiryMonitor {
    @Scheduled(fixedDelay = 86400000) // Daily
    public void checkCertificateExpiry() {
      // Check all certificates
      // Alert if expiry < 30 days
      // Log rotation timestamp
    }
  }
  ```

**Test-First:**
```bash
- [ ] mvn test: CertificateRotationTest
  - Cert rotation doesn't break services? ✓
  - New certificate used immediately? ✓

- [ ] Observability test: CertificateExpiryAlertTest
  - Alert triggered when cert expires < 30 days? ✓
  - Alert includes certificate details? ✓
```

**Effort**: 6 days | **Expertise**: Spring TLS/SSL, mTLS, certificate management | **Complexity**: High

---

## Implementation Dependencies & Sequencing

```
Network Isolation (5 days)
    ↓ (prerequisite: services must exist)
Credential Management (5.5 days)
    ↓ (can parallel start with Phase 3A)
TLS/mTLS (6 days)
    
TOTAL: 16.5 days = 3.3 weeks
```

**Optimized Timeline (Parallel Execution)**:
- Week 1: Network Isolation (5 days) + TLS Phase 3A (1.5 days in parallel)
- Week 2: Credential Management (5.5 days) + TLS Phase 3B-D (4.5 days in parallel)
- Week 3: Final integration testing + deployment prep

---

## Critical Path Issues & Mitigation

### Issue 1: Certificate Generation Timing
**Risk**: Self-signed certs expire every 90 days, breaks dev/test.
**Mitigation**: Use cert-manager for automatic renewal.

### Issue 2: Vault High Availability
**Risk**: Single Vault instance = single point of failure in production.
**Mitigation**: 
- Dev: Run Vault in dev mode (fine for testing)
- Production: Use HA Vault setup with auto-unseal

### Issue 3: Backward Compatibility
**Risk**: Existing deployments expect HTTP endpoints.
**Mitigation**: Gradual rollout with feature flags:
```yaml
security:
  tls:
    enabled: ${TLS_ENABLED:false}
  mtls:
    enabled: ${MTLS_ENABLED:false}
```

### Issue 4: Testing in Isolated Networks
**Risk**: NetworkPolicies break test containers from reaching services.
**Mitigation**: Create `test` namespace with relaxed policies, or use `hostNetwork: true` for test pods.

---

## Success Criteria

### Network Isolation ✅
- [ ] All NetworkPolicies deployed
- [ ] Services only reach allowed targets
- [ ] Cross-service access denied = verified in integration tests
- [ ] Ingress traffic works (external → Sentinel-Gear)

### Credential Management ✅
- [ ] Zero hardcoded secrets in code
- [ ] All secrets from Vault
- [ ] Credential rotation works without service restart
- [ ] Audit trail complete (Vault logs)

### TLS/mTLS ✅
- [ ] External traffic encrypted (TLS 1.3)
- [ ] Service-to-service authenticated (mTLS)
- [ ] Database connection encrypted
- [ ] Certificate expiry monitoring in place
- [ ] No mixed HTTP/HTTPS endpoints

---

## Resource Requirements

**Personnel**: 2-3 engineers
- 1 Security/DevOps specialist (Network, Vault)
- 1-2 Backend engineers (Spring Boot integration)

**Infrastructure**:
- Kubernetes cluster (minikube for dev)
- HashiCorp Vault (managed or self-hosted)
- Docker registry for images
- GitHub Actions for CI/CD

**Time**: 3-4 weeks

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Services unavailable during TLS migration | HIGH | Feature flags + gradual rollout |
| Certificate expiry causes outages | HIGH | cert-manager + monitoring |
| Vault single point of failure | HIGH | HA setup for production |
| Network policies break existing deployments | MEDIUM | Test policies in test namespace first |
| Performance impact from mTLS | MEDIUM | Benchmarking + connection pooling |

---

## Next Phase: IMPLEMENT

Once PROPOSE is approved:
1. Create feature branches for each blocker (network-isolation, vault-integration, tls-everywhere)
2. Write failing tests first
3. Implement features to make tests pass
4. Integration testing
5. Deployment to staging environment
6. Final security audit before production

---

## Reference Documentation

- Kubernetes NetworkPolicies: https://kubernetes.io/docs/concepts/services-networking/network-policies/
- Spring Vault: https://spring.io/projects/spring-vault
- HashiCorp Vault: https://www.vaultproject.io/
- Spring TLS/SSL: https://spring.io/blog/2023/06/07/securing-spring-boot-applications-with-tls
- mTLS Guide: https://12factor.net/backing-services
