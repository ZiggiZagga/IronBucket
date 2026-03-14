#!/usr/bin/env sh
set -eu

VAULT_ADDR="${VAULT_ADDR:-https://127.0.0.1:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-dev-root-token}"
VAULT_CACERT="${VAULT_CACERT:-/certs/ca/ca.crt}"

if ! command -v vault >/dev/null 2>&1; then
  echo "ERROR: vault CLI is required" >&2
  exit 1
fi

export VAULT_ADDR VAULT_TOKEN VAULT_CACERT

echo "Seeding Vault dev secrets at ${VAULT_ADDR}..."

# Ensure KV v2 mount exists.
if ! vault secrets list -format=json | grep -q '"secret/"'; then
  vault secrets enable -path=secret -version=2 kv >/dev/null 2>&1 || true
fi

# Sentinel-Gear presigned secret resolver expects KV v2 payload at this path.
vault kv put secret/ironbucket/sentinel-gear \
  presignedSecret="dev-presigned-secret-from-vault" \
  oidcClientSecret="dev-secret" >/dev/null

# Shared app credentials in KV v2 contexts.
vault kv put secret/ironbucket/brazz-nossel \
  app.s3.access-key="vault-managed-root-user" \
  app.s3.secret-key="vault-managed-root-password" \
  APP_S3_ACCESS_KEY="vault-managed-root-user" \
  APP_S3_SECRET_KEY="vault-managed-root-password" \
  minioAccessKey="vault-managed-root-user" \
  minioSecretKey="vault-managed-root-password" >/dev/null
vault kv put secret/ironbucket/claimspindel jwtAudience="ironbucket" >/dev/null
vault kv put secret/ironbucket/buzzle-vane eurekaTag="dev-region-1" >/dev/null
vault kv put secret/ironbucket/graphite-forge graphqlAdminMode="enabled" >/dev/null

echo "Vault dev secrets seeded successfully."
