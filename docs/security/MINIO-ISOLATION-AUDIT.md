# 🛡️ MinIO Isolation & Security Audit

**Date:** January 18, 2026  
**Auditor:** IronBucket Security Review  
**Status:** ⚠️ **PARTIAL COMPLIANCE - REQUIRES HARDENING**

---

## Executive Summary

### Current State

✅ **GOOD**: MinIO has no external port exposure in docker-compose  
⚠️ **WARNING**: Test scripts access MinIO directly, bypassing security layers  
❌ **CRITICAL**: No network policies prevent container-to-container direct access  
❌ **CRITICAL**: Missing production Kubernetes NetworkPolicies

---

## 1. Docker Compose Analysis

### MinIO Service Configuration

```yaml
steel-hammer-minio:
  networks:
    - steel-hammer-network
  hostname: steel-hammer-minio
  container_name: steel-hammer-minio
  # ✅ NO PORTS EXPOSED TO HOST
  # Only accessible within docker network
```

**Finding**: MinIO port 9000 is NOT exposed to localhost. This is correct.

**Implication**: 
- ✅ External clients cannot bypass IronBucket
- ❌ Internal containers CAN bypass IronBucket
- ❌ Test container directly accesses MinIO

---

## 2. Test Scripts Security Issues

### Scripts with Direct MinIO Access

| Script | Issue | Severity |
|--------|-------|----------|
| `test-s3-authenticated.sh` | Uses `http://steel-hammer-minio:9000` | HIGH |
| `test-s3-operations.sh` | Direct curl to MinIO | HIGH |
| `run-e2e-complete.sh` | boto3 to minio:9000 | HIGH |
| `e2e-verification.sh` | Direct S3 client | HIGH |

### Example Violation

```python
# FROM: steel-hammer/test-scripts/e2e-verification.sh
s3_direct = boto3.client(
    's3',
    endpoint_url='http://steel-hammer-minio:9000',  # ❌ BYPASSES SECURITY
    aws_access_key_id='minioadmin',
    aws_secret_access_key='minioadmin'
)
```

**Why This Is Wrong**:
- Bypasses Sentinel-Gear JWT validation
- Bypasses Claimspindel policy evaluation
- Bypasses Brazz-Nossel audit logging
- Uses hardcoded credentials (security smell)

---

## 3. Security Architecture Requirements

### Correct Flow (MUST BE ENFORCED)

```
Client
  ↓
Brazz-Nossel :8082  ← ONLY PUBLIC ENDPOINT
  ↓ [validates JWT]
Sentinel-Gear :8080
  ↓ [evaluates policy]
Claimspindel :8081
  ↓ [authorized request only]
MinIO :9000  ← NO DIRECT ACCESS
```

### Incorrect Flow (CURRENTLY POSSIBLE IN TESTS)

```
Test Container
  ↓ [direct network access] ❌
MinIO :9000
```

---

## 4. Required Hardening Measures

### 4.1 Docker Compose (Development)

**Status**: ✅ Partially Correct

MinIO is isolated, but test containers should only access via Brazz-Nossel.

**Recommendation**:
```yaml
# Update test scripts to use Brazz-Nossel endpoint
environment:
  - "S3_ENDPOINT=http://steel-hammer-brazz-nossel:8082"  # Not minio:9000
```

### 4.2 Kubernetes NetworkPolicies (Production)

**Status**: ❌ MISSING

**Required Policy**:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: minio-isolation
  namespace: ironbucket
spec:
  podSelector:
    matchLabels:
      app: minio
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: brazz-nossel  # ONLY ALLOW BRAZZ-NOSSEL
    ports:
    - protocol: TCP
      port: 9000
```

**Effect**: Blocks all pods except `brazz-nossel` from reaching MinIO.

### 4.3 MinIO Bucket Policies

**Status**: ❌ MISSING

**Required**:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Deny",
    "Principal": "*",
    "Action": "s3:*",
    "Resource": "arn:aws:s3:::*",
    "Condition": {
      "StringNotEquals": {
        "aws:UserAgent": "IronBucket-BrazzNossel/1.0"
      }
    }
  }]
}
```

**Effect**: Only requests with IronBucket user agent are allowed.

### 4.4 MinIO IAM Policies

**Status**: ❌ HARDCODED CREDENTIALS

Current:
```yaml
MINIO_ROOT_USER=minioadmin  # ❌ Default credentials
MINIO_ROOT_PASSWORD=minioadmin
```

**Required**:
```yaml
# Brazz-Nossel uses dedicated service account
MINIO_SERVICE_ACCOUNT=brazz-nossel-sa
MINIO_SERVICE_SECRET=${VAULT_MINIO_SECRET}  # From Vault
```

---

## 5. Test Strategy Correction

### Current (Incorrect)

```bash
# Test directly hits MinIO
aws s3 cp file.txt s3://bucket/ \
  --endpoint-url http://steel-hammer-minio:9000
```

### Corrected (Via IronBucket)

