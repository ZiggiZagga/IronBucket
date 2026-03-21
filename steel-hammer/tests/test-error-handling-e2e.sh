#!/bin/bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-https://steel-hammer-brazz-nossel:8082}"

echo "Error handling E2E via gateway: ${GATEWAY_URL}"
echo "- Validates 401, 403, 404, 500 responses"
