# 🔐 IronBucket & HashiCorp Vault Integration Guide

**Version**: 1.0  
**Date**: January 16, 2026  
**Status**: PRODUCTION-READY INTEGRATION  
**Audience**: DevOps, Security, Platform Engineers

---

## Executive Summary

This guide integrates **HashiCorp Vault** with IronBucket for secure credential management, secret rotation, and compliance auditing.

**Benefits**:
- ✅ Centralized secret storage (no hardcoded credentials)
- ✅ Automatic secret rotation (every 90 days)
- ✅ Detailed audit logging (who accessed what secret)
- ✅ Lease management (automatic credential lifecycle)
- ✅ Kubernetes integration (Vault Agent sidecar injection)

---

## 1. Architecture Overview

### 1.1 Integration Pattern

```
┌─────────────────────────────────────────────────────────┐
│                  Kubernetes Cluster                     │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Sentinel-Gear Pod                               │  │
│  │  ┌────────────────────────────────────────────┐  │  │
│  │  │ Vault Agent (Init Container)              │  │  │
│  │  │ - Fetch secrets from Vault                 │  │  │
│  │  │ - Store in mounted tmpfs volume            │  │  │
│  │  │ - Template secrets for app                 │  │  │
│  │  └────────────────────────────────────────────┘  │  │
│  │                        ↓                          │  │
│  │  ┌────────────────────────────────────────────┐  │  │
│  │  │ Spring Boot Application                    │  │  │
│  │  │ - Read secrets from mounted volume         │  │  │
│  │  │ - Cache in-memory for performance          │  │  │
│  │  │ - Use for database/S3 access               │  │  │
│  │  └────────────────────────────────────────────┘  │  │
│  │                        │                          │  │
│  └────────────────────────┼──────────────────────────┘  │
│                           │                             │
│                           ↓                             │
│  ┌─────────────────────────────────────────────────┐   │
│  │ HashiCorp Vault (Central Secret Manager)       │   │
│  │ ┌──────────────────────────────────────────┐   │   │
│  │ │ Kubernetes Auth (JWT validation)         │   │   │
│  │ │ Database Secrets Engine (PostgreSQL)     │   │   │
│  │ │ AWS Auth (For cloud deployments)         │   │   │
│  │ │ KV v2 Secrets (Static secrets)           │   │   │
│  │ └──────────────────────────────────────────┘   │   │
│  │          ↓                                      │   │
│  │  PostgreSQL / AWS / S3                         │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Secret Types

| Secret Type | Storage | Rotation | Use Case |
|-------------|---------|----------|----------|
| **Database Credentials** | Vault Database Engine | Automatic (30 days) | PostgreSQL user/password |
| **S3 Credentials** | KV v2 Secrets | Manual (90 days) | AWS/MinIO access keys |
| **API Keys** | KV v2 Secrets | Manual (90 days) | Third-party service keys |
| **TLS Certificates** | Vault PKI Engine | Automatic (30 days before expiry) | mTLS, HTTPS |
| **Encryption Keys** | Vault Transit Engine | Manual (90 days) | Data encryption keys |

---

## 2. Vault Setup (Self-Hosted)

### 2.1 Installation

```bash
# Install Vault
curl -fsSL https://apt.releases.hashicorp.com/gpg | \
  sudo apt-key add -
sudo apt-add-repository "deb [arch=amd64] https://apt.releases.hashicorp.com \
  $(lsb_release -cs) main"
sudo apt-get update && sudo apt-get install vault

# Or use Docker
docker run -d \
  --name vault \
  --cap-add IPC_LOCK \
  -p 8200:8200 \
  -e VAULT_DEV_ROOT_TOKEN_ID=root \
  vault server -dev

# Verify
vault status
```

### 2.2 Initialize & Unseal Vault (First Time)

```bash
# Initialize Vault
vault operator init \
  -key-shares=5 \
  -key-threshold=3 \
  -format=json > vault-init.json

# Store keys securely (encrypted, offline)
cat vault-init.json | jq '.unseal_keys_b64[]' | \
  while read key; do echo "$key" | \
  openssl enc -aes-256-cbc -out key.enc; done

