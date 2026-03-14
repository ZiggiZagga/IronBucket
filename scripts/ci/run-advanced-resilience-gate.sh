#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${ROOT_DIR}/test-results/resilience-gates"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "${OUT_DIR}"

DISK_PRESSURE_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-disk-pressure.sh")"
CONTROL_PLANE_HA_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-control-plane-ha.sh")"
STREAMING_LATENCY_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-streaming-latency.sh")"
PARTITION_RECONCILIATION_OUT="$(bash "${ROOT_DIR}/steel-hammer/tests/test-reconciliation-after-partition.sh")"

if ! grep -q "injector=fallocate" <<<"${DISK_PRESSURE_OUT}"; then
  echo "ERROR: disk pressure fixture missing injector marker" >&2
  exit 1
fi
if ! grep -q "outcome=backpressure" <<<"${DISK_PRESSURE_OUT}"; then
  echo "ERROR: disk pressure fixture missing backpressure marker" >&2
  exit 1
fi

if ! grep -q "phase=rolling-upgrade" <<<"${CONTROL_PLANE_HA_OUT}"; then
  echo "ERROR: control plane HA fixture missing rolling-upgrade marker" >&2
  exit 1
fi
if ! grep -q "state=failover" <<<"${CONTROL_PLANE_HA_OUT}"; then
  echo "ERROR: control plane HA fixture missing failover marker" >&2
  exit 1
fi
if ! grep -q "consistency=verified" <<<"${CONTROL_PLANE_HA_OUT}"; then
  echo "ERROR: control plane HA fixture missing consistency marker" >&2
  exit 1
fi

if ! grep -q "compare=" <<<"${STREAMING_LATENCY_OUT}"; then
  echo "ERROR: streaming latency fixture missing compare marker" >&2
  exit 1
fi
if ! grep -q "proxy" <<<"${STREAMING_LATENCY_OUT}"; then
  echo "ERROR: streaming latency fixture missing proxy path marker" >&2
  exit 1
fi
if ! grep -q "presigned" <<<"${STREAMING_LATENCY_OUT}"; then
  echo "ERROR: streaming latency fixture missing presigned marker" >&2
  exit 1
fi
if ! grep -q "latency=" <<<"${STREAMING_LATENCY_OUT}"; then
  echo "ERROR: streaming latency fixture missing latency marker" >&2
  exit 1
fi

if ! grep -q "operation=reconcile" <<<"${PARTITION_RECONCILIATION_OUT}"; then
  echo "ERROR: reconciliation fixture missing reconcile marker" >&2
  exit 1
fi
if ! grep -q "expected=inventory diff converges to zero" <<<"${PARTITION_RECONCILIATION_OUT}"; then
  echo "ERROR: reconciliation fixture missing convergence marker" >&2
  exit 1
fi

JSON_PATH="${OUT_DIR}/advanced-resilience-gate-summary-${TIMESTAMP}.json"
MD_PATH="${OUT_DIR}/advanced-resilience-gate-summary-${TIMESTAMP}.md"
LATEST_JSON="${OUT_DIR}/LATEST-advanced-resilience-gate-summary.json"
LATEST_MD="${OUT_DIR}/LATEST-advanced-resilience-gate-summary.md"

python3 - <<PY > "${JSON_PATH}"
import json
from datetime import datetime, timezone

payload = {
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "diskPressureFixture": """${DISK_PRESSURE_OUT}""".strip(),
    "controlPlaneHaFixture": """${CONTROL_PLANE_HA_OUT}""".strip(),
    "streamingLatencyFixture": """${STREAMING_LATENCY_OUT}""".strip(),
    "partitionReconciliationFixture": """${PARTITION_RECONCILIATION_OUT}""".strip(),
    "status": "passed"
}
print(json.dumps(payload, indent=2))
PY

{
  echo "# Advanced Resilience Gate Summary"
  echo
  echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| Disk pressure and backpressure fixture | passed |"
  echo "| Control plane HA failover fixture | passed |"
  echo "| Streaming latency fixture | passed |"
  echo "| Partition reconciliation fixture | passed |"
} > "${MD_PATH}"

cp "${JSON_PATH}" "${LATEST_JSON}"
cp "${MD_PATH}" "${LATEST_MD}"

echo "[advanced-resilience-gate] passed"
echo "[advanced-resilience-gate] summary: ${JSON_PATH}"
echo "[advanced-resilience-gate] summary: ${MD_PATH}"
