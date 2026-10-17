#!/usr/bin/env bash
# Demo-only: send MEDIUM + HIGH probes (not the full CRITICAL set).
set -euo pipefail
cd "$(dirname "$0")"
exec python3 run.py --severity MEDIUM,HIGH