# Unseal Vault (need 3 of 5 keys)
vault operator unseal $(jq -r '.unseal_keys_b64[0]' vault-init.json)
vault operator unseal $(jq -r '.unseal_keys_b64[1]' vault-init.json)
vault operator unseal $(jq -r '.unseal_keys_b64[2]' vault-init.json)

# Login with root token (for setup only)
vault login $(jq -r '.root_token' vault-init.json)

# Verify Vault is unsealed
vault status
```

### 2.3 Enable Auth Methods

```bash
# 1. Kubernetes Auth (for pod authentication)
vault auth enable kubernetes

vault write auth/kubernetes/config \
  token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token \
  kubernetes_host="https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt

# 2. AppRole Auth (for CI/CD)
vault auth enable approle

vault write auth/approle/role/ironbucket-ci \
  token_num_uses=0 \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0 \
  secret_id_num_uses=0 \
  policies="ironbucket-ci"

# 3. AWS Auth (for cloud deployments)
vault auth enable aws

vault write auth/aws/config/client \
  access_key=$AWS_ACCESS_KEY_ID \
  secret_key=$AWS_SECRET_ACCESS_KEY
```

### 2.4 Enable Secret Engines

```bash
# 1. KV v2 Secrets (static secrets)
vault secrets enable -path=secret kv-v2

# 2. Database Secrets Engine (dynamic credentials)
vault secrets enable database

# 3. PKI Secrets Engine (TLS certificates)
vault secrets enable pki
vault secrets tune -max-lease-ttl=87600h pki

# 4. Transit Secrets Engine (data encryption)
vault secrets enable transit
```

---

## 3. Secret Configuration

### 3.1 PostgreSQL Dynamic Credentials

```bash
# Configure PostgreSQL connection
vault write database/config/postgresql \
  plugin_name=postgresql-database-plugin \
  allowed_roles="ironbucket-app,ironbucket-readonly" \
  connection_url="postgresql://{{username}}:{{password}}@postgres:5432/ironbucket" \
  username="vault-admin" \
  password="$(openssl rand -base64 32)"

# Create role: Read-write access
vault write database/roles/ironbucket-app \
  db_name=postgresql \
  creation_statements="CREATE USER \"{{name}}\" WITH PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
  GRANT CONNECT ON DATABASE ironbucket TO \"{{name}}\"; \
  GRANT USAGE ON SCHEMA public TO \"{{name}}\"; \
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\"; \
  GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"

# Create role: Read-only access
vault write database/roles/ironbucket-readonly \
  db_name=postgresql \
  creation_statements="CREATE USER \"{{name}}\" WITH PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
  GRANT CONNECT ON DATABASE ironbucket TO \"{{name}}\"; \
  GRANT USAGE ON SCHEMA public TO \"{{name}}\"; \
  GRANT SELECT ON ALL TABLES IN SCHEMA public TO \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"

# Test: Fetch a credential
vault read database/creds/ironbucket-app
# Output:
# Key                Value
# ---                -----
# lease_id           database/creds/ironbucket-app/...
# lease_duration     1h
# lease_renewable    true
# password           <random password>
# username           v-kubernetes-ironb-...
```

### 3.2 S3 Credentials (Static KV)

```bash
# Store AWS S3 credentials
vault kv put secret/s3-credentials \
  access-key-id="AKIA..." \
  secret-access-key="..." \
  region="us-east-1" \
  bucket="ironbucket-data"

# Store MinIO credentials
vault kv put secret/minio-credentials \
  access-key="minioadmin" \
  secret-key="..." \
  endpoint="https://minio:9000" \
  bucket="ironbucket"

# Retrieve (test)
vault kv get secret/s3-credentials
```

### 3.3 API Keys & Service Secrets

```bash
# Keycloak client credentials
vault kv put secret/keycloak \
  client-id="ironbucket-app" \
  client-secret="..." \
  realm="ironbucket" \
  auth-server-url="https://keycloak:8443"

# JWT signing keys
vault kv put secret/jwt \
  issuer="https://keycloak:8443/..." \
  jwks-uri="https://keycloak:8443/.../certs"

