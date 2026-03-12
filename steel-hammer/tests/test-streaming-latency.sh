#!/usr/bin/env bash
set -euo pipefail

path_a="proxy"
path_b="presigned"
latency_budget_ms=100

echo "compare=$path_a:$path_b latency=${latency_budget_ms}ms"
