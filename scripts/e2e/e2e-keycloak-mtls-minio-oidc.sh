#!/usr/bin/env bash
set -euo pipefail

E2E_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$E2E_SCRIPT_DIR/../.." && pwd)"
# Force host-reachable endpoints by default; can be overridden by caller.
IS_CONTAINER="${IS_CONTAINER:-false}"
source "$E2E_SCRIPT_DIR/../.env.defaults"
source "$E2E_SCRIPT_DIR/../lib/common.sh"

register_error_trap
if [[ "${IS_CONTAINER:-false}" != "true" ]]; then
	ensure_cert_artifacts
else
	log_info "Running in container; certificate generation preflight is handled on host-side proof runners"
fi

OIDC_CLIENT_ID="${OIDC_CLIENT_ID:-minio-console}"
OIDC_CLIENT_SECRET="${OIDC_CLIENT_SECRET:-minio-console-secret}"
OIDC_REALM="${KEYCLOAK_REALM:-dev}"
REDIRECT_URI="${OIDC_REDIRECT_URI:-https://steel-hammer-minio:9001/oauth_callback}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

DEFAULT_REALM_IMPORT_FILE="$REPO_ROOT/steel-hammer/keycloak/dev-realm.json"
if [[ -f "/workspaces/IronBucket/steel-hammer/keycloak/dev-realm.json" ]]; then
	DEFAULT_REALM_IMPORT_FILE="/workspaces/IronBucket/steel-hammer/keycloak/dev-realm.json"
fi
REALM_IMPORT_FILE="${REALM_IMPORT_FILE:-$DEFAULT_REALM_IMPORT_FILE}"

DEFAULT_CERTS_ROOT="$REPO_ROOT/certs"
if [[ -d "/vault-pki-certs/client" ]]; then
        DEFAULT_CERTS_ROOT="/vault-pki-certs"
elif [[ -d "/certs/client" ]]; then
fi
CERTS_ROOT="${CERTS_ROOT:-$DEFAULT_CERTS_ROOT}"
BOB_CERT="${BOB_CERT:-$CERTS_ROOT/client/bob.crt}"
BOB_KEY="${BOB_KEY:-$CERTS_ROOT/client/bob.key}"
CHARLY_CERT="${CHARLY_CERT:-$CERTS_ROOT/client/charly.crt}"
CHARLY_KEY="${CHARLY_KEY:-$CERTS_ROOT/client/charly.key}"
BOB_PASSWORD="${BOB_PASSWORD:-bobP@ss}"
CHARLY_PASSWORD="${CHARLY_PASSWORD:-charlyP@ss}"

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

keycloak_admin_token() {
	local token
	token="$(curl -ksS -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
		-d "client_id=admin-cli" \
		-d "username=${KEYCLOAK_ADMIN_USER}" \
		-d "password=${KEYCLOAK_ADMIN_PASSWORD}" \
		-d "grant_type=password" | jq -r '.access_token // empty')"

	if [[ -z "$token" ]]; then
		error_exit 1 "Failed to acquire Keycloak admin token."
	fi

	printf '%s' "$token"
}

keycloak_realm_browser_flow() {
	local token="$1"
	curl -ksS -H "Authorization: Bearer ${token}" \
		"${KEYCLOAK_URL}/admin/realms/${OIDC_REALM}" | jq -r '.browserFlow // empty'
}

keycloak_minio_policy_mapper_present() {
	local token="$1"
	local client_uuid
	client_uuid="$(curl -ksS -H "Authorization: Bearer ${token}" \
		"${KEYCLOAK_URL}/admin/realms/${OIDC_REALM}/clients?clientId=minio-console" | jq -r '.[0].id // empty')"

	if [[ -z "$client_uuid" ]]; then
		printf 'false'
		return
	fi

	local count
	count="$(curl -ksS -H "Authorization: Bearer ${token}" \
		"${KEYCLOAK_URL}/admin/realms/${OIDC_REALM}/clients/${client_uuid}/protocol-mappers/models" | \
		jq '[.[] | select(.name=="policy-from-user-attribute" and .protocolMapper=="oidc-usermodel-attribute-mapper")] | length')"

	if [[ "$count" -gt 0 ]]; then
		printf 'true'
	else
		printf 'false'
	fi
}

