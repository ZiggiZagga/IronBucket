# IronBucket Troubleshooting Guide

## Common Issues

### Services Not Starting

**Problem:** Docker containers fail to start or are stuck

**Solutions:**

```bash
# Check container status
docker ps -a

# View logs
docker logs steel-hammer-test

# Remove and restart
docker-compose down -v
docker-compose up -d --build
```

### Port Already in Use

**Problem:** `Address already in use` error

**Solutions:**

```bash
# Find what's using the port (example: 8080)
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or change port in docker-compose.yml
ports:
  - "8081:8080"  # Use 8081 instead
```

### Out of Memory

**Problem:** `java.lang.OutOfMemoryError: Java heap space`

**Solutions:**

```bash
# Increase Docker memory
# Edit docker-compose.yml:
environment:
  - JAVA_OPTS=-Xmx4g  # Allocate 4GB

# Or increase Docker Desktop limit:
# Docker → Preferences → Resources → Memory: 8GB+
```

### Database Connection Refused

**Problem:** `Connection refused` on port 5432

**Solutions:**

```bash
# Wait for PostgreSQL to fully start
sleep 30

# Test PostgreSQL connection
docker exec steel-hammer-postgres psql -U postgres -c "SELECT 1"

# Check PostgreSQL logs
docker logs steel-hammer-postgres
```

### JWT Validation Failures

**Problem:** HTTP 401 or 403 errors

**Solutions:**

1. **Missing JWT Token**
   ```bash
   # Request must include Authorization header
   curl -H "Authorization: Bearer <JWT_TOKEN>" \
        http://localhost:8082/
   ```

2. **Invalid Signature**
   ```bash
   # Ensure JWT is signed with correct secret
   # Check Sentinel-Gear logs:
   docker logs steel-hammer-sentinel-gear | grep -i "signature"
   ```

3. **Expired Token**
   ```bash
   # Get a fresh token from Keycloak
   # Default token expiration: 3600 seconds (1 hour)
   ```

### File Upload Failing

**Problem:** S3 PutObject returns error

**Solutions:**

1. **Check MinIO is running**
   ```bash
   docker ps | grep minio
   ```

2. **Check bucket exists**
   ```bash
   docker exec steel-hammer-minio /opt/minio/minio-binaries/mc ls minio/
   ```

3. **Check permissions**
   ```bash
   # Ensure user has PutObject permission
   # Check policy in Claimspindel
   docker logs steel-hammer-claimspindel | grep -i "policy"
   ```

### Slow Performance

**Problem:** Operations taking longer than expected

**Solutions:**

1. **Check resource usage**
   ```bash
   docker stats
   ```

2. **Increase resource limits**
   ```yaml
   # In docker-compose.yml
   services:
     brazz-nossel:
       deploy:
         resources:
           limits:
             cpus: '2'
             memory: 2G
   ```

3. **Optimize database**
   ```bash
   # Check PostgreSQL query logs
   docker logs steel-hammer-postgres | grep "slow"
   ```

### MinIO Console Not Accessible

**Problem:** Can't access MinIO dashboard

**Solutions:**

```bash
# Check MinIO is running
docker ps | grep minio

# Access MinIO Console
# URL: http://localhost:9001
# Username: minioadmin
# Password: minioadmin

# Check ports are correctly mapped
docker port steel-hammer-minio
```

### Keycloak Login Issues

**Problem:** Can't login to Keycloak

**Solutions:**

```bash
# Check Keycloak is running
docker ps | grep keycloak

# Access Keycloak Admin Console
# URL: http://localhost:8080
# Username: admin
# Password: admin

# Check user exists
docker exec steel-hammer-keycloak /opt/keycloak/bin/kcadm.sh list-users -r dev --admin-server-url http://localhost:8080
```

### Service Discovery Not Working

**Problem:** Services can't find each other

**Solutions:**

```bash
# Check Buzzle-Vane (Eureka) is running
docker ps | grep buzzle

# Access Eureka Dashboard
# URL: http://localhost:8083/eureka/web

# Check service registration
# Should see all 6 services registered

# If services missing, restart them:
docker-compose restart brazz-nossel
docker-compose restart sentinel-gear
docker-compose restart claimspindel
```

## Network Troubleshooting

### Test Connectivity Between Services

```bash
# Test from test container
docker exec steel-hammer-test curl http://brazz-nossel:8082/health

# Test MinIO connectivity
docker exec steel-hammer-test curl http://minio:9000/minio/health/live
```

