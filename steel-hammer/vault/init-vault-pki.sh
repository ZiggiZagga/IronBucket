#!/usr/bin/env sh
# =============================================================================
# IronBucket – Vault PKI Initialisation
# =============================================================================
# Idempotent script that bootstraps:
#   1. PKI secrets engine (root CA + intermediate CA)
#   2. CRL / OCSP URLs so revocation works
#   3. Per-service roles for cert issuance
#   4. AppRole auth method with per-service roles + minimal policies
#   5. Issuance of initial certs (PEM + PKCS12) into /vault/pki-certs/<service>/
#   6. CA truststore (PKCS12) for JVM consumers
#
# Requires: vault CLI, openssl, keytool (JDK)
# Called by start-with-shared-ca.sh after KV has been seeded.
# =============================================================================
set -eu

VAULT_ADDR="${VAULT_ADDR:-https://127.0.0.1:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-$(cat /vault/file/root.token 2>/dev/null || echo 'dev-root-token')}"
VAULT_CACERT="${VAULT_CACERT:-/certs/ca/ca.crt}"
export VAULT_ADDR VAULT_TOKEN VAULT_CACERT

CERTS_OUT="/vault/pki-certs"
VAULT_DOMAIN="ironbucket.internal"
VAULT_URL="https://steel-hammer-vault:8200"
PKI_INIT_MARKER="/vault/file/.pki-initialized"

# ── Return early if already initialised (idempotent) ──────────────────────────
if [ -f "$PKI_INIT_MARKER" ]; then
  echo "[pki-init] PKI already initialised – skipping."
  return 0 2>/dev/null || exit 0
fi

echo "[pki-init] Starting Vault PKI bootstrap..."
mkdir -p "$CERTS_OUT/ca" "$CERTS_OUT/client"

# =============================================================================
# 1. Root PKI Engine
# =============================================================================
if ! vault secrets list -format=json | grep -q '"pki/"'; then
  vault secrets enable -path=pki pki
  vault secrets tune -max-lease-ttl=87600h pki  # 10 years
  echo "[pki-init] Root PKI engine enabled."
fi

# Generate root CA (only once – check if already set)
ROOT_CA_PEM="$CERTS_OUT/ca/root-ca.pem"
if [ ! -f "$ROOT_CA_PEM" ]; then
  vault write -field=certificate pki/root/generate/internal \
    common_name="IronBucket Root CA" \
    issuer_name="ironbucket-root" \
    ttl=87600h \
    key_type=rsa \
    key_bits=4096 \
    country="DE" \
    organization="IronBucket" \
    > "$ROOT_CA_PEM"
  echo "[pki-init] Root CA generated."
fi

# Configure root CA URLs
vault write pki/config/urls \
  issuing_certificates="${VAULT_URL}/v1/pki/ca" \
  crl_distribution_points="${VAULT_URL}/v1/pki/crl" \
  ocsp_servers="${VAULT_URL}/v1/pki/ocsp"

# =============================================================================
# 2. Intermediate PKI Engine
# =============================================================================
if ! vault secrets list -format=json | grep -q '"pki_int/"'; then
  vault secrets enable -path=pki_int pki
  vault secrets tune -max-lease-ttl=43800h pki_int  # 5 years
  echo "[pki-init] Intermediate PKI engine enabled."
fi

INT_CA_PEM="$CERTS_OUT/ca/intermediate-ca.pem"
INT_CHAIN_PEM="$CERTS_OUT/ca/ca-chain.pem"
if [ ! -f "$INT_CA_PEM" ]; then
  # Generate intermediate CSR
  CSR="$(vault write -field=csr pki_int/intermediate/generate/internal \
    common_name="IronBucket Intermediate CA" \
    issuer_name="ironbucket-intermediate" \
    ttl=43800h \
    key_type=rsa \
    key_bits=4096)"

  # Sign CSR with root CA
  vault write -field=certificate pki/root/sign-intermediate \
    issuer_ref="ironbucket-root" \
    csr="$CSR" \
    common_name="IronBucket Intermediate CA" \
    ttl=43800h \
    > "$INT_CA_PEM"

  # Import signed intermediate cert
  vault write pki_int/intermediate/set-signed \
    certificate=@"$INT_CA_PEM"

  echo "[pki-init] Intermediate CA signed and imported."