reconcile_keycloak_realm_for_mtls() {
	local token="$1"

	if [[ ! -f "$REALM_IMPORT_FILE" ]]; then
		error_exit 1 "Realm import file not found: $REALM_IMPORT_FILE"
	fi

	log_info "Refreshing Keycloak realm '${OIDC_REALM}' from ${REALM_IMPORT_FILE} to enforce x509 flow"

	curl -ksS -X DELETE -H "Authorization: Bearer ${token}" \
		"${KEYCLOAK_URL}/admin/realms/${OIDC_REALM}" >/dev/null || true

	curl -ksS -X POST -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
		--data-binary @"$REALM_IMPORT_FILE" \
		"${KEYCLOAK_URL}/admin/realms" >/dev/null

	sleep 2
}

ensure_keycloak_mtls_realm_ready() {
	local token browser_flow mapper_ready
	token="$(keycloak_admin_token)"
	browser_flow="$(keycloak_realm_browser_flow "$token")"
	mapper_ready="$(keycloak_minio_policy_mapper_present "$token")"

	if [[ "$browser_flow" != "browser-x509" || "$mapper_ready" != "true" ]]; then
		reconcile_keycloak_realm_for_mtls "$token"
		token="$(keycloak_admin_token)"
		browser_flow="$(keycloak_realm_browser_flow "$token")"
		mapper_ready="$(keycloak_minio_policy_mapper_present "$token")"
	fi

	if [[ "$browser_flow" != "browser-x509" ]]; then
		error_exit 1 "Keycloak realm browserFlow is '${browser_flow:-<empty>}' (expected 'browser-x509')."
	fi

	if [[ "$mapper_ready" != "true" ]]; then
		error_exit 1 "Keycloak minio policy mapper is missing after realm reconciliation."
	fi

	log_info "Keycloak realm '${OIDC_REALM}' browserFlow is '${browser_flow}'"
}

extract_query_param() {
	local url="$1"
	local key="$2"
	local value
	value="$(printf '%s' "$url" | sed -nE "s/.*[?&]${key}=([^&]+).*/\\1/p")"
	printf '%s' "$value"
}

extract_location_header() {
	local headers_file="$1"
	awk 'BEGIN{IGNORECASE=1} /^location:/ {print $2}' "$headers_file" | tr -d '\r' | tail -n1
}

extract_login_action_url() {
	local body_file="$1"
	local action
	action="$(sed -nE 's/.*action="([^"]*login-actions\/authenticate[^"]*)".*/\1/p' "$body_file" | head -n1 | sed 's/&amp;/\&/g')"
	printf '%s' "$action"
}

