# 🔧 IronBucket Troubleshooting Guide

**Target Audience**: Operators, Users, Developers  
**Read Time**: 20 minutes  
**Status**: ✅ Complete  
**Last Updated**: January 15, 2026

---

## Quick Troubleshooting Index

| Problem | Section | Time |
|---------|---------|------|
| Services won't start | [Startup Issues](#startup-issues) | 5 min |
| Policy evaluation failing | [Policy Errors](#policy-errors) | 5 min |
| S3 operations timing out | [S3 Connection Issues](#s3-connection-issues) | 5 min |
| Audit logs not appearing | [Logging Issues](#logging-issues) | 5 min |
| Authentication failures | [Auth Issues](#authentication-issues) | 5 min |
| Performance degradation | [Performance Issues](#performance-issues) | 10 min |

---

## 🚀 Startup Issues

### Problem: Services Won't Start

**Symptoms**:
```bash
$ docker-compose ps
# Output: "exited (1)" status for one or more services
```

### Diagnosis Steps

```bash
# Step 1: Check service logs
docker logs brazz-nossel-service
docker logs buzzle-vane-service
docker logs claimspindel-service
docker logs sentinel-gear-service

# Look for specific error messages
```

### Common Causes & Fixes

#### 1. Port Already in Use

**Error**:
```
Address already in use: 8081
```

**Fix**:
```bash
# Find what's using the port
lsof -i :8081

# Stop the conflicting service
kill <PID>

# OR stop all containers
docker-compose down
sleep 5
docker-compose up -d
```

#### 2. PostgreSQL Not Ready

**Error**:
```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused
```

**Fix**:
```bash
# PostgreSQL needs time to start
# Check if it's running
docker-compose ps postgres

# Wait and retry (give it 30-60 seconds)
sleep 60
docker-compose up -d

# Verify connection
docker exec postgres psql -U ironbucket -d ironbucket -c "SELECT 1"
```

#### 3. Docker Image Pull Failures

**Error**:
```
Error response from daemon: pull access denied for brazz-nossel-service
```

**Fix**:
```bash
# Rebuild images locally
docker-compose build --no-cache

# OR pull latest versions
docker-compose pull

# Then start
docker-compose up -d
```

#### 4. Out of Memory

**Error**:
```
Java heap space
```

**Fix**:
```bash
# Increase Docker memory allocation
# Edit docker-compose.yml:
services:
  brazz-nossel-service:
    environment:
      JAVA_OPTS: "-Xmx2g -Xms1g"

# Restart
docker-compose restart brazz-nossel-service
```

#### 5. Missing Environment Variables

**Error**:
```
java.lang.IllegalArgumentException: The hostname in the URL cannot be null
```

**Fix**:
```bash
# Check .env file exists
ls -la steel-hammer/.env

# Verify required variables
echo $DB_HOST
echo $S3_ENDPOINT

# Reload environment
docker-compose down
docker-compose up -d
```

### Complete Startup Procedure

When nothing works, follow this:

```bash
# Step 1: Clean stop
cd steel-hammer
docker-compose down
docker volume ls | grep ironbucket

# Step 2: Remove volumes to reset data
docker volume rm ironbucket-postgres-data
docker volume rm ironbucket-minio-data

# Step 3: Rebuild everything
docker-compose build --no-cache

# Step 4: Start in sequence
docker-compose up -d postgres
sleep 30

docker-compose up -d minio
sleep 10

docker-compose up -d keycloak
sleep 20

docker-compose up -d  # All remaining services
sleep 30

# Step 5: Verify health
docker-compose ps
curl http://localhost:8080/actuator/health
```

**Expected output** (all services running):
```
NAME                    STATUS
postgres                Up 2 minutes
minio                   Up 2 minutes
keycloak                Up 2 minutes
brazz-nossel-service    Up 1 minute
buzzle-vane-service     Up 1 minute
claimspindel-service    Up 1 minute
sentinel-gear-service   Up 1 minute
```

---

## 📋 Policy Errors

### Problem: Policy Not Found

**Symptom**:
```
HTTP 404: Policy 'read-public-objects' not found
```

**Diagnosis**:
```bash
# Check if policy exists in database
docker exec postgres psql -U ironbucket -d ironbucket -c \
  "SELECT * FROM policies WHERE name = 'read-public-objects';"

# No output = policy doesn't exist
```

**Fix**:
```bash
# Create the policy
curl -X POST http://localhost:8080/policies \
  -H "Content-Type: application/json" \
  -d @policy-file.json

# Verify it was created
curl http://localhost:8080/policies/read-public-objects
```

### Problem: Policy Evaluation Fails

**Symptom**:
```
ERROR: Invalid policy syntax: Unexpected token at line 5
```

**Diagnosis**:
```bash
# Test policy with curl
curl -X POST http://localhost:8081/evaluate \
  -H "Content-Type: application/json" \
  -d @policy-file.json | jq .

# Look for parsing errors in response
```

**Fix**: Check policy JSON syntax

```bash
# Validate JSON
cat policy-file.json | jq . > /dev/null || echo "Invalid JSON"

# Common errors:
# 1. Missing commas
# 2. Trailing commas
# 3. Unquoted property names
# 4. Invalid escape sequences
```

**Valid policy structure**:
```json
{
  "name": "read-public-objects",
  "description": "Allow reading public objects",
  "effect": "ALLOW",
  "resources": [
    "bucket/public/*"
  ],
  "actions": [
    "s3:GetObject"
  ],
  "conditions": {
    "timeRange": {
      "start": "09:00",
      "end": "17:00"
    }
  }
}
```

See: [policy-schema.md](docs/policy-schema.md) for complete specification.

### Problem: Policy Returns Unexpected Decision

**Symptom**:
```
Expected: ALLOW
Actual: DENY
```

**Debug Steps**:
```bash
# Check policy contents
curl http://localhost:8081/policies/policy-name | jq .

# Evaluate with debug flag
curl -X POST http://localhost:8081/evaluate?debug=true \
  -H "Content-Type: application/json" \
  -d '{
    "policy": "policy-name",
    "resource": "bucket/file.txt",
    "action": "s3:GetObject",
    "user": "alice@example.com"
  }' | jq .

# Response should show:
# - Which rules matched
# - Which conditions passed/failed
# - Final decision logic
```

**Common causes**:
1. **Resource pattern doesn't match**: Regex or wildcard issue
2. **Condition not met**: Time, IP, or other condition failed
3. **User not in group**: Identity claim missing
4. **Policy precedence**: Multiple policies conflicting

---

## 🔗 S3 Connection Issues

### Problem: S3 Operation Timeout

**Symptom**:
```
Request timeout after 30 seconds
```

**Check S3 service**:
```bash
# Verify MinIO is running
docker-compose ps minio

# Check MinIO health
curl http://localhost:9000/minio/health/live

# Check S3 connectivity from container
docker exec buzzle-vane-service \
  curl -s http://minio:9000/minio/health/live
```

**Fix**:
```bash
# Option 1: Increase timeout
# Edit Buzzle-Vane configuration:
s3:
  socket:
    timeout: 90000  # 90 seconds

# Option 2: Restart MinIO
docker-compose restart minio

# Option 3: Check network
docker network ls
docker network inspect ironbucket_default
```

### Problem: S3 Authentication Fails

**Symptom**:
```
403 Forbidden: Access Denied
```

**Diagnosis**:
```bash
# Check MinIO credentials
docker logs minio | grep "Credentials"

# Verify from container
docker exec minio mc ls minio/bucket-name

# Check S3 Proxy config
curl http://localhost:8082/config | jq .s3.credentials
```

**Fix**:
```bash
# Recreate MinIO with correct credentials
docker-compose down minio
docker volume rm ironbucket-minio-data
docker-compose up -d minio
sleep 30

# Set access key and secret key
docker exec minio mc alias set s3-local \
  http://localhost:9000 \
  minioadmin \
  minioadmin
```

### Problem: Bucket Not Found

**Symptom**:
```
404 NoSuchBucket
```

**Diagnosis**:
```bash
# List available buckets
docker exec minio mc ls minio/

# Expected output shows: [DIR] bucket-name
```

**Fix**:
```bash
# Create missing bucket
docker exec minio mc mb minio/bucket-name

# Verify
docker exec minio mc ls minio/
```

---

## 🔐 Authentication Issues

### Problem: Invalid Token Rejected

**Symptom**:
```
401 Unauthorized: Invalid token
```

**Diagnosis**:
```bash
# Check token format
# Tokens should be: Bearer eyJhbGc...

# Verify Keycloak is running
docker-compose ps keycloak

# Check token validity
curl http://localhost:8080/auth/token | jq .
```

**Fix**:
```bash
# Obtain fresh token
TOKEN=$(curl -s -X POST \
  http://localhost:8080/auth/token \
  -d 'client_id=client' \
  -d 'client_secret=secret' \
  -d 'grant_type=client_credentials' | jq -r '.access_token')

# Verify it works
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/actuator/health
```

### Problem: User Not Authenticated

**Symptom**:
```
403 Forbidden: User 'unknown' not recognized
```

**Diagnosis**:
```bash
# Check Keycloak user exists
docker exec keycloak /opt/keycloak/bin/kcadm.sh \
  list users -r master

# Check user claims
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/auth/userinfo | jq .
```

**Fix**:
```bash
# Create missing user
docker exec keycloak /opt/keycloak/bin/kcadm.sh \
  create users -r master \
  -s username=newuser \
  -s enabled=true \
  -s email=newuser@example.com

# Set password
docker exec keycloak /opt/keycloak/bin/kcadm.sh \
  set-password -r master \
  --username newuser \
  --new-password password123
```

---

## 📝 Logging Issues

### Problem: Audit Logs Not Appearing

**Symptom**:
```
Request was processed but no audit log entry exists
```

**Diagnosis**:
```bash
# Check Claimspindel service status
docker logs claimspindel-service | tail -20

# Query audit database directly
docker exec postgres psql -U ironbucket -d ironbucket -c \
  "SELECT COUNT(*) FROM audit_logs;"

# Check if table exists
docker exec postgres psql -U ironbucket -d ironbucket -c \
  "\dt audit_logs"
```

**Fix**:
```bash
# Option 1: Restart audit service
docker-compose restart claimspindel-service

# Option 2: Check database connection
docker logs claimspindel-service | grep -i "connection\|database"

# Option 3: Verify PostgreSQL has audit table
docker exec postgres psql -U ironbucket -d ironbucket < \
  ../Claimspindel/src/main/resources/schema.sql
```

### Problem: Logs Are Delayed

**Symptom**:
```
Audit log appears 5+ minutes after request
```

**Cause**: Batch processing buffer

**Fix**:
```bash
# Reduce batch timeout
# Edit Claimspindel configuration:
audit:
  batch:
    timeout: 2000  # 2 seconds instead of default

# Restart service
docker-compose restart claimspindel-service
```

**Note**: Shorter timeout = more frequent database writes = higher latency overhead. Balance based on your needs.

### Problem: Logs Show Truncated Data

**Symptom**:
```
request_body: "{ truncated... }"
```

**Fix**:
```bash
# Increase log data size limit
audit:
  maxPayloadSize: 1048576  # 1MB instead of default

# Restart
docker-compose restart claimspindel-service
```

---

## ⚡ Performance Issues

### Problem: High Latency

**Symptom**:
```
Requests taking 5+ seconds
```

**Diagnosis**:
```bash
# 1. Check service logs for errors
docker logs brazz-nossel-service | grep "ERROR\|WARN"

# 2. Check resource usage
docker stats

# 3. Check database performance
docker exec postgres psql -U ironbucket -d ironbucket -c \
  "SELECT query, calls, mean_time FROM pg_stat_statements \
   ORDER BY mean_time DESC LIMIT 10;"

# 4. Check network connectivity
docker exec brazz-nossel-service \
  curl -I http://minio:9000/minio/health/live
```

**Fix** (in order of likelihood):

```bash
# 1. Restart services
docker-compose restart

# 2. Increase memory
# Edit docker-compose.yml:
environment:
  JAVA_OPTS: "-Xmx2g -Xms1g"

# 3. Clear caches
curl -X POST http://localhost:8081/cache/clear

# 4. Check disk space
df -h
# If < 10% free, clean up Docker
docker system prune -a
```

### Problem: Memory Leak

**Symptom**:
```
Memory usage increasing over time
Restart fixes it temporarily
```

**Diagnosis**:
```bash
# Monitor memory over time
docker stats --no-stream

# Check logs for exceptions
docker logs brazz-nossel-service | grep "OutOfMemory"

# Generate heap dump
docker exec brazz-nossel-service \
  jcmd 1 GC.heap_dump /tmp/heap.dump
```

**Fix**:
1. Restart affected service
2. Increase heap size
3. File bug with heap dump attached

### Problem: Database Slow Queries

**Symptom**:
```
Audit logging slowing down requests
Policy evaluation taking too long
```

**Diagnosis**:
```bash
# Enable query logging
docker exec postgres psql -U ironbucket -d ironbucket -c \
  "ALTER SYSTEM SET log_min_duration_statement = 1000;"

# Restart PostgreSQL
docker-compose restart postgres

# Check slow queries
docker logs postgres | grep "duration:"
```

**Fix**:
```bash
# Add index on frequently queried column
docker exec postgres psql -U ironbucket -d ironbucket -c \
  "CREATE INDEX idx_audit_user ON audit_logs(user_id);"

# Analyze query plan
docker exec postgres psql -U ironbucket -d ironbucket -c \
  "EXPLAIN SELECT * FROM audit_logs WHERE user_id = 'alice';"
```

---

## 🔍 Diagnostic Commands

### Health Check Script

```bash
#!/bin/bash
# save as: health-check.sh
# run: bash health-check.sh

echo "=== IronBucket Health Check ==="
echo ""

echo "✓ Docker Status"
docker-compose ps

echo ""
echo "✓ Service Health Endpoints"
curl -s http://localhost:8080/actuator/health | jq .
curl -s http://localhost:8081/actuator/health | jq .
curl -s http://localhost:8082/actuator/health | jq .
curl -s http://localhost:8083/actuator/health | jq .

echo ""
echo "✓ Database Connection"
docker exec postgres psql -U ironbucket -d ironbucket -c "SELECT NOW();"

echo ""
echo "✓ MinIO Status"
curl -s http://localhost:9000/minio/health/live

echo ""
echo "✓ Policy Count"
docker exec postgres psql -U ironbucket -d ironbucket -c \
  "SELECT COUNT(*) as policy_count FROM policies;"

echo ""
echo "✓ Audit Log Count"
docker exec postgres psql -U ironbucket -d ironbucket -c \
  "SELECT COUNT(*) as log_count FROM audit_logs;"

echo ""
echo "=== Health Check Complete ==="
```

### Log Analysis Commands

```bash
# Find errors in all services
docker-compose logs --timestamps | grep ERROR

# Real-time log streaming
docker-compose logs -f

# Logs for specific service
docker logs -f brazz-nossel-service

# Last 100 lines
docker-compose logs --tail=100

# Logs since last 5 minutes
docker-compose logs --since 5m
```

---

## 📞 Getting Help

### Before Asking for Help

1. ✅ Run the [health check script](#health-check-script)
2. ✅ Collect logs: `docker-compose logs > logs.txt`
3. ✅ Check [DOCS-INDEX.md](DOCS-INDEX.md) for related docs
4. ✅ Search existing [GitHub Issues](https://github.com/ZiggiZagga/IronBucket/issues)

### How to Report Issues

**Include**:
- [ ] Exact error message
- [ ] Steps to reproduce
- [ ] Health check output
- [ ] Relevant logs (redacted for secrets)
- [ ] Environment: OS, Docker version, Java version
- [ ] Commit hash: `git rev-parse --short HEAD`

**Template**:
```markdown
**Environment**:
- OS: Ubuntu 24.04
- Docker: 26.0.0
- Java: 21

**Error**:
```
[Error message here]
```

**Steps to Reproduce**:
1. Start with `docker-compose up`
2. Run command...
3. Observe error

**Expected**:
[What should happen]

**Actual**:
[What actually happens]

**Logs**:
```
[Relevant log section]
```
```

---

## 🔗 Related Documentation

| Document | Purpose |
|----------|---------|
| [START.md](START.md) | Getting started guide |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System design |
| [DEPLOYMENT-GUIDE.md](docs/DEPLOYMENT-GUIDE.md) | Production deployment |
| [steel-hammer/STARTUP-ORDER.md](steel-hammer/STARTUP-ORDER.md) | Service startup sequence |

---

## ✅ Verification Checklist

When all services are running:

- [ ] All containers show "Up" status
- [ ] All health endpoints return 200 OK
- [ ] Database queries return results
- [ ] Policy operations work
- [ ] S3 operations complete
- [ ] Audit logs appear in database
- [ ] No error messages in logs

---

**Last Updated**: January 15, 2026  
**Maintained By**: @ZiggiZagga  
**Status**: ✅ COMPLETE & TESTED

Still stuck? Open a GitHub issue with the templates above!
