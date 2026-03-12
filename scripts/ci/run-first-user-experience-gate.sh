#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export KEEP_STACK="${KEEP_STACK:-false}"

echo "[first-user-gate] running Phase 1-4 first-user experience proof"
chmod +x "$ROOT_DIR/scripts/e2e/prove-phase1-4-complete.sh"
bash "$ROOT_DIR/scripts/e2e/prove-phase1-4-complete.sh"

echo "[first-user-gate] passed"
