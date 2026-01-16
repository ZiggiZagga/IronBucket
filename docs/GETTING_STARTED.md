# Getting Started with IronBucket

## Prerequisites

- **Docker** and **Docker Compose**
- **Java 25** or later
- **Maven 3.9** or later
- **4GB RAM** minimum
- **Linux/macOS/Windows** (with Docker Desktop)

## 5-Minute Quick Start

### 1. Navigate to Steel Hammer
```bash
cd steel-hammer
```

### 2. Start All Services
```bash
docker-compose -f docker-compose-steel-hammer.yml up -d --build
```

This will start:
- PostgreSQL (metadata)
- MinIO (S3 storage)
- Keycloak (OIDC identity)
- Buzzle-Vane (service discovery)
- Sentinel-Gear (JWT validator)
- Claimspindel (policy router)
- Brazz-Nossel (S3 proxy)
- Test container (E2E verification)

### 3. Wait for Startup (120-180 seconds)
```bash
sleep 180
```

### 4. Check Results
```bash
docker logs steel-hammer-test | tail -150
```

You should see:
```
✅ Phase 1: Maven tests - SUCCESS
✅ Phase 2: Service health - SUCCESS
✅ Phase 3: E2E Flow - SUCCESSFUL
✅ Phase 4: JWT Authentication Enforcement
```

## What Just Happened

1. **Phase 1:** Ran all unit tests across 6 projects (231 tests)
2. **Phase 2:** Verified all services are running and healthy
3. **Phase 3:** Tested complete file upload/download flow to MinIO
4. **Phase 4:** Verified JWT authentication is enforced

If you see all ✅ checkmarks, **IronBucket is working correctly**.

## Manual Service Access

### MinIO Console
- **URL:** http://localhost:9001
- **Username:** minioadmin
- **Password:** minioadmin

### Keycloak
- **URL:** http://localhost:8080
- **Username:** admin
- **Password:** admin

### Service Ports

| Service | Port | Purpose |
|---------|------|---------|
| Sentinel-Gear | 8080 | JWT validation |
| Claimspindel | 8081 | Policy routing |
| Brazz-Nossel | 8082 | S3 proxy |
| Buzzle-Vane | 8083 | Service discovery |
| MinIO | 9000 | S3 storage |
| MinIO Console | 9001 | Web UI |
| Keycloak | 8080 | Identity provider |
| PostgreSQL | 5432 | Metadata DB |

## Test Users

Pre-configured in Keycloak:

| Username | Role | Password |
|----------|------|----------|
| bob | dev | bobP@ss |
| alice | admin | aliceP@ss |

## Stopping Services

```bash
docker-compose -f docker-compose-steel-hammer.yml down
```

## Cleaning Everything

```bash
docker-compose -f docker-compose-steel-hammer.yml down -v
```

This removes volumes and temporary data.

## Troubleshooting

### Services not starting?
```bash
# Check logs
docker logs steel-hammer-test

# Check container status
docker ps
```

### Port already in use?
```bash
# Find what's using port 8080
lsof -i :8080

# Or use different port in docker-compose
```

### Out of disk space?
```bash
# Clean up Docker
docker system prune -a
```

## Next Steps

- Read [ARCHITECTURE.md](ARCHITECTURE.md) to understand the system design
- See [TESTING.md](TESTING.md) for running tests manually
- Check [DEPLOYMENT.md](DEPLOYMENT.md) for production setup
- Review [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues

## Production Deployment

For production, see [DEPLOYMENT.md](DEPLOYMENT.md) with:
- TLS/HTTPS configuration
- High availability setup
- Security hardening
- Performance tuning

**Status:** ✅ Production Ready
