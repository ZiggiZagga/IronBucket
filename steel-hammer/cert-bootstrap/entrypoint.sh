#!/usr/bin/env bash
set -euo pipefail

CERTS_DIR="${CERTS_DIR:-/certs}"
GENERATOR_SCRIPT="${CERTS_DIR}/generate-certificates.sh"
required_files=(
  "ca/ca.crt"
  "ca/ca-truststore.p12"
  "services/infrastructure/keycloak/tls.crt"
  "services/infrastructure/keycloak/tls.key"
  "services/infrastructure/minio/tls.crt"
  "services/infrastructure/minio/tls.key"
  "services/infrastructure/vault/tls.crt"
  "services/infrastructure/vault/tls.key"
  "services/buzzle-vane/fullchain.crt"
  "services/buzzle-vane/tls.key"
)

missing=0
for relative_path in "${required_files[@]}"; do
  if [[ ! -f "${CERTS_DIR}/${relative_path}" ]]; then
    missing=1
    break
  fi
done

if [[ "$missing" -eq 0 ]]; then
  echo "[steel-hammer-cert-bootstrap] Certificate artifacts already present"
  exit 0
fi

if [[ ! -f "$GENERATOR_SCRIPT" ]]; then
  echo "[steel-hammer-cert-bootstrap] Missing generator script: $GENERATOR_SCRIPT" >&2
  exit 1
fi

echo "[steel-hammer-cert-bootstrap] Generating certificate artifacts"
bash "$GENERATOR_SCRIPT"
echo "[steel-hammer-cert-bootstrap] Certificate generation complete"