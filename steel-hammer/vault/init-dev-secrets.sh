#!/usr/bin/env bash
set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-https://127.0.0.1:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-dev-root-token}"
VAULT_CACERT="${VAULT_CACERT:-/certs/ca/ca.crt}"

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required" >&2
  exit 1
fi

put_secret() {
  local path="$1"
  local json="$2"

  curl --cacert "${VAULT_CACERT}" -fsS \
    -H "X-Vault-Token: ${VAULT_TOKEN}" \
    -H "Content-Type: application/json" \
    -X POST \
    -d "${json}" \
    "${VAULT_ADDR}/v1/${path}" >/dev/null
}

echo "Seeding Vault dev secrets at ${VAULT_ADDR}..."

# Sentinel-Gear presigned secret resolver expects KV v2 payload at this path.
put_secret "secret/data/ironbucket/sentinel-gear" '{"data":{"presignedSecret":"dev-presigned-secret-from-vault"}}'

# Shared app credentials in KV v2 contexts.
put_secret "secret/data/ironbucket/brazz-nossel" '{"data":{"minioAccessKey":"vault-managed-root-user","minioSecretKey":"vault-managed-root-password"}}'
put_secret "secret/data/ironbucket/claimspindel" '{"data":{"jwtAudience":"ironbucket"}}'
put_secret "secret/data/ironbucket/buzzle-vane" '{"data":{"eurekaTag":"dev-region-1"}}'
put_secret "secret/data/ironbucket/graphite-forge" '{"data":{"graphqlAdminMode":"enabled"}}'

echo "Vault dev secrets seeded successfully."
