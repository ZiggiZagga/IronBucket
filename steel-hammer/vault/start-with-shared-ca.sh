#!/usr/bin/env sh
set -eu

export VAULT_ADDR="${VAULT_ADDR:-https://127.0.0.1:8200}"
export VAULT_CACERT="${VAULT_CACERT:-/certs/ca/ca.crt}"

INIT_MARKER="/vault/file/.initialized"
UNSEAL_FILE="/vault/file/unseal.key"
ROOT_FILE="/vault/file/root.token"

vault server -config=/vault/local/dev-server.hcl &
VAULT_PID=$!

cleanup() {
  kill "$VAULT_PID" 2>/dev/null || true
}
trap cleanup INT TERM

ready=0
for _ in $(seq 1 90); do
  set +e
  vault status >/dev/null 2>&1
  rc=$?
  set -e
  if [ "$rc" -eq 0 ] || [ "$rc" -eq 1 ] || [ "$rc" -eq 2 ]; then
    ready=1
    break
  fi
  sleep 1
done

if [ "$ready" -ne 1 ]; then
  echo "ERROR: Vault did not become ready in time" >&2
  wait "$VAULT_PID"
  exit 1
fi

if [ ! -f "$INIT_MARKER" ]; then
  INIT_OUTPUT="$(vault operator init -key-shares=1 -key-threshold=1)"
  echo "$INIT_OUTPUT" > /vault/file/init-output.txt
  echo "$INIT_OUTPUT" | awk -F': ' '/Unseal Key 1:/{print $2}' > "$UNSEAL_FILE"
  echo "$INIT_OUTPUT" | awk -F': ' '/Initial Root Token:/{print $2}' > "$ROOT_FILE"
  touch "$INIT_MARKER"
fi

if [ -f "$UNSEAL_FILE" ]; then
  UNSEAL_KEY="$(cat "$UNSEAL_FILE")"
  if [ -n "$UNSEAL_KEY" ]; then
    vault operator unseal "$UNSEAL_KEY" >/dev/null 2>&1 || true
  fi
fi

if [ -f "$ROOT_FILE" ]; then
  export VAULT_TOKEN="$(cat "$ROOT_FILE")"
  vault token create -id=dev-root-token -policy=root -orphan >/dev/null 2>&1 || true
  if ! vault secrets list -format=json | grep -q '"secret/"'; then
    vault secrets enable -path=secret -version=2 kv >/dev/null 2>&1 || true
  fi
fi

wait "$VAULT_PID"
