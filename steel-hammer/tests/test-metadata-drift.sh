#!/usr/bin/env bash
set -euo pipefail

source_inventory="metadata-index"
target_inventory="backend-metadata"
fields=(checksum acl metadata versions tags)

echo "comparing ${source_inventory} with ${target_inventory}"
printf '%s\n' "${fields[@]}"
