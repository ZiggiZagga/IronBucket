# IronBucket mTLS Implementation Guide

## Overview

IronBucket implements mutual TLS (mTLS) for secure service-to-service communication. Every service must present a valid certificate signed by the IronBucket CA to communicate with other services.

## Architecture

```
┌─────────────────┐
│   Root CA       │
│  (10y validity) │
└────────┬────────┘
         │
    ┌────┴────────────────────────────────┐
    │                                     │
┌───▼────────────┐              ┌────────▼──────────┐
│ Service Certs  │              │ Client Test Cert  │
│ (1y validity)  │              │ (1y validity)     │
└────────────────┘              └───────────────────┘
     │
     ├── Sentinel-Gear (8080)
     ├── Claimspindel (8081)
     ├── Brazz-Nossel (8082)
     ├── Buzzle-Vane (8083)
     ├── PostgreSQL (5432)
     ├── MinIO (9000)
     └── Keycloak (8080)
```

## Certificate Structure

```
certs/
├── ca/
│   ├── ca.crt                    # Root CA certificate (public)
│   └── ca.key                    # Root CA private key (KEEP SECRET)
├── services/
│   ├── sentinel-gear/
│   │   ├── tls.crt               # Service certificate
│   │   ├── tls.key               # Service private key
│   │   ├── keystore.p12          # Java keystore (Spring Boot)
│   │   ├── truststore.p12        # CA truststore
│   │   └── fullchain.crt         # Certificate chain (cert + CA)
│   ├── claimspindel/
│   ├── brazz-nossel/
│   ├── buzzle-vane/
│   └── infrastructure/
│       ├── postgres/
│       ├── minio/
│       └── keycloak/
└── client/
    ├── client.crt                # Test client certificate
    ├── client.key                # Test client private key
    └── client.p12                # Test client PKCS12
```

## Quick Start

### 1. Generate Certificates (One-Time Setup)

```bash
cd certs
./generate-certificates.sh
```

This creates:
- Root CA (valid 10 years)
- Service certificates (valid 1 year)
- Client test certificate (valid 1 year)

### 2. Start Services with mTLS

```bash
# Start single service with mTLS profile
cd services/Sentinel-Gear
mvn spring-boot:run -Dspring-boot.run.profiles=mtls

# Or via Docker Compose (TODO: add mTLS volumes)
docker-compose -f docker-compose-mtls.yml up
```

### 3. Test mTLS Connectivity

```bash
# Test with curl (requires client certificate)
curl --cacert certs/ca/ca.crt \
     --cert certs/client/client.crt \
     --key certs/client/client.key \
     https://localhost:8080/actuator/health

# Expected: {"status":"UP"}

# Test WITHOUT client cert (should fail)
curl --cacert certs/ca/ca.crt \
     https://localhost:8080/actuator/health

# Expected: SSL handshake error (client certificate required)
```

### 4. Run mTLS Integration Tests

```bash
cd services/Sentinel-Gear
mvn test -Dtest=SentinelGearmTLSTest -Dspring.profiles.active=mtls
```

## Service Configuration

Each service has an `application-mtls.yml` with:

```yaml
server:
  ssl:
    enabled: true
    key-store: file:../../certs/services/<service-name>/keystore.p12
    key-store-password: changeit
    client-auth: need  # Require client certificates (mTLS)
    trust-store: file:../../certs/services/<service-name>/truststore.p12
    trust-store-password: changeit
    enabled-protocols: TLSv1.3,TLSv1.2
```

## Service-to-Service Communication

Services use `WebClient` with mTLS configured:

```java
@Autowired
@Qualifier("claimspindelWebClient")
private WebClient claimspindelWebClient;

// Call Claimspindel with mTLS
claimspindelWebClient
    .get()
    .uri("/api/policies")
    .retrieve()
    .bodyToMono(String.class);
```

The `MTLSClientConfig` automatically configures WebClient beans with:
- Client certificate (keystore)
- CA trust (truststore)
- TLS 1.3 protocol
- Proper hostname verification

## Security Features

### ✅ What mTLS Provides

