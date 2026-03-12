# Presigned Replay/Tamper Runbook

## Scope

This runbook covers Sentinel-Gear incidents where presigned request validation detects:
- replayed nonce attempts
- signature tampering
- expired presigned payload usage
- missing or inconsistent signed headers

## Required Runtime Configuration

Set these variables in each deployment environment:
- `IRONBUCKET_SECURITY_PRESIGNED_ENABLED=true`
- `IRONBUCKET_SECURITY_PRESIGNED_SECRET=<shared-secret>`
- `IRONBUCKET_SECURITY_PRESIGNED_NONCE_TTL=PT5M`

Sentinel-Gear is expected to fail fast at startup when presigned validation is enabled and no secret is provided.

## Detection Signals

Use these primary sources:
- Sentinel-Gear logs in the active environment
- Alerting rules for security validation failures
- Request traces around gateway rejection events

Typical indicators:
- repeated nonce value for the same tenant/object path
- HMAC validation failure
- presigned expiry check failure
- signed-header mismatch failures

## Immediate Triage (Same Working Day SLA)

1. Confirm active severity and impact window.
2. Identify affected tenant, bucket, and object path from gateway logs.
3. Determine failure class: replay, tamper, expiry, or malformed request.
4. Check whether failures correlate to a single client rollout or broad traffic.
5. Preserve evidence: logs, request IDs, traces, and timestamp range.

## Containment Actions

1. Rotate `IRONBUCKET_SECURITY_PRESIGNED_SECRET` in the environment secret store.
2. Restart Sentinel-Gear pods to pick up rotated secret.
3. For suspected replay bursts, temporarily reduce `IRONBUCKET_SECURITY_PRESIGNED_NONCE_TTL`.
4. If client signing bug is confirmed, coordinate rollback/fix with client owners.

## Secret Rotation Procedure

Kubernetes example:

```bash
kubectl -n ironbucket-prod create secret generic ironbucket-secrets \
  --from-literal=ironbucket-security-presigned-secret="$(openssl rand -base64 64)" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n ironbucket-prod rollout restart deployment/sentinel-gear
kubectl -n ironbucket-prod rollout status deployment/sentinel-gear
```

Post-rotation checks:
1. Run release smoke gate locally or in CI: `bash scripts/ci/run-presigned-security-smoke.sh`
2. Verify Sentinel-Gear health endpoints are green.
3. Confirm rejection rates return to expected baseline.

## Recovery Validation Checklist

- Secret rotated and rollout completed
- No startup failures related to missing presigned secret
- Presigned smoke gate passing
- No sustained replay/tamper alert spikes
- Incident timeline documented in ops tracker

## Escalation

- Service owner: Sentinel-Gear maintainers
- Platform owner: CI/CD and runtime operations maintainer on duty
- Escalate to security lead if repeated tamper attempts persist beyond one rotation cycle