# TLS certificates
vault kv put secret/tls \
  cert="-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----" \
  key="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
```

---

## 4. Kubernetes Integration

### 4.1 Kubernetes Auth Configuration

```bash
# Create ServiceAccount for IronBucket
kubectl create serviceaccount ironbucket -n ironbucket

# Create RBAC binding (token reviewer)
kubectl create clusterrole vault-auth \
  --verb=create \
  --resource=tokenreviews
kubectl create clusterrolebinding vault-auth \
  --clusterrole=vault-auth \
  --serviceaccount=ironbucket:vault

# Configure Kubernetes auth in Vault
vault write auth/kubernetes/role/ironbucket \
  bound_service_account_names=ironbucket \
  bound_service_account_namespaces=ironbucket \
  policies=ironbucket-app \
  ttl=1h
```

### 4.2 Vault Agent Injector (Automatic Secret Injection)

```bash
# Install Vault Helm Chart
helm repo add hashicorp https://helm.releases.hashicorp.com
helm repo update

helm install vault hashicorp/vault \
  --namespace vault \
  --create-namespace \
  --values vault-helm-values.yaml

# Enable Vault Agent Injection
vault write auth/kubernetes/config \
  kubernetes_host="https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
  token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token

# Verify injection controller running
kubectl get deployment -n vault vault-agent-injector
```

### 4.3 Pod Configuration with Vault Annotations

```yaml
# kubernetes/sentinel-gear-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sentinel-gear
  namespace: ironbucket
spec:
  replicas: 3
  selector:
    matchLabels:
      app: sentinel-gear
  template:
    metadata:
      labels:
        app: sentinel-gear
      annotations:
        # Vault Agent Injector annotations
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "ironbucket"
        vault.hashicorp.com/agent-inject-secret-database: "database/creds/ironbucket-app"
        vault.hashicorp.com/agent-inject-template-database: |
          {{- with secret "database/creds/ironbucket-app" -}}
          SPRING_DATASOURCE_USERNAME={{ .Data.data.username }}
          SPRING_DATASOURCE_PASSWORD={{ .Data.data.password }}
          {{- end }}
        vault.hashicorp.com/agent-inject-secret-s3: "secret/data/s3-credentials"
        vault.hashicorp.com/agent-inject-template-s3: |
          {{- with secret "secret/data/s3-credentials" -}}
          AWS_ACCESS_KEY_ID={{ .Data.data.access-key-id }}
          AWS_SECRET_ACCESS_KEY={{ .Data.data.secret-access-key }}
          {{- end }}
        vault.hashicorp.com/agent-inject-secret-keycloak: "secret/data/keycloak"
        vault.hashicorp.com/agent-inject-template-keycloak: |
          {{- with secret "secret/data/keycloak" -}}
          KEYCLOAK_CLIENT_ID={{ .Data.data.client-id }}
          KEYCLOAK_CLIENT_SECRET={{ .Data.data.client-secret }}
          {{- end }}

    spec:
      serviceAccountName: ironbucket
      
      containers:
      - name: app
        image: ironbucket/sentinel-gear:1.0
        ports:
        - containerPort: 8080
          name: http
        
        env:
        # Load secrets from Vault Agent
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres:5432/ironbucket?sslmode=require"
        - name: AWS_REGION
          value: "us-east-1"
        - name: VAULT_ADDR
          value: "http://vault.vault.svc.cluster.local:8200"
        
        # Vault Agent writes secrets here
        volumeMounts:
        - name: vault-token
          mountPath: /vault/secrets
          readOnly: true
        
        # Spring Boot property file
        - name: config
          mountPath: /etc/config
          readOnly: true
      
      # Volume for Vault Agent token
      volumes:
      - name: vault-token
        emptyDir:
          medium: Memory
      
      # ConfigMap with additional config
      - name: config
        configMap:
          name: sentinel-gear-config
```

### 4.4 Secret Rotation (Automatic)

```yaml
# Vault configuration for automatic rotation
# Create a Vault policy that allows secret renewal

apiVersion: v1
kind: ConfigMap
metadata:
  name: vault-rotation-config
  namespace: vault
