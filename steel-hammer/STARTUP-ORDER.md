# IronBucket Container Startup Order

## Service Dependency Graph

```
Level 1 (Base Infrastructure - No Dependencies)
├─ PostgreSQL (steel-hammer-postgres)
└─ MinIO (steel-hammer-minio)

Level 2 (Identity Provider)
└─ Keycloak (steel-hammer-keycloak)
   └─ Depends on: PostgreSQL

Level 3 (Service Discovery)
└─ Buzzle-Vane / Eureka Server (steel-hammer-buzzle-vane)
   └─ Depends on: PostgreSQL, Keycloak

Level 4 (API Gateway)
└─ Sentinel-Gear (steel-hammer-sentinel-gear)
   └─ Depends on: PostgreSQL, Keycloak, Buzzle-Vane (Eureka)
   └─ Exposes: Port 8080 (API), Port 8081 (Management)

Level 5 (Policy Engine)
└─ Claimspindel (steel-hammer-claimspindel)
   └─ Depends on: PostgreSQL, Keycloak, Buzzle-Vane, Sentinel-Gear

Level 6 (S3 Proxy)
└─ Brazz-Nossel (steel-hammer-brazz-nossel)
   └─ Depends on: PostgreSQL, Keycloak, Buzzle-Vane, Claimspindel, MinIO

Level 7 (E2E Testing)
└─ Test Client (steel-hammer-test)
   └─ Depends on: All services above
```

## Startup Sequence

### Phase 1: Infrastructure
1. **PostgreSQL** starts (no dependencies)
   - Initializes database for Keycloak, Sentinel-Gear, etc.

2. **MinIO** starts (no dependencies)
   - Provides S3-compatible object storage

### Phase 2: Identity & Discovery
3. **Keycloak** starts (waits for PostgreSQL)
   - Provides OIDC/OAuth2 identity services
   - Available at: `http://steel-hammer-keycloak:7081`

4. **Buzzle-Vane (Eureka)** starts (waits for PostgreSQL + Keycloak)
   - Provides service discovery and registration
   - Available at: `http://steel-hammer-buzzle-vane:8083/eureka`

### Phase 3: API & Routing
5. **Sentinel-Gear (Gateway)** starts (waits for PostgreSQL + Keycloak + Eureka)
   - Spring Cloud Gateway with OIDC integration
   - Exposes: `http://localhost:8080` (external API)
   - Discovers downstream services via Eureka

### Phase 4: Core Services
6. **Claimspindel (Policy Engine)** starts (waits for all Level 4 services)
   - Policy evaluation and ABAC/RBAC enforcement
   - Registers with Eureka as downstream of Gateway

7. **Brazz-Nossel (S3 Proxy)** starts (waits for all Level 5 services + MinIO)
   - S3-compatible request routing with policy integration
   - Connects to MinIO for object storage

### Phase 5: Testing
8. **Test Client** starts (waits for all services)
   - Runs E2E tests after 60-second initialization window
   - Tests complete flow: Gateway → Policy → S3 Proxy

## Health Check Timeline

Each service includes health check probes:
- **Interval**: 10 seconds
- **Timeout**: 5 seconds
- **Retries**: 3 attempts

Expected health progression:
1. PostgreSQL: Healthy (immediate)
2. MinIO: Healthy (10-30 seconds)
3. Keycloak: Healthy (60-90 seconds, due to Quarkus startup)
4. Buzzle-Vane: Healthy (40-60 seconds)
5. Sentinel-Gear: Healthy (60-80 seconds)
6. Claimspindel: Healthy (50-70 seconds)
7. Brazz-Nossel: Healthy (60-80 seconds)

**Total startup time**: ~90-120 seconds

## Benefits of This Ordering

1. **Reduced Connection Errors**: Services wait for dependencies
2. **Improved Eureka Registration**: Gateway and microservices register cleanly
3. **Graceful Degradation**: If a service fails, downstream services wait
4. **Clear Dependency Graph**: Easy to understand service relationships
5. **Faster E2E Testing**: Tests only start after all services are ready

## Manual Startup

If you need to start services individually:

```bash
# Start in order:
docker-compose -f docker-compose-steel-hammer.yml up -d steel-hammer-postgres steel-hammer-minio
sleep 30
docker-compose -f docker-compose-steel-hammer.yml up -d steel-hammer-keycloak
sleep 30
docker-compose -f docker-compose-steel-hammer.yml up -d steel-hammer-buzzle-vane
sleep 30
docker-compose -f docker-compose-steel-hammer.yml up -d steel-hammer-sentinel-gear steel-hammer-claimspindel
sleep 30
docker-compose -f docker-compose-steel-hammer.yml up -d steel-hammer-brazz-nossel
sleep 30
docker-compose -f docker-compose-steel-hammer.yml up -d steel-hammer-test
```

## Troubleshooting

If services don't reach healthy state:

1. **PostgreSQL unhealthy**: Check database logs
   ```bash
   docker logs steel-hammer-postgres
   ```

2. **Keycloak unhealthy**: Check Keycloak startup logs
   ```bash
   docker logs steel-hammer-keycloak | tail -50
   ```

3. **Buzzle-Vane unhealthy**: Check Eureka server logs
   ```bash
   docker logs steel-hammer-buzzle-vane
   ```

4. **Sentinel-Gear unhealthy**: Check gateway logs
   ```bash
   docker logs steel-hammer-sentinel-gear
   ```

5. **Services can't register with Eureka**: Verify network connectivity
   ```bash
   docker network inspect steel-hammer_steel-hammer-network
   ```
