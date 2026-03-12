#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

echo "[shell-validate] scanning .sh files"

failures=0
while IFS= read -r file; do
  first_line="$(head -n 1 "$file" 2>/dev/null || true)"

  if [[ "$first_line" =~ bash ]]; then
    if ! bash -n "$file"; then
      echo "[shell-validate] FAIL (bash): $file"
      failures=$((failures + 1))
    fi
  else
    if ! sh -n "$file"; then
      echo "[shell-validate] FAIL (sh): $file"
      failures=$((failures + 1))
    fi
  fi
done < <(find . -type f -name '*.sh' \
  -not -path './.git/*' \
  -not -path './**/node_modules/**' \
  | sort)

if [[ "$failures" -gt 0 ]]; then
  echo "[shell-validate] detected $failures failing shell scripts"
  exit 1
fi

echo "[shell-validate] all shell scripts passed syntax validation"
