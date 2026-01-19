# IronBucket Network Policies

Kubernetes NetworkPolicies für Zero-Trust Network Architecture.

## Structure

```
network-policies/
├── base/                          # Base network policies (deny-all default)
│   └── deny-all-default.yaml
├── ingress-tier/                  # External traffic only
│   └── sentinel-gear-ingress.yaml
├── service-tier/                  # Service-to-service communication
│   ├── sentinel-gear-policies.yaml
│   ├── claimspindel-policies.yaml
│   ├── brazz-nossel-policies.yaml
│   └── buzzle-vane-policies.yaml
├── infrastructure-tier/           # System services
│   ├── postgres-policies.yaml
│   ├── minio-policies.yaml
│   └── keycloak-policies.yaml
└── kustomization.yaml
```

## Deployment

```bash
# Apply all policies
kubectl apply -k ./

# Verify policies are deployed
kubectl get networkpolicies -n default

# Test connectivity
./scripts/test-network-policies.sh
```

## Policy Tiers

### Tier 1: Ingress (External Traffic)
- Sentinel-Gear (8080) ← Ingress Controller only

### Tier 2: Service-to-Service
- Sentinel-Gear → Claimspindel (8081)
- Sentinel-Gear → Brazz-Nossel (8082)
- Sentinel-Gear → Buzzle-Vane (8083)
- Sentinel-Gear → Keycloak (8080)
- Claimspindel → PostgreSQL (5432)
- Brazz-Nossel → MinIO (9000)
- Brazz-Nossel → PostgreSQL (5432)

### Tier 3: Infrastructure
- Default deny all egress/ingress
- Explicit allows only for documented flows

## Testing

Run integration tests:
```bash
cd services/Sentinel-Gear
mvn test -Dtest=*NetworkPolicy*
```

## Monitoring & Debugging

### Check which policies are blocking traffic
```bash
kubectl logs -n kube-system -l component=kube-controller-manager | grep networkpolicy

# Or use network policy visualization
kubectl describe networkpolicy sentinel-gear-allow-ingress
```

### Allow temporary debugging
```bash
kubectl label pod <pod-name> debug=true
# (temporarily allow all for this pod via label selector)
```

### Audit logging
```bash
kubectl get events --all-namespaces | grep NetworkPolicy
```