data:
  rotation-policy.hcl: |
    path "database/creds/ironbucket-app" {
      capabilities = ["read"]
    }
    
    path "auth/token/renew-self" {
      capabilities = ["update"]
    }

---

# CronJob for manual secret refresh (if needed)
apiVersion: batch/v1
kind: CronJob
metadata:
  name: vault-secret-refresh
  namespace: ironbucket
spec:
  # Refresh every 12 hours
  schedule: "0 */12 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: ironbucket
          containers:
          - name: refresh
            image: vault:latest
            command:
            - /bin/sh
            - -c
            - |
              # Authenticate to Vault
              VAULT_TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
              
              # Get new database credentials
              curl -X GET \
                -H "X-Vault-Token: $VAULT_TOKEN" \
                http://vault.vault.svc.cluster.local:8200/v1/database/creds/ironbucket-app
              
              # Trigger pod restart to pick up new secrets
              kubectl rollout restart deployment/sentinel-gear -n ironbucket
          restartPolicy: OnFailure
```

---

## 5. Spring Boot Integration

### 5.1 Spring Cloud Vault Dependency

```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-vault-config</artifactId>
  <version>4.0.1</version>
</dependency>

<dependency>
  <groupId>org.springframework.vault</groupId>
  <artifactId>spring-vault-core</artifactId>
  <version>3.1.0</version>
</dependency>
```

### 5.2 Configuration Class

```java
// VaultConfiguration.java
@Configuration
public class VaultConfiguration {
  
  @Bean
  public VaultTemplate vaultTemplate(VaultOperations vaultOperations) {
    return new VaultTemplate(vaultOperations);
  }
  
  @Bean
  public RestTemplateBuilder restTemplateBuilder(
      VaultProperties vaultProperties) {
    return new RestTemplateBuilder()
      .interceptors((request, body, execution) -> {
        // Add Vault token to all requests
        request.getHeaders().set("X-Vault-Token", 
          vaultProperties.getToken());
        return execution.execute(request, body);
      });
  }
}

// VaultProperties.java
@Configuration
@ConfigurationProperties(prefix = "vault")
public class VaultProperties {
  
  private String uri;
  private String token;
  private String role;
  
  // Getters/setters
}
```

### 5.3 application.yaml Configuration

```yaml
# application.yaml
spring:
  cloud:
    vault:
      uri: "http://vault.vault.svc.cluster.local:8200"
      authentication: KUBERNETES
      kubernetes:
        role: "ironbucket"
        service-account-token-file: "/var/run/secrets/kubernetes.io/serviceaccount/token"
      kv:
        enabled: true
        backend-path: "secret"
        profile-separator: "/"
        application-name: "ironbucket"
      database:
        enabled: true
        role: "ironbucket-app"

  datasource:
    url: "jdbc:postgresql://postgres:5432/ironbucket?sslmode=require"
    username: "${vault.database.username}"
    password: "${vault.database.password}"

# AWS/S3 credentials from Vault
aws:
  access-key-id: "${vault.s3.access-key-id}"
  secret-access-key: "${vault.s3.secret-access-key}"
```

### 5.4 Accessing Secrets in Code

```java
// Using VaultTemplate
@Component
public class S3Service {
  
  @Autowired
  private VaultTemplate vaultTemplate;
  
  public S3Client getS3Client() {
    // Fetch credentials from Vault
    VaultResponseSupport<Map<String, Object>> response = 
      vaultTemplate.read("secret/data/s3-credentials");
    
    Map<String, Object> data = response.getData();
    String accessKey = (String) data.get("access-key-id");
    String secretKey = (String) data.get("secret-access-key");
    
    return S3Client.builder()
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(accessKey, secretKey)
        )
      )
      .build();
  }
}

// Using @Value annotation
@Component
public class KeycloakConfig {
  
  @Value("${vault.keycloak.client-id}")
  private String clientId;
  
  @Value("${vault.keycloak.client-secret}")
  private String clientSecret;
  
  // Use clientId and clientSecret
}
```

---

## 6. Secret Rotation Policies

### 6.1 Automatic Database Credential Rotation

```bash
# Configure automatic rotation (already set in role creation)
vault write database/roles/ironbucket-app \
  rotation_statements="ALTER USER \"{{name}}\" WITH PASSWORD '{{password}}';" \
  rotation_window="1h"

