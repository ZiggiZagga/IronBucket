#!/bin/bash

###############################################################################
# Certificate Generation Script for IronBucket mTLS
#
# This script generates:
# 1. Root CA (Certificate Authority)
# 2. Service certificates for each microservice
# 3. Client certificates for testing
#
# All certificates are signed by the same CA for mutual trust.
#
# Usage: ./generate-certificates.sh
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERTS_DIR="${SCRIPT_DIR}"
CA_DIR="${CERTS_DIR}/ca"
SERVICES_DIR="${CERTS_DIR}/services"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== IronBucket mTLS Certificate Generation ===${NC}\n"

# Create directories
mkdir -p "${CA_DIR}"
mkdir -p "${SERVICES_DIR}"/{sentinel-gear,claimspindel,brazz-nossel,buzzle-vane}
mkdir -p "${SERVICES_DIR}/infrastructure"/{postgres,minio,keycloak,vault}

###############################################################################
# Step 1: Generate Root CA
###############################################################################
echo -e "${BLUE}Step 1: Generating Root CA...${NC}"

if [ -f "${CA_DIR}/ca.key" ]; then
  echo -e "${YELLOW}⚠ Root CA already exists, skipping generation${NC}"
else
  # Generate CA private key (4096-bit RSA)
  openssl genrsa -out "${CA_DIR}/ca.key" 4096
  
  # Generate CA certificate (valid for 10 years)
  openssl req -x509 -new -nodes \
    -key "${CA_DIR}/ca.key" \
    -sha256 \
    -days 3650 \
    -out "${CA_DIR}/ca.crt" \
    -subj "/C=US/ST=California/L=San Francisco/O=IronBucket/OU=Security/CN=IronBucket Root CA"
  
  echo -e "${GREEN}✓ Root CA generated${NC}"
  echo "  CA Certificate: ${CA_DIR}/ca.crt"
  echo "  CA Key: ${CA_DIR}/ca.key"
fi

echo ""

###############################################################################
# Step 2: Generate Service Certificates
###############################################################################
echo -e "${BLUE}Step 2: Generating service certificates...${NC}\n"

generate_service_cert() {
  local service_name=$1
  local service_cn=$2
  local service_dns=${3:-$(basename "${service_name}")}
  local service_dir="${SERVICES_DIR}/${service_name}"
  
  echo -e "${BLUE}Generating certificate for ${service_name}...${NC}"
  
  # Generate service private key
  openssl genrsa -out "${service_dir}/tls.key" 2048
  
  # Generate certificate signing request (CSR)
  openssl req -new \
    -key "${service_dir}/tls.key" \
    -out "${service_dir}/tls.csr" \
    -subj "/C=US/ST=California/L=San Francisco/O=IronBucket/OU=${service_name}/CN=${service_cn}"
  
  # Create certificate extension file for SAN (Subject Alternative Names)
  cat > "${service_dir}/cert-ext.cnf" <<EOF
subjectAltName = DNS:${service_dns},DNS:localhost,DNS:${service_cn},IP:127.0.0.1
extendedKeyUsage = serverAuth,clientAuth
EOF
  
  # Sign certificate with CA (valid for 365 days)
  openssl x509 -req \
    -in "${service_dir}/tls.csr" \
    -CA "${CA_DIR}/ca.crt" \
    -CAkey "${CA_DIR}/ca.key" \
    -CAcreateserial \
    -out "${service_dir}/tls.crt" \
    -days 365 \
    -sha256 \
    -extfile "${service_dir}/cert-ext.cnf"
  
  # Verify certificate
  if openssl verify -CAfile "${CA_DIR}/ca.crt" "${service_dir}/tls.crt" &>/dev/null; then
    echo -e "${GREEN}✓${NC} ${service_name} certificate valid"
  else
    echo -e "${RED}✗${NC} ${service_name} certificate invalid"
    exit 1
  fi
  
  # Create Java keystore (PKCS12) for Spring Boot
  openssl pkcs12 -export \
    -in "${service_dir}/tls.crt" \
    -inkey "${service_dir}/tls.key" \
    -out "${service_dir}/keystore.p12" \
    -name "${service_name}" \
    -passout pass:changeit
  
  # Create truststore with CA certificate
  keytool -import -trustcacerts -noprompt \
    -alias ca \
    -file "${CA_DIR}/ca.crt" \
    -keystore "${service_dir}/truststore.p12" \
    -storepass changeit \
    -storetype PKCS12 2>/dev/null || true
  
  echo -e "${GREEN}✓${NC} ${service_name} keystore and truststore created"
  echo ""
}

# Generate certificates for each service
generate_service_cert "sentinel-gear" "sentinel-gear.ironbucket.local"
generate_service_cert "claimspindel" "claimspindel.ironbucket.local"
generate_service_cert "brazz-nossel" "brazz-nossel.ironbucket.local"
generate_service_cert "buzzle-vane" "buzzle-vane.ironbucket.local"

# Infrastructure services
generate_service_cert "infrastructure/postgres" "postgres.ironbucket.local" "postgres"
generate_service_cert "infrastructure/minio" "minio.ironbucket.local" "minio"
generate_service_cert "infrastructure/keycloak" "keycloak.ironbucket.local" "steel-hammer-keycloak"
generate_service_cert "infrastructure/vault" "vault.ironbucket.local" "steel-hammer-vault"

###############################################################################
# Step 3: Generate client certificate for testing
###############################################################################
echo -e "${BLUE}Step 3: Generating client test certificate...${NC}"

