#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ALERT_FILE="$ROOT_DIR/steel-hammer/alerts/alert-rules.yml"
SECURITY_DASH="$ROOT_DIR/steel-hammer/grafana/dashboards/security-dashboard.json"
PERF_DASH="$ROOT_DIR/steel-hammer/grafana/dashboards/performance-dashboard.json"
AUDIT_DASH="$ROOT_DIR/steel-hammer/grafana/dashboards/audit-dashboard.json"

require_file() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "[obs-assets] ERROR: missing file: $path" >&2
    exit 1
  fi
}

require_match() {
  local pattern="$1"
  local file="$2"
  if ! grep -Eq "$pattern" "$file"; then
    echo "[obs-assets] ERROR: pattern '$pattern' not found in $file" >&2
    exit 1
  fi
}

require_file "$ALERT_FILE"
require_file "$SECURITY_DASH"
require_file "$PERF_DASH"
require_file "$AUDIT_DASH"

python3 - <<'PY'
import json
import pathlib
import sys

files = [
    pathlib.Path("/workspaces/IronBucket/steel-hammer/grafana/dashboards/security-dashboard.json"),
    pathlib.Path("/workspaces/IronBucket/steel-hammer/grafana/dashboards/performance-dashboard.json"),
    pathlib.Path("/workspaces/IronBucket/steel-hammer/grafana/dashboards/audit-dashboard.json"),
]

for file_path in files:
    try:
        payload = json.loads(file_path.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"[obs-assets] ERROR: invalid JSON in {file_path}: {exc}", file=sys.stderr)
        sys.exit(1)

    if not isinstance(payload.get("panels"), list) or len(payload["panels"]) == 0:
        print(f"[obs-assets] ERROR: dashboard has no panels: {file_path}", file=sys.stderr)
        sys.exit(1)

print("[obs-assets] dashboard JSON validation passed")
PY

# Alerts expected for infra observability extension.
require_match "ObservabilityInfraScrapeDown" "$ALERT_FILE"
require_match "KeycloakAuthErrorSpike" "$ALERT_FILE"
require_match "PostgresConnectionSaturation" "$ALERT_FILE"
require_match "MinioStorageErrorRate" "$ALERT_FILE"

# Dashboard coverage checks for Keycloak/Postgres/MinIO.
require_match "keycloak|Keycloak" "$SECURITY_DASH"
require_match "postgres|Postgres" "$PERF_DASH"
require_match "minio|MinIO" "$PERF_DASH"

echo "[obs-assets] alert and dashboard coverage validation passed"