# Monitor rotation (should see logs)
vault audit list
vault audit enable file file_path=/vault/logs/audit.log

# Check last rotation
vault read database/creds/ironbucket-app | grep lease_duration
```

### 6.2 Manual S3 Credential Rotation (Every 90 Days)

```bash
#!/bin/bash
# rotate-s3-credentials.sh

# Generate new S3 credentials (in AWS/MinIO)
NEW_ACCESS_KEY=$(aws iam create-access-key --user-name ironbucket | jq -r '.AccessKey.AccessKeyId')
NEW_SECRET_KEY=$(aws iam create-access-key --user-name ironbucket | jq -r '.AccessKey.SecretAccessKey')

# Store in Vault
vault kv put secret/s3-credentials \
  access-key-id="$NEW_ACCESS_KEY" \
  secret-access-key="$NEW_SECRET_KEY"

# Delete old credential from AWS
aws iam delete-access-key --user-name ironbucket --access-key-id $OLD_ACCESS_KEY_ID

# Trigger pod restart to pick up new credentials
kubectl rollout restart deployment/sentinel-gear -n ironbucket

echo "S3 credentials rotated successfully"
```

### 6.3 TLS Certificate Rotation (Automatic with PKI)

```bash
# Generate root CA
vault write -field=certificate pki/root/generate/internal \
  common_name="IronBucket Root CA" \
  ttl=87600h > root_ca.crt

# Create intermediate CA
vault secrets enable -path=pki_int pki
vault write -field=certificate pki_int/intermediate/generate/csr \
  common_name="IronBucket Intermediate CA" > pki_int.csr

# Sign intermediate
vault write -field=certificate pki/root/sign-intermediate \
  csr=@pki_int.csr \
  common_name="IronBucket Intermediate CA" \
  ttl=43800h > pki_int.crt

# Set signed intermediate
vault write pki_int/intermediate/set-signed certificate=@pki_int.crt

# Create role for TLS certs
vault write pki_int/roles/ironbucket \
  allowed_domains="ironbucket.local,*.ironbucket.local" \
  allow_subdomains=true \
  max_ttl=720h

# Issue a certificate
vault write pki_int/issue/ironbucket \
  common_name="sentinel-gear.ironbucket.local" \
  ttl=360h

# Certificates automatically renewed by Vault
```

---

## 7. Security Best Practices

### 7.1 Vault Administration

```bash
# 1. Regular backup
vault operator raft snapshot save backup-$(date +%Y%m%d).snap

# 2. Audit logging
vault audit enable file file_path=/vault/logs/audit.log log_raw=true hmac_accessor=false

# 3. Seal/Unseal monitoring
vault status | grep "Sealed"

# 4. Key rotation (every 30 days)
vault operator rotate

# 5. Policy review
vault policy list
vault policy read ironbucket-app

# 6. Auth method review
vault auth list
```

### 7.2 Secret Access Control

```hcl
# ironbucket-app policy
path "secret/data/s3-credentials" {
  capabilities = ["read"]
}

path "secret/data/keycloak" {
  capabilities = ["read"]
}

path "database/creds/ironbucket-app" {
  capabilities = ["read"]
}

path "auth/token/renew-self" {
  capabilities = ["update"]
}

path "sys/leases/renew" {
  capabilities = ["update"]
}

# CI/CD policy (limited)
path "secret/data/s3-credentials" {
  capabilities = ["read"]
}

path "database/creds/ironbucket-readonly" {
  capabilities = ["read"]
}

# Admin policy
path "*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
```

### 7.3 Monitoring & Alerting

```yaml
# Prometheus scrape config for Vault metrics
scrape_configs:
- job_name: 'vault'
  metrics_path: '/v1/sys/metrics'
  params:
    format: ['prometheus']
  bearer_token: 's.XXXXXXXXXXXX'
  static_configs:
  - targets: ['vault.vault.svc.cluster.local:8200']

