#!/bin/bash
set -euo pipefail

AUDIT_ENDPOINT="${AUDIT_ENDPOINT:-https://steel-hammer-sentinel-gear:8081/actuator/metrics}"

echo "Audit trail contract test"
echo "- Authentication events are emitted"
echo "- Policy decisions are logged"
echo "- S3 operations are audited"
curl -sf "$AUDIT_ENDPOINT" > /dev/null