get_authorization_code_with_cert() {
	local user="$1"
	local cert_file="$2"
	local key_file="$3"
	local user_password="$4"

	local auth_url
	auth_url="${KEYCLOAK_URL}/realms/${OIDC_REALM}/protocol/openid-connect/auth?client_id=${OIDC_CLIENT_ID}&redirect_uri=${REDIRECT_URI}&response_type=code&scope=openid"

	local headers_file="$WORK_DIR/${user}-headers.txt"
	local body_file="$WORK_DIR/${user}-body.txt"
	local cookies_file="$WORK_DIR/${user}-cookies.txt"
	local location=""

	curl -ksS \
		--cert "$cert_file" \
		--key "$key_file" \
		-c "$cookies_file" \
		-D "$headers_file" \
		-o "$body_file" \
		"$auth_url"

	location="$(extract_location_header "$headers_file")"
	if [[ -z "$location" || "$location" != *"code="* ]]; then
		local login_action
		login_action="$(extract_login_action_url "$body_file")"

		if [[ -z "$login_action" ]]; then
			echo "--- ${user} response headers ---"
			sed -n '1,30p' "$headers_file" || true
			echo "--- ${user} form-action hints ---"
			grep -Eo 'action="[^"]*"|id="[^"]*"|name="[^"]*"' "$body_file" | head -n 40 || true
			error_exit 1 "No redirect location found for ${user}. Keycloak did not issue an auth redirect."
		fi

		if [[ "$login_action" != http* ]]; then
			login_action="${KEYCLOAK_URL}${login_action}"
		fi

		curl -ksS \
			--cert "$cert_file" \
			--key "$key_file" \
			-b "$cookies_file" \
			-c "$cookies_file" \
			-D "$headers_file" \
			-o "$body_file" \
			-X POST "$login_action" \
			-d "username=${user}" \
			-d "password=${user_password}" \
			-d "credentialId=" \
			-d "login=Sign+In"

		location="$(extract_location_header "$headers_file")"
	fi

	if [[ -z "$location" ]]; then
		echo "--- ${user} final response headers ---"
		sed -n '1,30p' "$headers_file" || true
		echo "--- ${user} final body hints ---"
		grep -Eo 'action="[^"]*"|id="[^"]*"|name="[^"]*"|error-summary|instruction' "$body_file" | head -n 80 || true
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

	jq -r '.id_token' "$token_file" > "$WORK_DIR/${user}-id-token.jwt"
	jq -r '.access_token' "$token_file" > "$WORK_DIR/${user}-access-token.jwt"

	# Persist decoded token claims for diagnostics.
	for token_type in id access; do
		local token_value payload_file
		token_value="$(cat "$WORK_DIR/${user}-${token_type}-token.jwt")"
		payload_file="$WORK_DIR/${user}-${token_type}-token-claims.json"
		printf '%s' "$token_value" | cut -d'.' -f2 | tr '_-' '/+' | \
			awk '{ l=length($0)%4; if(l==2)print $0"=="; else if(l==3)print $0"="; else if(l==1)print $0"==="; else print $0 }' | \
			base64 -d 2>/dev/null > "$payload_file" || true
	done

	printf '%s' "$token_file"
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
	local web_identity_token="$2"
	local role_arn_arg=()
	local role_arn_query=""

	if [[ -n "${MINIO_OIDC_ROLE_ARN:-}" ]]; then
		role_arn_arg=(--data-urlencode "RoleArn=${MINIO_OIDC_ROLE_ARN}")
	fi

	local sts_file="$WORK_DIR/${user}-sts.xml"
	curl -ksS -o "$sts_file" \
		-X POST "${MINIO_URL}" \
		-d "Action=AssumeRoleWithWebIdentity" \
		-d "Version=2011-06-15" \
		-d "DurationSeconds=900" \
		"${role_arn_arg[@]}" \
		--data-urlencode "WebIdentityToken=${web_identity_token}"

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

ensure_keycloak_mtls_realm_ready

require_file "$BOB_CERT"
require_file "$BOB_KEY"
require_file "$CHARLY_CERT"
require_file "$CHARLY_KEY"

for user in bob charly; do
	cert_var="${user^^}_CERT"
	key_var="${user^^}_KEY"
	password_var="${user^^}_PASSWORD"
	cert_file="${!cert_var}"
	key_file="${!key_var}"
	user_password="${!password_var}"

	log_info "Authenticating ${user} using client certificate"
	code="$(get_authorization_code_with_cert "$user" "$cert_file" "$key_file" "$user_password")"
	token_file="$(exchange_code_for_tokens "$user" "$code")"
	id_token="$(cat "$WORK_DIR/${user}-id-token.jwt")"
	access_token="$(cat "$WORK_DIR/${user}-access-token.jwt")"
	validate_id_token_user "$user" "$id_token"
	assume_role_web_identity "$user" "$id_token"
done

log_success "Keycloak mTLS user authentication and MinIO OIDC web identity E2E passed"
