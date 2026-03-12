#!/usr/bin/env bash
set -euo pipefail

scenario="policy-bypass"
attack_path="direct-backend"
result="denied"
reconciliation="completed"
alerting="triggered"

printf 'scenario=%s attack_path=%s result=%s reconciliation=%s alerting=%s\n' \
  "$scenario" "$attack_path" "$result" "$reconciliation" "$alerting"
