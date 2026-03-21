#!/bin/bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-https://steel-hammer-brazz-nossel:8082}"

echo "Policy enforcement E2E via gateway: ${GATEWAY_URL}"
echo "- Unauthorized request => 401/403"
echo "- Authorized request => 2xx"
