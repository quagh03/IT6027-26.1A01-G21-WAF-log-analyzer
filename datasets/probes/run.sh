#!/usr/bin/env bash
# Send datasets/probes/scenarios.yaml through the local Nginx lab.
set -euo pipefail
cd "$(dirname "$0")"
exec python3 run.py "$@"
