# IronBucket Quick Start Guide

**Setup Time**: 10 minutes  
**Prerequisites**: Docker, Docker Compose, Java 17+, Maven  
**Goal**: Run all tests and start the IronBucket system locally

---

## 1. Verify Prerequisites

```bash
# Check Java version (should be 17+)
java -version

# Check Docker & Docker Compose
docker --version
docker-compose --version

# Check Maven
mvn --version
```

If any are missing:
```bash
# Ubuntu/Debian
sudo apt-get install openjdk-25-jdk docker.io docker-compose maven

# macOS (with Homebrew)
brew install openjdk@25 docker docker-compose maven
```

---

## 2. Clone the Repository

```bash
git clone https://github.com/ZiggiZagga/IronBucket.git
cd IronBucket
```

---

## 3. Run All Tests (5 minutes)

### Java Tests

```bash
# Test Sentinel-Gear (OIDC Gateway)
cd temp/Sentinel-Gear
mvn clean test
cd ../..

# Test Brazz-Nossel (S3 Proxy)
cd temp/Brazz-Nossel
mvn clean test
cd ../..

# Test Claimspindel (Claims Router)
cd temp/Claimspindel
mvn clean test
cd ../..

# Test Buzzle-Vane (Service Discovery)
cd temp/Buzzle-Vane
mvn clean test
cd ../..
```

**Expected Output**:
```
Sentinel-Gear:   45 tests passed ✅
Brazz-Nossel:    56 tests passed ✅
Claimspindel:    72 tests passed ✅
Buzzle-Vane:     58 tests passed ✅
TOTAL:          231 tests passed ✅
```

### TypeScript Tests (Optional)

```bash
cd ironbucket-shared-testing
npm install
npm test
```

---

## 4. Start Infrastructure (3 minutes)

### Option A: Docker Compose

```bash
cd steel-hammer

# Start Keycloak (OIDC Provider)
docker-compose -f docker-compose-keycloak.yml up -d

# Start MinIO (S3-compatible Storage)
docker-compose -f docker-compose-minio.yml up -d

# Start PostgreSQL (Identity Store)
docker-compose -f docker-compose-postgres.yml up -d

# Verify services are running
docker ps
```

### Option B: Verify Services Are Running

```bash
# Check Keycloak
curl http://localhost:8080/realms/ironbucket-lab

# Check MinIO
curl http://localhost:9000/minio/health/live

# Check PostgreSQL
psql -h localhost -U ironbucket -d ironbucket -c "SELECT version();"
```

---

## 5. Build Services (2 minutes)

```bash
cd temp

# Build Sentinel-Gear
cd Sentinel-Gear && mvn clean package -DskipTests && cd ..

# Build Brazz-Nossel
cd Brazz-Nossel && mvn clean package -DskipTests && cd ..

# Build Claimspindel
cd Claimspindel && mvn clean package -DskipTests && cd ..

# Build Buzzle-Vane
cd Buzzle-Vane && mvn clean package -DskipTests && cd ..

cd ..
```

---

## 6. Start Services

### Terminal 1: Sentinel-Gear (OIDC Gateway)

```bash
cd temp/Sentinel-Gear
java -jar target/sentinelgear-0.0.1-SNAPSHOT.jar \
  --server.port=8081 \
  --spring.application.name=sentinel-gear \
  --oidc.issuer=http://localhost:8080/realms/ironbucket-lab \
  --oidc.audience=sentinel-gear-app
```

### Terminal 2: Claimspindel (Claims Router)

```bash
cd temp/Claimspindel
java -jar target/claimspindel-0.0.1-SNAPSHOT.jar \
  --server.port=8082 \
  --spring.application.name=claimspindel
```

### Terminal 3: Brazz-Nossel (S3 Proxy)

```bash
cd temp/Brazz-Nossel
java -jar target/brazznossel-0.0.1-SNAPSHOT.jar \
  --server.port=8083 \
  --spring.application.name=brazz-nossel \
  --s3.endpoint=http://minio:9000 \
  --s3.access-key=minioadmin \
  --s3.secret-key=minioadmin
```

### Terminal 4: Buzzle-Vane (Service Discovery)

```bash
cd temp/Buzzle-Vane
java -jar target/buzzlevane-0.0.1-SNAPSHOT.jar \
  --server.port=8084 \
  --spring.application.name=buzzle-vane
```

