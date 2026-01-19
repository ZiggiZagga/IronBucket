# IronBucket Deployment Guide

**Version:** 1.0.0  
**Date:** January 15, 2026  
**Status:** Production-Ready  

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Local Development (Docker Compose)](#local-development)
3. [Kubernetes Deployment](#kubernetes-deployment)
4. [Configuration Management](#configuration-management)
5. [Monitoring & Observability](#monitoring--observability)
6. [Troubleshooting](#troubleshooting)
7. [Security Hardening](#security-hardening)

---

## Quick Start

### Prerequisites

- Docker & Docker Compose 2.0+
- Kubernetes 1.26+ (for K8s deployment)
- kubectl 1.26+ (for K8s)
- Java 25 (for local development)
- Maven 3.9+

### Get IronBucket Running in 5 Minutes

```bash
# 1. Clone the repository
git clone https://github.com/ZiggiZagga/IronBucket.git
cd IronBucket

# 2. Start all services (Docker Compose)
cd steel-hammer
DOCKER_FILES_HOMEDIR=. docker-compose -f docker-compose-steel-hammer.yml up -d

# 3. Wait for services to start (30-60 seconds)
sleep 60

# 4. Verify all services are running
docker ps | grep steel-hammer

# 5. Check service health
curl http://localhost:8080/actuator/health

# 6. View logs
docker-compose -f docker-compose-steel-hammer.yml logs -f
```

---

## Local Development

### Docker Compose Stack

Complete local environment with all dependencies:

```bash
cd /workspaces/IronBucket/steel-hammer
DOCKER_FILES_HOMEDIR=. docker-compose -f docker-compose-steel-hammer.yml up -d
```

**Services Started:**
- **Sentinel-Gear** (8080): OIDC Gateway
- **Claimspindel** (8081): Claims Router
- **Brazz-Nossel** (8082): S3 Proxy
- **Buzzle-Vane** (8083): Service Discovery
- **Keycloak** (7081): Identity Provider
- **PostgreSQL** (5432): Identity Database
- **MinIO** (9000): S3-Compatible Storage

**Accessing Services:**

```bash
# Sentinel-Gear
curl http://localhost:8080/actuator/health

# Keycloak Admin
http://localhost:7081/auth/admin  (user: admin, pass: admin)

# MinIO Console
http://localhost:9001 (user: minioadmin, pass: minioadmin)
```

### Stopping the Stack

```bash
cd steel-hammer
docker-compose -f docker-compose-steel-hammer.yml down -v
```

### Running Tests Locally

```bash
# Test Sentinel-Gear
cd temp/Sentinel-Gear
JAVA_HOME=/path/to/java25 mvn clean test

# Test all services
for svc in Sentinel-Gear Claimspindel Brazz-Nossel Buzzle-Vane; do
  cd temp/$svc
  JAVA_HOME=/path/to/java25 mvn clean test
done
```

---

## Kubernetes Deployment

### Prerequisites

```bash
# Create namespace
kubectl create namespace ironbucket-prod

# Label namespace for network policies
kubectl label namespace ironbucket-prod name=ironbucket-prod
```

### Deploy All Services

```bash
# 1. Create secrets
kubectl create secret generic ironbucket-secrets \
  --from-literal=oauth2-client-id=your-client-id \
  --from-literal=oauth2-client-secret=your-client-secret \
  --from-literal=idp-provider-host=keycloak.identity.svc.cluster.local \
  --from-literal=idp-provider-realm=ironbucket-prod \
  --from-literal=s3-access-key=minioadmin \
  --from-literal=s3-secret-key=minioadmin \
  --from-literal=s3-endpoint=https://s3.amazonaws.com \
  --from-literal=ssl-keystore-password=your-keystore-password \
  -n ironbucket-prod

# 2. Deploy all services
kubectl apply -f docs/k8s-manifests-production.yaml

# 3. Watch rollout
kubectl rollout status deployment/sentinel-gear -n ironbucket-prod
kubectl rollout status deployment/claimspindel -n ironbucket-prod
kubectl rollout status deployment/brazz-nossel -n ironbucket-prod
kubectl rollout status deployment/buzzle-vane -n ironbucket-prod

# 4. Verify all pods are running
kubectl get pods -n ironbucket-prod
```

### Access Services

```bash
# Port forward to Sentinel-Gear
kubectl port-forward svc/sentinel-gear 8080:8080 -n ironbucket-prod

# Test health endpoint
curl http://localhost:8080/actuator/health

# Port forward for Prometheus metrics
kubectl port-forward svc/sentinel-gear 8080:8080 -n ironbucket-prod
curl http://localhost:8080/actuator/prometheus
```

### View Logs

```bash
# Follow logs for Sentinel-Gear
kubectl logs -f deployment/sentinel-gear -n ironbucket-prod

# Logs for specific pod
kubectl logs -f pod/sentinel-gear-xyz -n ironbucket-prod

# View last 100 lines
kubectl logs -n ironbucket-prod --tail=100 deployment/sentinel-gear
```

### Scaling

```bash
# Manual scaling
kubectl scale deployment sentinel-gear --replicas=5 -n ironbucket-prod

# Verify HPA is active
kubectl get hpa -n ironbucket-prod
kubectl describe hpa sentinel-gear-hpa -n ironbucket-prod
```

### Rolling Update

```bash
# Update image
kubectl set image deployment/sentinel-gear \
  sentinel-gear=your-registry/sentinel-gear:1.0.1 \
  -n ironbucket-prod

# Watch rollout
kubectl rollout status deployment/sentinel-gear -n ironbucket-prod

# Rollback if needed
kubectl rollout undo deployment/sentinel-gear -n ironbucket-prod
```

---

## Configuration Management

### Environment Variables

All configuration is environment-driven (12-factor app). Set via:

**Kubernetes (ConfigMap & Secret)**
```yaml
# ConfigMap for non-sensitive data
kubectl create configmap ironbucket-config \
  --from-literal=TENANT_MODE=multi \
  --from-literal=DEFAULT_TENANT=default \
  -n ironbucket-prod

# Secret for sensitive data
kubectl create secret generic ironbucket-secrets \
  --from-literal=OAUTH2_CLIENT_SECRET=xxx \
  -n ironbucket-prod
```

**Docker Compose**
```bash
# Create .env file
cat > .env << EOF
OAUTH2_CLIENT_ID=dev-client
OAUTH2_CLIENT_SECRET=dev-secret
IDP_PROVIDER_HOST=keycloak
IDP_PROVIDER_REALM=dev
TENANT_MODE=multi
EOF

docker-compose --env-file .env up -d
```

### Key Configuration Variables

**Identity Provider**
```yaml
IDP_PROVIDER_PROTOCOL: https
IDP_PROVIDER_HOST: keycloak.identity.svc.cluster.local
IDP_PROVIDER_REALM: ironbucket-prod
OAUTH2_CLIENT_ID: sentinel-gear
OAUTH2_CLIENT_SECRET: ${SECRET}
JWT_ISSUER_WHITELIST: https://keycloak.identity.svc.cluster.local/realms/ironbucket-prod
JWT_AUDIENCE: ironbucket
```

**S3 Storage**
```yaml
S3_ENDPOINT: https://s3.amazonaws.com
S3_REGION: us-east-1
S3_ACCESS_KEY: ${SECRET}
S3_SECRET_KEY: ${SECRET}
S3_PATH_STYLE: false
```

**Security**
```yaml
SSL_ENABLED: true
SSL_KEYSTORE_PATH: /etc/ssl/keystore.p12
SSL_KEYSTORE_PASSWORD: ${SECRET}
CORS_ALLOWED_ORIGINS: https://portal.ironbucket.io
```

**Performance**
```yaml
RATE_LIMIT_REPLENISH: 100
RATE_LIMIT_BURST: 200
TRACING_SAMPLE_RATE: 0.1
```

---

## Monitoring & Observability

### Prometheus Integration

All services expose metrics at `/actuator/prometheus`:

```bash
# Scrape metrics
curl http://localhost:8080/actuator/prometheus

# Common metrics
http_requests_total
http_request_duration_seconds
jvm_memory_used_bytes
jvm_gc_pause_seconds
resilience4j_circuitbreaker_state
```

### Grafana Dashboards

Import dashboard templates:
```bash
# JWT Validation Performance
- Latency p50, p95, p99
- Cache hit rate
- Validation error rate

# Policy Evaluation
- Evaluation time distribution
- Cache hit rate
- Decision breakdown (Allow/Deny)

# S3 Proxy
- Throughput (req/s)
- Latency distribution
- Error rates by operation
- Backend health

# System Health
- Pod memory/CPU usage
- Pod restart count
- Network I/O
- Database connection pool
```

### Distributed Tracing

OpenTelemetry integration for end-to-end tracing:

```bash
# Configure tracing endpoint
OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
OTEL_SERVICE_NAME=sentinel-gear
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1  # 10% sampling
```

### Logging

Structured JSON logging to stdout:

```json
{
  "timestamp": "2026-01-15T10:30:00Z",
  "level": "INFO",
  "logger": "com.ironbucket.sentinelgear.identity.JWTValidator",
  "message": "JWT validated successfully",
  "request_id": "req-12345",
  "tenant": "customer-a",
  "user": "alice@example.com",
  "latency_ms": 0.5,
  "cache_hit": true
}
```

---

## Troubleshooting

### Service Won't Start

```bash
# Check logs
kubectl logs deployment/sentinel-gear -n ironbucket-prod

# Common issues:
# 1. Missing secrets
kubectl get secrets -n ironbucket-prod

# 2. Configuration errors
kubectl describe pod sentinel-gear-xyz -n ironbucket-prod

# 3. Resource constraints
kubectl top nodes
kubectl top pods -n ironbucket-prod
```

### High Latency

```bash
# Check metrics
curl http://localhost:8080/actuator/prometheus | grep duration

# Check circuit breaker status
curl http://localhost:8080/actuator/circuitbreaker

# Check cache hit rate
curl http://localhost:8080/actuator/prometheus | grep cache

# Investigate:
# 1. Network latency: Check service-to-service calls
# 2. Database slow: Check PostgreSQL query logs
# 3. S3 slow: Check S3 response times
```

### Authentication Failures

```bash
# Check JWT validation errors
kubectl logs deployment/sentinel-gear -n ironbucket-prod | grep -i auth

# Verify OIDC configuration
curl https://keycloak:7081/realms/ironbucket-prod/.well-known/openid-configuration

# Check issuer whitelist
echo $JWT_ISSUER_WHITELIST

# Test JWT manually
jwt decode <token>
```

### Policy Enforcement Issues

```bash
# Enable debug logging
kubectl set env deployment/claimspindel LOG_LEVEL=DEBUG -n ironbucket-prod

# Check policy evaluation
curl -X POST http://localhost:8081/evaluate \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"action": "s3:GetObject", "resource": "arn:aws:s3:::bucket/key"}'

# Verify policy cache
curl http://localhost:8081/actuator/prometheus | grep policy_cache
```

---

## Security Hardening

### Network Security

```yaml
# NetworkPolicy: Restrict pod-to-pod traffic
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: ironbucket-network-policy
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: ironbucket-prod
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              name: ironbucket-prod
```

### RBAC & Authorization

```bash
# Service account with minimal permissions
kubectl create serviceaccount ironbucket-sa -n ironbucket-prod

# Role with only necessary permissions
kubectl create role ironbucket-role \
  --verb=get,list,watch \
  --resource=pods,services \
  -n ironbucket-prod
```

### Secret Management

```bash
# Use sealed-secrets or external-secrets
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.18.0/controller.yaml

# Encrypt secrets at rest
kubectl api-resources | grep encryption

# Rotate secrets regularly
kubectl create secret generic ironbucket-secrets \
  --from-literal=new-secret=value \
  --dry-run=client -o yaml | kubectl apply -f -
```

### Pod Security Policies

```yaml
# Enforce security standards
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: ironbucket-psp
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  runAsUser:
    rule: 'MustRunAsNonRoot'
  seLinux:
    rule: 'MustRunAs'
  fsGroup:
    rule: 'MustRunAs'
  readOnlyRootFilesystem: true
```

### TLS/SSL Configuration

```bash
# Generate certificates
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365

# Create secret
kubectl create secret tls tls-secret \
  --cert=cert.pem \
  --key=key.pem \
  -n ironbucket-prod

# Configure ingress
kubectl apply -f ingress-tls.yaml
```

---

## Production Checklist

Before going live, verify:

- [ ] All secrets are properly secured
- [ ] TLS certificates are valid
- [ ] Monitoring and logging are active
- [ ] Backups are configured
- [ ] Disaster recovery procedures are tested
- [ ] Network policies are in place
- [ ] RBAC is properly configured
- [ ] Pod security policies are enforced
- [ ] Resource requests/limits are set
- [ ] Health checks are passing
- [ ] All 231 tests pass
- [ ] E2E tests are green
- [ ] Load testing completed (100+ concurrent users)
- [ ] Security scanning passed (Snyk, Trivy)
- [ ] Documentation is complete
- [ ] Runbooks are available
- [ ] On-call rotation is configured
- [ ] Incident response procedures are documented

---

## Support

For issues or questions:
1. Check logs: `kubectl logs deployment/sentinel-gear -n ironbucket-prod`
2. Review metrics: `curl http://localhost:8080/actuator/prometheus`
3. Check health: `curl http://localhost:8080/actuator/health`
4. Review documentation: https://github.com/ZiggiZagga/IronBucket/docs

---

*Last Updated: January 15, 2026*  
*Version: 1.0.0*