CLIENT_DIR="${CERTS_DIR}/client"
mkdir -p "${CLIENT_DIR}"

openssl genrsa -out "${CLIENT_DIR}/client.key" 2048

openssl req -new \
  -key "${CLIENT_DIR}/client.key" \
  -out "${CLIENT_DIR}/client.csr" \
  -subj "/C=US/ST=California/L=San Francisco/O=IronBucket/OU=Testing/CN=test-client"

cat > "${CLIENT_DIR}/client-ext.cnf" <<EOF
extendedKeyUsage = clientAuth
EOF

openssl x509 -req \
  -in "${CLIENT_DIR}/client.csr" \
  -CA "${CA_DIR}/ca.crt" \
  -CAkey "${CA_DIR}/ca.key" \
  -CAcreateserial \
  -out "${CLIENT_DIR}/client.crt" \
  -days 365 \
  -sha256 \
  -extfile "${CLIENT_DIR}/client-ext.cnf"

echo -e "${GREEN}✓ Client certificate generated${NC}\n"

###############################################################################
# Step 4: Create certificate bundle
###############################################################################
echo -e "${BLUE}Step 4: Creating certificate bundles...${NC}"

# Create full chain certificates (cert + CA)
for service_dir in "${SERVICES_DIR}"/*; do
  if [ -d "$service_dir" ]; then
    service_name=$(basename "$service_dir")
    if [ "$service_name" != "infrastructure" ]; then
      cat "${service_dir}/tls.crt" "${CA_DIR}/ca.crt" > "${service_dir}/fullchain.crt"
      echo -e "${GREEN}✓${NC} ${service_name}/fullchain.crt created"
    fi
  fi
done

# Infrastructure services
for infra_service in postgres minio keycloak vault; do
  cat "${SERVICES_DIR}/infrastructure/${infra_service}/tls.crt" "${CA_DIR}/ca.crt" > "${SERVICES_DIR}/infrastructure/${infra_service}/fullchain.crt"
  echo -e "${GREEN}✓${NC} infrastructure/${infra_service}/fullchain.crt created"
done

echo ""

###############################################################################
# Step 5: Print certificate information
###############################################################################
echo -e "${BLUE}Step 5: Certificate summary...${NC}\n"

echo "Root CA:"
echo "  Subject: $(openssl x509 -in "${CA_DIR}/ca.crt" -noout -subject | cut -d= -f2-)"
echo "  Valid until: $(openssl x509 -in "${CA_DIR}/ca.crt" -noout -enddate | cut -d= -f2)"
echo ""

echo "Service Certificates:"
for service in sentinel-gear claimspindel brazz-nossel buzzle-vane; do
  echo "  ${service}:"
  echo "    Subject: $(openssl x509 -in "${SERVICES_DIR}/${service}/tls.crt" -noout -subject | cut -d= -f2-)"
  echo "    Valid until: $(openssl x509 -in "${SERVICES_DIR}/${service}/tls.crt" -noout -enddate | cut -d= -f2)"
  echo "    SAN: $(openssl x509 -in "${SERVICES_DIR}/${service}/tls.crt" -noout -ext subjectAltName | grep -oP 'DNS:\K[^,]+'  | head -1)"
done

echo ""

###############################################################################
# Step 6: Create shared CA truststore
###############################################################################
echo -e "${BLUE}Step 6: Creating shared CA truststore...${NC}"

keytool -import -trustcacerts -noprompt \
  -alias ironbucket-ca \
  -file "${CA_DIR}/ca.crt" \
  -keystore "${CA_DIR}/ca-truststore.p12" \
  -storepass changeit \
  -storetype PKCS12 2>/dev/null || true

echo -e "${GREEN}✓${NC} Shared truststore created at ${CA_DIR}/ca-truststore.p12"

echo ""

###############################################################################
# Step 7: Set proper permissions
###############################################################################
echo -e "${BLUE}Step 7: Setting file permissions...${NC}"

# Private keys should be readable only by owner
find "${CERTS_DIR}" -name "*.key" -exec chmod 600 {} \;
find "${CERTS_DIR}" -name "*.p12" -exec chmod 600 {} \;

# Infrastructure services run as non-root users in containers and must read mounted TLS keys.
for key_file in \
  "${SERVICES_DIR}/infrastructure/minio/tls.key" \
  "${SERVICES_DIR}/infrastructure/keycloak/tls.key" \
  "${SERVICES_DIR}/infrastructure/vault/tls.key"; do
  if [ -f "${key_file}" ]; then
    chmod 644 "${key_file}"
  fi
done

# Certificates can be world-readable
find "${CERTS_DIR}" -name "*.crt" -exec chmod 644 {} \;

echo -e "${GREEN}✓ File permissions set${NC}\n"

###############################################################################
# Summary
###############################################################################
echo -e "${GREEN}✓ Certificate generation complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Configure Spring Boot services to use mTLS"
echo "     - Update application.yml with keystore/truststore paths"
echo "     - Enable SSL with require-client-auth"
echo ""
echo "  2. Test mTLS connectivity"
echo "     curl --cacert certs/ca/ca.crt \\"
echo "          --cert certs/client/client.crt \\"
echo "          --key certs/client/client.key \\"
echo "          https://localhost:8080/actuator/health"
echo ""
echo "  3. Run mTLS tests"
echo "     mvn test -Dtest=*mTLS*"
echo ""
echo "Certificate locations:"
echo "  - Root CA: ${CA_DIR}/ca.crt"
echo "  - Service certs: ${SERVICES_DIR}/<service-name>/"
echo "  - Client cert: ${CLIENT_DIR}/"