1. **Mutual Authentication**: Both client and server verify each other
2. **Transport Encryption**: All traffic encrypted with TLS 1.3
3. **Certificate-Based Authorization**: Only services with valid certs can communicate
4. **Environment-Agnostic**: Works in Docker, Kubernetes, bare metal
5. **Zero-Trust by Default**: No implicit trust between services

### ✅ What's Enforced

- TLS 1.3 / TLS 1.2 only (no TLS 1.1, 1.0, or SSLv3)
- Strong cipher suites only (AES-256-GCM, ChaCha20)
- Client certificate required (`client-auth: need`)
- Certificate chain validation
- Hostname verification

## Certificate Rotation

### Manual Rotation (Development)

```bash
# Regenerate all certificates
cd certs
rm -rf services/ ca/ client/
./generate-certificates.sh

# Restart services to pick up new certificates
docker-compose restart
```

### Automated Rotation (Production)

- Use cert-manager (Kubernetes)
- Or HashiCorp Vault PKI engine
- Set renewal threshold to 30 days before expiry
- Monitor certificate expiration with Prometheus

## Troubleshooting

### Error: "SSL handshake error"

**Cause**: Client certificate not provided or invalid

**Fix**: Ensure client certificate is:
- Signed by the same CA
- Not expired
- Has `clientAuth` extended key usage

```bash
# Verify certificate
openssl verify -CAfile certs/ca/ca.crt certs/client/client.crt

# Check expiry
openssl x509 -in certs/client/client.crt -noout -enddate
```

### Error: "Certificate chain incomplete"

**Cause**: Server certificate doesn't chain to CA

**Fix**: Use `fullchain.crt` instead of `tls.crt`

```yaml
# application-mtls.yml
server:
  ssl:
    key-store: file:../../certs/services/<service>/keystore.p12
    # NOT: tls.crt (single cert)
```

### Error: "Connection refused"

**Cause**: Service not running or not listening on HTTPS port

**Fix**: Check service logs

```bash
docker-compose logs sentinel-gear | grep -i ssl
```

Expected:
```
Tomcat started on port(s): 8080 (https) with context path ''
```

### Error: "Unable to find valid certification path"

**Cause**: CA certificate not in truststore

**Fix**: Re-run certificate generation script

```bash
cd certs
./generate-certificates.sh
```

## Testing Checklist

- [ ] Certificates generated successfully
- [ ] All services start with `--spring.profiles.active=mtls`
- [ ] HTTPS endpoints accessible with client cert
- [ ] HTTP endpoints rejected or redirected
- [ ] Connections without client cert rejected
- [ ] Service-to-service mTLS works
- [ ] Health checks pass
- [ ] Integration tests pass

## Production Deployment

### Docker Compose

Mount certificates as volumes:

```yaml
services:
  sentinel-gear:
    volumes:
      - ./certs/services/sentinel-gear:/certs:ro
      - ./certs/ca/ca.crt:/ca/ca.crt:ro
    environment:
      - SPRING_PROFILES_ACTIVE=mtls
```

### Kubernetes

Use Kubernetes Secrets:

```bash
kubectl create secret generic sentinel-gear-tls \
  --from-file=keystore.p12=certs/services/sentinel-gear/keystore.p12 \
  --from-file=truststore.p12=certs/services/sentinel-gear/truststore.p12
```

Mount in deployment:

```yaml
volumes:
  - name: tls-certs
    secret:
      secretName: sentinel-gear-tls
```

## Best Practices

1. **Rotate certificates before expiry** (30-day threshold)
2. **Keep CA private key secure** (encrypt at rest, access control)
3. **Use different certificates per environment** (dev/staging/prod)
4. **Monitor certificate expiration** (Prometheus alerts)
5. **Test certificate rotation** in staging first
6. **Log certificate usage** for audit trails
7. **Use cert-manager** for automated renewal in production

## References

- [RFC 5246 - TLS 1.2](https://www.rfc-editor.org/rfc/rfc5246)
- [RFC 8446 - TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446)
- [Spring Boot SSL](https://docs.spring.io/spring-boot/reference/web/reactive.html#web.reactive.server.ssl)
- [mTLS Best Practices](https://www.rfc-editor.org/rfc/rfc8705)