fi

write_pem_bundle() {
  OUTPUT_PATH="$1"
  shift

  : > "$OUTPUT_PATH"
  for PEM_FILE in "$@"; do
    sed -e '$a\' "$PEM_FILE" >> "$OUTPUT_PATH"
  done
}

# Build trust chain (intermediate -> root)
write_pem_bundle "$INT_CHAIN_PEM" "$INT_CA_PEM" "$ROOT_CA_PEM"

# Configure intermediate CA URLs
vault write pki_int/config/urls \
  issuing_certificates="${VAULT_URL}/v1/pki_int/ca" \
  crl_distribution_points="${VAULT_URL}/v1/pki_int/crl" \
  ocsp_servers="${VAULT_URL}/v1/pki_int/ocsp"

# =============================================================================
# 3. Create Per-Service Roles
# =============================================================================
create_role() {
  ROLE_NAME="$1"
  SERVICE_CN="$2"
  ALLOWED_DOMAINS="$3"

  vault write "pki_int/roles/${ROLE_NAME}" \
    issuer_ref="$(vault read -field=default pki_int/config/issuers)" \
    allowed_domains="${ALLOWED_DOMAINS}" \
    allow_bare_domains=true \
    allow_subdomains=true \
    allow_localhost=true \
    allow_ip_sans=true \
    allowed_uri_sans="spiffe://ironbucket.internal/${ROLE_NAME}" \
    server_flag=true \
    client_flag=true \
    key_type=rsa \
    key_bits=2048 \
    max_ttl=8760h \
    ttl=8760h \
    require_cn=false
  echo "[pki-init] Role '${ROLE_NAME}' for CN '${SERVICE_CN}' created."
}

create_role "sentinel-gear"   "sentinel-gear.${VAULT_DOMAIN}"   "sentinel-gear.${VAULT_DOMAIN},steel-hammer-sentinel-gear,localhost"
create_role "claimspindel"    "claimspindel.${VAULT_DOMAIN}"    "claimspindel.${VAULT_DOMAIN},steel-hammer-claimspindel,localhost"
create_role "brazz-nossel"    "brazz-nossel.${VAULT_DOMAIN}"    "brazz-nossel.${VAULT_DOMAIN},steel-hammer-brazz-nossel,localhost"
create_role "buzzle-vane"     "buzzle-vane.${VAULT_DOMAIN}"     "buzzle-vane.${VAULT_DOMAIN},steel-hammer-buzzle-vane,localhost"
create_role "graphite-forge"  "graphite-forge.${VAULT_DOMAIN}"  "graphite-forge.${VAULT_DOMAIN},steel-hammer-graphite-forge,localhost"
create_role "keycloak"        "keycloak.${VAULT_DOMAIN}"        "keycloak.${VAULT_DOMAIN},steel-hammer-keycloak,localhost"
create_role "minio"           "minio.${VAULT_DOMAIN}"           "minio.${VAULT_DOMAIN},steel-hammer-minio,localhost"
create_role "postgres"        "postgres.${VAULT_DOMAIN}"        "postgres.${VAULT_DOMAIN},steel-hammer-postgres,localhost"

# Role for client (mTLS test clients and service-to-service)
vault write pki_int/roles/ironbucket-client \
  issuer_ref="$(vault read -field=default pki_int/config/issuers)" \
  allowed_domains="${VAULT_DOMAIN},localhost" \
  allow_subdomains=true \
  allow_localhost=true \
  allow_ip_sans=true \
  server_flag=false \
  client_flag=true \
  key_type=rsa \
  key_bits=2048 \
  max_ttl=8760h \
  ttl=8760h
echo "[pki-init] Client cert role created."

# =============================================================================
# 4. Issue Initial Certs for All Services (PEM + PKCS12 for JVM)
# =============================================================================
KEYSTORE_PASS="changeit"