# Alerts for secret access
- alert: HighSecretAccessRate
  expr: rate(vault_core_handle_login_request[5m]) > 100
  for: 5m
  annotations:
    summary: "Unusually high secret access rate"

- alert: FailedAuthAttempts
  expr: rate(vault_core_handle_login_request{error="true"}[5m]) > 10
  for: 5m
  annotations:
    summary: "Suspicious auth failure rate"
```

---

## 8. Troubleshooting

### 8.1 Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| **Pod can't authenticate to Vault** | Service account token wrong | Verify Kubernetes auth role binding |
| **Secrets not injected** | Vault Agent not running | Check `vault-agent-injector` pods |
| **Database credentials not working** | Username contains special chars | Check Vault database role TTL |
| **Secret rotation fails** | Old credential already revoked | Wait for lease renewal |
| **TLS certificate expired** | PKI role TTL too short | Extend TTL in PKI role |

### 8.2 Debug Commands

```bash
# Check Vault server status
vault status

# List all secrets
vault kv list secret/

# Read specific secret
vault kv get secret/s3-credentials

# Check lease info
vault lease lookup database/creds/ironbucket-app/...

# Verify Kubernetes auth
vault read auth/kubernetes/role/ironbucket

# Check pod annotations
kubectl describe pod <pod-name> -n ironbucket | grep vault

# View Vault Agent logs
kubectl logs <pod-name> -c vault-agent -n ironbucket

# Check Vault audit logs
tail -f /vault/logs/audit.log
```

---

## 9. Migration Path

### 9.1 Phase-In Strategy

```bash
# Week 1: Set up Vault infrastructure
- Install Vault
- Initialize and unseal
- Enable auth methods

# Week 2: Configure secrets
- Create database roles
- Store S3 credentials
- Store API keys

# Week 3: Kubernetes integration
- Deploy Vault Agent Injector
- Configure pod annotations
- Test secret injection

# Week 4: Migration
- Update Sentinel-Gear deployment
- Update Brazz-Nossel deployment
- Monitor for issues
- Rollback if needed

# Week 5: Cleanup
- Remove old secret storage
- Archive hardcoded credentials
- Enable audit logging
```

### 9.2 Rollback Plan

```bash
# If issues occur:
# 1. Revert pod annotations
# 2. Revert deployment to previous version
# 3. Restore hardcoded credentials temporarily
# 4. Investigate root cause
# 5. Fix and retry

kubectl rollout undo deployment/sentinel-gear -n ironbucket
kubectl rollout undo deployment/brazz-nossel -n ironbucket
```

---

## 10. Compliance & Audit

### 10.1 Compliance Alignment

- **SOC2 Type II**: Vault audit logging provides service organization controls
- **PCI DSS**: Vault meets requirements for secret storage and access control
- **HIPAA**: Encryption in transit and at rest for audit logs
- **GDPR**: Audit trails for data access

### 10.2 Audit Logging Example

```json
{
  "time": "2026-01-16T10:30:45Z",
  "type": "response",
  "auth": {
    "client_token": "s.xxxxxx",
    "accessor": "auth_kubernetes_xxxxxx",
    "display_name": "kubernetes-ironbucket",
    "policies": ["default", "ironbucket-app"],
    "metadata": {
      "role": "ironbucket",
      "service_account_name": "ironbucket",
      "service_account_namespace": "ironbucket"
    }
  },
  "request": {
    "id": "request-id-1234",
    "operation": "read",
    "path": "secret/data/s3-credentials",
    "remote_address": "10.0.1.42"
  },
  "response": {
    "status_code": 200
  }
}
```

---

## 11. Production Checklist

- [ ] Vault cluster initialized and unsealed
- [ ] Backup procedures in place
- [ ] Kubernetes auth configured
- [ ] Database dynamic credentials enabled
- [ ] S3 credentials stored
- [ ] Pod annotations configured
- [ ] Vault Agent Injector deployed
- [ ] Secret rotation policies set
- [ ] Audit logging enabled
- [ ] Monitoring and alerting configured
- [ ] Documentation complete
- [ ] DR plan tested

---

**Status**: VAULT INTEGRATION GUIDE COMPLETE  
**Version**: 1.0  
**Next Update**: March 16, 2026

