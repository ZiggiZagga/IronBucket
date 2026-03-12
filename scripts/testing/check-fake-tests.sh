#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

PATTERN='expect\(true\)\.toBe\(true\)|assertTrue\(true\)|assertEquals\(true,\s*true\)|\(placeholder\)|dummy test|TODO.*test'
INCLUDE_GLOB='**/*.{test,spec}.{js,ts,java}'

echo "Scanning for fake/placeholder test patterns..."

RESULTS=$(grep -RInE "$PATTERN" "$ROOT_DIR" \
  --include='*.test.js' \
  --include='*.test.ts' \
  --include='*.spec.js' \
  --include='*.spec.ts' \
  --include='*Test.java' \
  --exclude-dir=node_modules \
  --exclude-dir=.git \
  --exclude-dir=target \
  --exclude-dir=dist \
  --exclude-dir=build \
  || true)

if [[ -n "$RESULTS" ]]; then
  echo ""
  echo "Found potential fake tests:"
  echo "$RESULTS"
  echo ""
  echo "Failing check. Please replace placeholders with meaningful assertions or mark as skipped with a clear reason."
  exit 1
fi

echo "No fake/placeholder test patterns found."
