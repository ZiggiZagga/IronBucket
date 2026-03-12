#!/usr/bin/env bash
set -euo pipefail

migration="versioning-enabled"
assertion="delete-marker preserved with historical versions"

echo "migration=$migration assertion=$assertion"