### Check Network

```bash
# List networks
docker network ls

# Inspect network
docker network inspect steel-hammer-network

# Should see all containers connected
```

## Data Troubleshooting

### PostgreSQL Data Issues

```bash
# Connect to database
docker exec -it steel-hammer-postgres psql -U postgres -d postgres

# List tables
\dt

# Query audit logs
SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 10;

# Check data integrity
SELECT COUNT(*) FROM audit_logs;
```

### MinIO Data Issues

```bash
# List buckets
docker exec steel-hammer-minio /opt/minio/minio-binaries/mc ls minio/

# List objects
docker exec steel-hammer-minio /opt/minio/minio-binaries/mc ls minio/bucket-name/

# Download file for inspection
docker exec steel-hammer-minio /opt/minio/minio-binaries/mc cat minio/bucket/object
```

## Logs and Debugging

### View Real-time Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f brazz-nossel

# Last 100 lines
docker-compose logs --tail=100 brazz-nossel
```

### Search Logs

```bash
# Find errors
docker logs steel-hammer-brazz-nossel 2>&1 | grep -i error

# Find JWT issues
docker logs steel-hammer-sentinel-gear | grep -i jwt

# Find database issues
docker logs steel-hammer-postgres | grep -i error
```

### Export Logs

```bash
# Save logs to file
docker logs steel-hammer-brazz-nossel > brazz-nossel.log

# Archive all logs
docker-compose logs > all-services.log
```

## Health Checks

### Check Service Health Endpoints

```bash
# Brazz-Nossel
curl http://localhost:8082/actuator/health

# Sentinel-Gear
curl http://localhost:8080/actuator/health

# Claimspindel
curl http://localhost:8081/actuator/health

# Buzzle-Vane
curl http://localhost:8083/actuator/health
```

### Verify All Services

```bash
#!/bin/bash
SERVICES=("8080" "8081" "8082" "8083" "9000" "5432")

for PORT in "${SERVICES[@]}"; do
  if nc -z localhost "$PORT" 2>/dev/null; then
    echo "✅ Service on port $PORT is running"
  else
    echo "❌ Service on port $PORT is NOT running"
  fi
done
```

## Reset Everything

### Complete Clean Restart

```bash
# Stop all services
docker-compose down

# Remove all data
docker-compose down -v

# Remove all containers and images
docker system prune -a

# Rebuild and start
docker-compose up -d --build
```

### Reset Database Only

```bash
# Stop PostgreSQL
docker-compose stop postgres

# Remove database volume
docker volume rm steel-hammer_postgres-data

# Restart
docker-compose up -d postgres
```

### Reset MinIO Only

```bash
# Stop MinIO
docker-compose stop minio

# Remove data volume
docker volume rm steel-hammer_minio-data

# Restart
docker-compose up -d minio
```

## Performance Optimization

### Enable Connection Pooling

```yaml
# docker-compose.yml environment variables
DATABASE_URL: "jdbc:postgresql://postgres:5432/ironbucket?maxPoolSize=20&minPoolSize=5"
```

### Increase Timeouts

```yaml
environment:
  SERVER_SERVLET_SESSION_TIMEOUT: "1800s"
  SPRING_JPA_HIBERNATE_JDBC_BATCH_SIZE: "25"
  SPRING_JPA_HIBERNATE_ORDER_INSERTS: "true"
  SPRING_JPA_HIBERNATE_ORDER_UPDATES: "true"
```

## Getting Help

### Debug Checklist

Before reporting issues:
1. [ ] Run latest version: `git pull && docker-compose build`
2. [ ] Clean restart: `docker-compose down -v && docker-compose up`
3. [ ] Check logs: `docker logs service-name`
4. [ ] Verify prerequisites: Java 25+, Maven 3.9+, Docker latest
5. [ ] Check port conflicts: `lsof -i :8080`
6. [ ] Check disk space: `df -h`
7. [ ] Check memory: `docker stats`

### Report Issues

When reporting issues, include:
1. OS and version
2. Docker version: `docker --version`
3. Docker Compose version: `docker-compose --version`
4. Full error message from logs
5. Steps to reproduce
6. Output of `docker ps -a`

## Status

**Troubleshooting Guide:** ✅ Comprehensive  
**Common Issues:** ✅ Covered  
**Solutions:** ✅ Provided  
**Self-Service:** ✅ Enabled