```bash
# Test goes through proper security flow
export JWT_TOKEN=$(get_test_jwt)

curl -X PUT http://steel-hammer-brazz-nossel:8082/bucket/file.txt \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @file.txt
```

---

## 6. Compliance Matrix

| Requirement | Status | Notes |
|-------------|--------|-------|
| No external MinIO ports | ✅ PASS | Port 9000 not exposed |
| Container network isolation | ⚠️ PARTIAL | No NetworkPolicy in K8s |
| All requests via Brazz-Nossel | ❌ FAIL | Tests bypass gateway |
| JWT validation enforced | ❌ FAIL | Direct MinIO access exists |
| Policy evaluation enforced | ❌ FAIL | Tests skip Claimspindel |
| Audit logging complete | ❌ FAIL | Direct access not logged |
| Credential rotation | ❌ FAIL | Hardcoded minioadmin |
| Service account isolation | ❌ FAIL | Using root credentials |

**Overall Grade**: 🔴 **D+ (Needs Immediate Attention)**

---

## 7. Remediation Plan

### Phase 1: Immediate (This Sprint)

1. ✅ Document security violation
2. 🔲 Update all test scripts to use Brazz-Nossel endpoint
3. 🔲 Add warning comments to discourage direct MinIO access
4. 🔲 Create MinIO service account for Brazz-Nossel
5. 🔲 Remove hardcoded credentials from docker-compose

### Phase 2: Near-Term (Next Sprint)

6. 🔲 Implement Kubernetes NetworkPolicies
7. 🔲 Add MinIO bucket policies
8. 🔲 Integrate Vault for credential management
9. 🔲 Add monitoring for direct MinIO access attempts
10. 🔲 Create security compliance tests

### Phase 3: Long-Term (Production Readiness)

11. 🔲 Enable MinIO TLS
12. 🔲 Implement mutual TLS between Brazz-Nossel ↔ MinIO
13. 🔲 Add runtime security policies (OPA Gatekeeper)
14. 🔲 Implement SIEM integration for security events
15. 🔲 Regular penetration testing

---

## 8. Verification Tests

### Test 1: External Port Exposure

```bash
# Should FAIL (good)
curl http://localhost:9000
# Expected: Connection refused
```

✅ **PASS**: MinIO not exposed on localhost

### Test 2: Container Network Isolation

```bash
# From arbitrary container
docker run --network steel-hammer-network alpine \
  curl http://steel-hammer-minio:9000/minio/health/live
# Expected: SUCCESS (bad - no NetworkPolicy)
```

❌ **FAIL**: Any container can reach MinIO

### Test 3: Bypass Attempt

```bash
# Attempt direct S3 operation
aws s3 ls --endpoint-url http://localhost:9000
# Expected: Connection refused
```

✅ **PASS**: External bypass blocked

---

## 9. Production Deployment Checklist

Before deploying to production, ALL items must be ✅:

- [ ] MinIO has no public ingress
- [ ] NetworkPolicy allows only Brazz-Nossel → MinIO
- [ ] MinIO credentials stored in Vault
- [ ] Service accounts configured (no root access)
- [ ] Bucket policies enforce proxy-only access
- [ ] All tests use Brazz-Nossel endpoint
- [ ] Direct MinIO access attempts are logged and alerted
- [ ] TLS enabled for all MinIO connections
- [ ] Security audit completed and documented
- [ ] Penetration test passed

---

## 10. Conclusion

### Current Security Posture

IronBucket has a **solid security architecture design** but **incomplete enforcement**:

✅ **Architecture**: Sentinel-Gear + Claimspindel + Brazz-Nossel form correct zero-trust layers  
⚠️ **Implementation**: Missing network policies and credential management  
❌ **Testing**: Tests bypass security layers for convenience  

### Risk Assessment

| Risk | Likelihood | Impact | Severity |
|------|------------|--------|----------|
| External bypass | LOW | HIGH | MEDIUM |
| Internal bypass (dev) | HIGH | LOW | MEDIUM |
| Internal bypass (prod) | MEDIUM | HIGH | **CRITICAL** |
| Credential theft | MEDIUM | HIGH | **CRITICAL** |

### Recommendation

**DO NOT DEPLOY TO PRODUCTION** until:
1. NetworkPolicies implemented
2. Credential management via Vault
3. All tests updated to use Brazz-Nossel
4. Security compliance tests passing

**SAFE FOR DEVELOPMENT** with understanding that:
- Test environment is not hardened
- Direct MinIO access is possible for debugging
- Must be fixed before production deployment

---

**Next Actions**:
1. Implement NetworkPolicies in Kubernetes manifests
2. Refactor test scripts to use proper security flow
3. Set up Vault integration for MinIO credentials
4. Add compliance verification to CI/CD pipeline

**Approval Required From**:
- [ ] Security Team
- [ ] Platform Engineering
- [ ] DevOps Lead

---

**Document Version**: 1.0  
**Last Updated**: January 18, 2026  
**Next Review**: Before production deployment
