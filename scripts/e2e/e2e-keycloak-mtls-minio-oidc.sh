#!/usr/bin/env bash
set -euo pipefail

E2E_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$E2E_SCRIPT_DIR/../.." && pwd)"
# Force host-reachable endpoints by default; can be overridden by caller.
IS_CONTAINER="${IS_CONTAINER:-false}"
source "$E2E_SCRIPT_DIR/../.env.defaults"
source "$E2E_SCRIPT_DIR/../lib/common.sh"

register_error_trap

OIDC_CLIENT_ID="${OIDC_CLIENT_ID:-dev-client}"
OIDC_CLIENT_SECRET="${OIDC_CLIENT_SECRET:-dev-secret}"
OIDC_REALM="${KEYCLOAK_REALM:-dev}"
REDIRECT_URI="${OIDC_REDIRECT_URI:-https://steel-hammer-minio:9001/oauth_callback}"

DEFAULT_CERTS_ROOT="$REPO_ROOT/certs"
if [[ -d "/certs/client" ]]; then
	DEFAULT_CERTS_ROOT="/certs"
fi
CERTS_ROOT="${CERTS_ROOT:-$DEFAULT_CERTS_ROOT}"
BOB_CERT="${BOB_CERT:-$CERTS_ROOT/client/bob.crt}"
BOB_KEY="${BOB_KEY:-$CERTS_ROOT/client/bob.key}"
CHARLY_CERT="${CHARLY_CERT:-$CERTS_ROOT/client/charly.crt}"
CHARLY_KEY="${CHARLY_KEY:-$CERTS_ROOT/client/charly.key}"

WORK_DIR="${TEMP_DIR}/mtls-oidc-e2e"
mkdir -p "$WORK_DIR"

require_file() {
	local path="$1"
	if [[ ! -f "$path" ]]; then
		error_exit 1 "Required file missing: $path"
	fi
}

resolve_service_url_for_host_shell() {
	local current_url="$1"
	local docker_host="$2"
	local localhost_port="$3"

	if [[ "$current_url" == *"${docker_host}"* ]] && ! getent hosts "$docker_host" >/dev/null 2>&1; then
		printf '%s' "https://localhost:${localhost_port}"
		return
	fi

	printf '%s' "$current_url"
}

extract_query_param() {
	local url="$1"
	local key="$2"
	local value
	value="$(printf '%s' "$url" | sed -nE "s/.*[?&]${key}=([^&]+).*/\\1/p")"
	printf '%s' "$value"
}

get_authorization_code_with_cert() {
	local user="$1"
	local cert_file="$2"
	local key_file="$3"

	local auth_url
	auth_url="${KEYCLOAK_URL}/realms/${OIDC_REALM}/protocol/openid-connect/auth?client_id=${OIDC_CLIENT_ID}&redirect_uri=${REDIRECT_URI}&response_type=code&scope=openid"

	local headers_file="$WORK_DIR/${user}-headers.txt"
	local body_file="$WORK_DIR/${user}-body.txt"
	local location=""

	curl -ksS \
		--cert "$cert_file" \
		--key "$key_file" \
		-D "$headers_file" \
		-o "$body_file" \
		"$auth_url"

	location="$(awk 'BEGIN{IGNORECASE=1} /^location:/ {print $2}' "$headers_file" | tr -d '\r' | tail -n1)"
	if [[ -z "$location" ]]; then
		error_exit 1 "No redirect location found for ${user}. Keycloak did not issue an auth redirect."
	fi

	if [[ "$location" != *"code="* ]]; then
		error_exit 1 "Redirect for ${user} does not contain authorization code: $location"
	fi

	local code
	code="$(extract_query_param "$location" "code")"
	if [[ -z "$code" ]]; then
		error_exit 1 "Failed to extract authorization code for ${user}."
	fi

	printf '%s' "$code"
}

exchange_code_for_tokens() {
	local user="$1"
	local code="$2"
	local token_file="$WORK_DIR/${user}-token.json"

	curl -ksS -o "$token_file" \
		-X POST "${KEYCLOAK_URL}/realms/${OIDC_REALM}/protocol/openid-connect/token" \
		-d "client_id=${OIDC_CLIENT_ID}" \
		-d "client_secret=${OIDC_CLIENT_SECRET}" \
		-d "grant_type=authorization_code" \
		-d "code=${code}" \
		-d "redirect_uri=${REDIRECT_URI}"

	if ! jq -e '.access_token and .id_token' "$token_file" >/dev/null 2>&1; then
		cat "$token_file"
		error_exit 1 "Token exchange failed for ${user}."
	fi

	jq -r '.id_token' "$token_file"
}

validate_id_token_user() {
	local user="$1"
	local id_token="$2"
	local payload
	payload="$(printf '%s' "$id_token" | cut -d'.' -f2 | tr '_-' '/+' | awk '{ l=length($0)%4; if(l==2)print $0"=="; else if(l==3)print $0"="; else if(l==1)print $0"==="; else print $0 }' | base64 -d 2>/dev/null || true)"

	local preferred_username
	preferred_username="$(printf '%s' "$payload" | jq -r '.preferred_username // empty' 2>/dev/null || true)"
	if [[ "$preferred_username" != "$user" ]]; then
		error_exit 1 "ID token user mismatch. Expected ${user}, got ${preferred_username:-<empty>}"
	fi
}

assume_role_web_identity() {
	local user="$1"
	local id_token="$2"
	local encoded
	encoded="$(printf '%s' "$id_token" | jq -sRr @uri)"

	local sts_file="$WORK_DIR/${user}-sts.xml"
	curl -ksS -o "$sts_file" \
		"${MINIO_URL}?Action=AssumeRoleWithWebIdentity&Version=2011-06-15&DurationSeconds=900&WebIdentityToken=${encoded}"

	if ! grep -q "AssumeRoleWithWebIdentityResponse" "$sts_file"; then
		cat "$sts_file"
		error_exit 1 "MinIO STS response missing for ${user}."
	fi

	if ! grep -q "<AccessKeyId>" "$sts_file"; then
		cat "$sts_file"
		error_exit 1 "MinIO STS credentials missing for ${user}."
	fi

	log_success "MinIO OIDC STS credentials issued for ${user}"
}

log_info "Validating Keycloak mTLS + MinIO OIDC for bob and charly"

KEYCLOAK_URL="$(resolve_service_url_for_host_shell "$KEYCLOAK_URL" "steel-hammer-keycloak" "7081")"
MINIO_URL="$(resolve_service_url_for_host_shell "$MINIO_URL" "steel-hammer-minio" "9000")"

require_file "$BOB_CERT"
require_file "$BOB_KEY"
require_file "$CHARLY_CERT"
require_file "$CHARLY_KEY"

for user in bob charly; do
	cert_var="${user^^}_CERT"
	key_var="${user^^}_KEY"
	cert_file="${!cert_var}"
	key_file="${!key_var}"

	log_info "Authenticating ${user} using client certificate"
	code="$(get_authorization_code_with_cert "$user" "$cert_file" "$key_file")"
	id_token="$(exchange_code_for_tokens "$user" "$code")"
	validate_id_token_user "$user" "$id_token"
	assume_role_web_identity "$user" "$id_token"
done

log_success "Keycloak mTLS user authentication and MinIO OIDC web identity E2E passed"