extract_json_pem_field() {
  JSON_INPUT="$1"
  FIELD_NAME="$2"

  printf '%s' "$JSON_INPUT" | tr -d '\n' | \
    sed -n "s/.*\"${FIELD_NAME}\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | \
    sed 's/\\n/\
/g; s/\\"/"/g; s/\\\\/\\/g'
}

issue_cert() {
  SERVICE="$1"
  CN="$2"
  ALT_NAMES="$3"

  SVCDIR="$CERTS_OUT/services/$SERVICE"
  mkdir -p "$SVCDIR"

  # Skip if cert already exists and not expired
  EXISTING="$SVCDIR/tls.crt"
  if [ -f "$EXISTING" ]; then
    EXPIRY="$(openssl x509 -enddate -noout -in "$EXISTING" 2>/dev/null | cut -d= -f2 || echo '')"
    if [ -n "$EXPIRY" ]; then
      # Check validity: returns 0 if valid for more than 7 days
      if openssl x509 -checkend 604800 -noout -in "$EXISTING" 2>/dev/null; then
        echo "[pki-init] Cert for '$SERVICE' still valid – skipping issuance."
        return 0
      fi
    fi
  fi

  echo "[pki-init] Issuing cert for '$SERVICE' (CN: $CN)..."

  # Issue cert from Vault PKI
  ISSUE_JSON="$(vault write -format=json "pki_int/issue/${SERVICE}" \
    common_name="${CN}" \
    alt_names="${ALT_NAMES}" \
    ip_sans="127.0.0.1" \
    uri_sans="spiffe://ironbucket.internal/${SERVICE}" \
    ttl=8760h \
    format=pem)"

  # Extract cert components
  extract_json_pem_field "$ISSUE_JSON" "certificate" > "$SVCDIR/tls.crt"
  extract_json_pem_field "$ISSUE_JSON" "private_key" > "$SVCDIR/tls.key"
  if [ ! -s "$SVCDIR/tls.crt" ] || [ ! -s "$SVCDIR/tls.key" ]; then
    echo "[pki-init] ERROR: Empty certificate material extracted for '$SERVICE'." >&2
    exit 1
  fi
  cp "$INT_CA_PEM" "$SVCDIR/issuing-ca.crt"
  cp "$INT_CHAIN_PEM" "$SVCDIR/ca-chain.crt"

  # Build fullchain (leaf + intermediates)
  write_pem_bundle "$SVCDIR/fullchain.crt" "$SVCDIR/tls.crt" "$SVCDIR/ca-chain.crt"

  chmod 644 "$SVCDIR/tls.key" "$SVCDIR/tls.crt" "$SVCDIR/fullchain.crt" "$SVCDIR/ca-chain.crt"

  # Build PKCS12 keystore for JVM services
  openssl pkcs12 -export \
    -inkey "$SVCDIR/tls.key" \
    -in "$SVCDIR/tls.crt" \
    -certfile "$SVCDIR/ca-chain.crt" \
    -name "$SERVICE" \
    -out "$SVCDIR/keystore.p12" \
    -passout "pass:${KEYSTORE_PASS}" 2>/dev/null
  chmod 644 "$SVCDIR/keystore.p12"

  echo "[pki-init] Cert issued and stored at $SVCDIR"
}

# Issue certs for all app services
issue_cert "sentinel-gear"  "steel-hammer-sentinel-gear"  "steel-hammer-sentinel-gear,sentinel-gear.${VAULT_DOMAIN},localhost"
issue_cert "claimspindel"   "steel-hammer-claimspindel"   "steel-hammer-claimspindel,claimspindel.${VAULT_DOMAIN},localhost"
issue_cert "brazz-nossel"   "steel-hammer-brazz-nossel"   "steel-hammer-brazz-nossel,brazz-nossel.${VAULT_DOMAIN},localhost"
issue_cert "buzzle-vane"    "steel-hammer-buzzle-vane"    "steel-hammer-buzzle-vane,buzzle-vane.${VAULT_DOMAIN},localhost"
issue_cert "graphite-forge" "steel-hammer-graphite-forge" "steel-hammer-graphite-forge,graphite-forge.${VAULT_DOMAIN},localhost"

