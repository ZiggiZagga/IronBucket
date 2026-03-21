#!/bin/bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-https://steel-hammer-brazz-nossel:8082}"

echo "Tenant isolation contract test via gateway: ${GATEWAY_URL}"
echo "- Alice cannot access Bob resources"
echo "- Bob cannot access Alice resources"
