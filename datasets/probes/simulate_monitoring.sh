#!/usr/bin/env bash
# Randomly chain existing probe scripts until Ctrl+C.
# Example:
#   ./datasets/probes/simulate_monitoring.sh
#   ./datasets/probes/simulate_monitoring.sh --rounds 10 --interval 5
#   ./datasets/probes/simulate_monitoring.sh --once-each
#   ./datasets/probes/simulate_monitoring.sh --only clean,medium,high
set -euo pipefail
cd "$(dirname "$0")"
exec python3 simulate_monitoring.py "$@"