# Issue certs for infrastructure (used by healthchecks / inter-service)
issue_cert "keycloak"  "steel-hammer-keycloak"  "steel-hammer-keycloak,keycloak.${VAULT_DOMAIN},localhost"
issue_cert "minio"     "steel-hammer-minio"     "steel-hammer-minio,minio.${VAULT_DOMAIN},localhost"
issue_cert "postgres"  "steel-hammer-postgres"  "steel-hammer-postgres,postgres.${VAULT_DOMAIN},localhost"

# =============================================================================
# 5. Issue mTLS Client Certs (bob, charly, generic service-client)
# =============================================================================
issue_client_cert() {
  NAME="$1"
  CN="$2"
  CLIENTDIR="$CERTS_OUT/client"

  EXISTING="$CLIENTDIR/${NAME}.crt"
  if [ -f "$EXISTING" ] && openssl x509 -checkend 604800 -noout -in "$EXISTING" 2>/dev/null; then
    echo "[pki-init] Client cert '$NAME' still valid – skipping."
    return 0
  fi

  echo "[pki-init] Issuing client cert '$NAME'..."
  ISSUE_JSON="$(vault write -format=json pki_int/issue/ironbucket-client \
    common_name="${CN}" \
    ttl=8760h \
    format=pem)"

  extract_json_pem_field "$ISSUE_JSON" "certificate" > "$CLIENTDIR/${NAME}.crt"
  extract_json_pem_field "$ISSUE_JSON" "private_key" > "$CLIENTDIR/${NAME}.key"
  if [ ! -s "$CLIENTDIR/${NAME}.crt" ] || [ ! -s "$CLIENTDIR/${NAME}.key" ]; then
    echo "[pki-init] ERROR: Empty client certificate material extracted for '$NAME'." >&2
    exit 1
  fi
  openssl pkcs12 -export \
    -inkey "$CLIENTDIR/${NAME}.key" \
    -in "$CLIENTDIR/${NAME}.crt" \
    -name "$NAME" \
    -out "$CLIENTDIR/${NAME}.p12" \
    -passout "pass:${KEYSTORE_PASS}" 2>/dev/null
  chmod 644 "$CLIENTDIR/${NAME}.key" "$CLIENTDIR/${NAME}.p12"
  echo "[pki-init] Client cert '$NAME' stored at $CLIENTDIR"
}

issue_client_cert "bob"    "bob.ironbucket.internal"
issue_client_cert "charly" "charly.ironbucket.internal"
issue_client_cert "client" "client.ironbucket.internal"

# =============================================================================
# 6. Build CA Truststore for JVM (contains Vault PKI root + intermediate)
# =============================================================================
CA_TRUSTSTORE="$CERTS_OUT/ca/ca-truststore.p12"
if [ ! -f "$CA_TRUSTSTORE" ] || [ "$INT_CHAIN_PEM" -nt "$CA_TRUSTSTORE" ] 2>/dev/null; then
  echo "[pki-init] Building CA truststore..."

  # Import root CA
  keytool -importcert -noprompt \
    -alias ironbucket-root-ca \
    -file "$ROOT_CA_PEM" \
    -keystore "$CA_TRUSTSTORE" \
    -storetype PKCS12 \
    -storepass "$KEYSTORE_PASS" 2>/dev/null || true

  # Import intermediate CA
  keytool -importcert -noprompt \
    -alias ironbucket-intermediate-ca \
    -file "$INT_CA_PEM" \
    -keystore "$CA_TRUSTSTORE" \
    -storetype PKCS12 \
    -storepass "$KEYSTORE_PASS" 2>/dev/null || true

  # Import the bootstrap CA that signed Vault's own TLS cert (from /certs volume)
  # Vault's listener TLS cert predates this PKI and is signed by a separate bootstrap CA
  if [ -f "/certs/ca/ca.crt" ]; then
    keytool -importcert -noprompt \
      -alias vault-bootstrap-ca \
      -file "/certs/ca/ca.crt" \
      -keystore "$CA_TRUSTSTORE" \
      -storetype PKCS12 \
      -storepass "$KEYSTORE_PASS" 2>/dev/null || true
    echo "[pki-init] Bootstrap CA imported into truststore."
  fi

  # Consolidated trust PEM for curl / Node / openssl consumers (includes bootstrap CA)
  if [ -f "/certs/ca/ca.crt" ]; then
    write_pem_bundle "$CERTS_OUT/ca/ca.crt" "$ROOT_CA_PEM" "$INT_CA_PEM" "/certs/ca/ca.crt"
  else
    write_pem_bundle "$CERTS_OUT/ca/ca.crt" "$ROOT_CA_PEM" "$INT_CA_PEM"
  fi
  cp "$CERTS_OUT/ca/ca.crt" "$CERTS_OUT/ca/ca-chain.pem"
  chmod 644 "$CERTS_OUT/ca/ca.crt" "$CA_TRUSTSTORE"
  echo "[pki-init] CA truststore created."