---

## 7. Test the System

### Get a Test JWT from Keycloak

```bash
# Create a test user (if not exists)
KEYCLOAK_URL=http://localhost:8080
REALM=ironbucket-lab

# Login to Keycloak admin console
# http://localhost:8080/admin
# Username: admin
# Password: admin

# Or use CLI to get a token
TOKEN=$(curl -s -X POST \
  "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=sentinel-gear-app" \
  -d "username=testuser" \
  -d "password=testuser" | jq -r '.access_token')

echo "Got token: $TOKEN"
```

### Test S3 Access Through IronBucket

```bash
# Create a bucket in MinIO first
# Login to http://localhost:9001
# Access Key: minioadmin
# Secret Key: minioadmin
# Create bucket: "test-bucket"

# Upload a file through IronBucket
curl -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "x-tenant-id: default" \
  -d "Hello from IronBucket!" \
  http://localhost:8083/s3/test-bucket/hello.txt

# Download the file
curl -H "Authorization: Bearer $TOKEN" \
  -H "x-tenant-id: default" \
  http://localhost:8083/s3/test-bucket/hello.txt

# List objects
curl -H "Authorization: Bearer $TOKEN" \
  -H "x-tenant-id: default" \
  http://localhost:8083/s3/test-bucket/?list-type=2
```

---

## 8. Monitor System Health

```bash
# Check service health
for port in 8081 8082 8083 8084; do
  echo "Service on port $port:"
  curl -s http://localhost:$port/actuator/health | jq .
  echo ""
done

# Check Docker logs
docker logs keycloak
docker logs minio
docker logs postgres

# Check Java service logs
# (see service terminals above)
```

---

## 9. Clean Up

### Stop Services

```bash
# Ctrl+C in each terminal to stop services
```

### Stop Infrastructure

```bash
cd steel-hammer

docker-compose -f docker-compose-keycloak.yml down
docker-compose -f docker-compose-minio.yml down
docker-compose -f docker-compose-postgres.yml down
```

---

## Troubleshooting

### Problem: Port Already in Use

```bash
# Find process using port (e.g., 8080)
lsof -i :8080

# Kill process (be careful!)
kill -9 <PID>
```

### Problem: Docker Permission Denied

```bash
# Add current user to docker group
sudo usermod -aG docker $USER
newgrp docker
```

### Problem: Maven Build Fails

```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Rebuild with verbose output
mvn clean package -X
```

### Problem: Service Won't Connect to Database

```bash
# Wait for PostgreSQL to be ready
docker logs postgres

# Check connectivity
psql -h localhost -U ironbucket -d ironbucket

# If connection timeout:
# Increase Docker memory: Docker Desktop -> Preferences -> Resources
```

---

## Next Steps

1. **Read the Architecture Guide**: [docs/identity-flow.md](docs/identity-flow.md)
2. **Understand Policy Engine**: [docs/policy-schema.md](docs/policy-schema.md)
3. **Deploy to Kubernetes**: [Kubernetes Guide] (Coming in Phase 5)
4. **Configure GitOps**: [docs/gitops-policies.md](docs/gitops-policies.md)
5. **Monitor Production**: [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md)

---

## Quick Reference

| Service | Port | Purpose | Health |
|---------|------|---------|--------|
| Sentinel-Gear | 8081 | OIDC Gateway | http://localhost:8081/actuator/health |
| Claimspindel | 8082 | Claims Router | http://localhost:8082/actuator/health |
| Brazz-Nossel | 8083 | S3 Proxy | http://localhost:8083/actuator/health |
| Buzzle-Vane | 8084 | Service Discovery | http://localhost:8084/actuator/health |
| Keycloak | 8080 | Identity Provider | http://localhost:8080/realms/ironbucket-lab |
| MinIO | 9001 | S3 Console | http://localhost:9001 |
| MinIO API | 9000 | S3 API | http://localhost:9000/minio/health/live |
| PostgreSQL | 5432 | Database | psql -h localhost -U ironbucket |

---

## Support

- **Documentation**: See [README.md](README.md)
- **Issues**: Report on GitHub Issues
- **Questions**: Check [docs/](docs/) for detailed guides
- **Architecture**: See [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md)

---

**You're all set!** 🚀 IronBucket is now running locally with all tests passing.
