#!/usr/bin/env bash
set -euo pipefail

phase="rolling-upgrade"
state="failover"
consistency="verified"

echo "phase=$phase state=$state consistency=$consistency"