fi

# =============================================================================
# 7. Vault Policies & AppRole Auth
# =============================================================================
echo "[pki-init] Configuring AppRole auth and policies..."

# Enable AppRole if not already enabled
if ! vault auth list -format=json | grep -q '"approle/"'; then
  vault auth enable approle
  echo "[pki-init] AppRole auth method enabled."
fi

# Helper function: create a minimal KV-read policy for a service
create_service_policy() {
  SERVICE="$1"
  KV_PATH="secret/data/ironbucket/${SERVICE}"
  vault policy write "ironbucket-${SERVICE}" - <<POLICY
path "${KV_PATH}" {
  capabilities = ["read"]
}
path "${KV_PATH}/*" {
  capabilities = ["read"]
}
path "pki_int/issue/${SERVICE}" {
  capabilities = ["create", "update"]
}
path "pki_int/revoke" {
  capabilities = ["create", "update"]
}
path "sys/leases/renew" {
  capabilities = ["update"]
}
POLICY
  echo "[pki-init] Policy 'ironbucket-${SERVICE}' written."
}

for SVC in sentinel-gear claimspindel brazz-nossel buzzle-vane graphite-forge; do
  create_service_policy "$SVC"
done

# Create AppRole roles and persist role_id + secret_id to shared volume
APPROLE_DIR="$CERTS_OUT/.approle"
mkdir -p "$APPROLE_DIR"
chmod 755 "$APPROLE_DIR"

create_approle() {
  SERVICE="$1"

  # Create AppRole role
  vault write "auth/approle/role/${SERVICE}" \
    token_policies="ironbucket-${SERVICE}" \
    token_ttl=1h \
    token_max_ttl=4h \
    secret_id_ttl=0 \
    bind_secret_id=true

  # Write role_id to file
  vault read -field=role_id "auth/approle/role/${SERVICE}/role-id" \
    > "$APPROLE_DIR/${SERVICE}.role_id"

  # Generate secret_id
  vault write -field=secret_id -f "auth/approle/role/${SERVICE}/secret-id" \
    > "$APPROLE_DIR/${SERVICE}.secret_id"

  chmod 644 "$APPROLE_DIR/${SERVICE}.role_id" "$APPROLE_DIR/${SERVICE}.secret_id"
  echo "[pki-init] AppRole '${SERVICE}' created."
}

for SVC in sentinel-gear claimspindel brazz-nossel buzzle-vane graphite-forge; do
  create_approle "$SVC"
done

# =============================================================================
# 8. Mark Initialisation Complete & Signal Readiness
# =============================================================================
touch "$PKI_INIT_MARKER"
# Create a readiness file that other containers can watch for
touch "$CERTS_OUT/.ready"
chmod 644 "$CERTS_OUT/.ready"

echo "[pki-init] Vault PKI bootstrap complete."
echo "[pki-init]   Root CA  : $ROOT_CA_PEM"
echo "[pki-init]   Int CA   : $INT_CA_PEM"
echo "[pki-init]   Certs    : $CERTS_OUT/services/"
echo "[pki-init]   Truststore: $CA_TRUSTSTORE"
echo "[pki-init]   AppRoles : $APPROLE_DIR"
