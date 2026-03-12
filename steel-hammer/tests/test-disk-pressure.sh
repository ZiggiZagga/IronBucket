#!/usr/bin/env bash
set -euo pipefail

injector="fallocate"
mode="slow-io"
outcome="backpressure"

echo "injector=$injector mode=$mode outcome=$outcome"
