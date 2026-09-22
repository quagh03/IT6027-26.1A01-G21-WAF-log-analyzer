#!/usr/bin/env bash
# Week 1 stub — a few clean Juice Shop paths.
# Week 4 expands to ~50 browse requests.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1}"
HOST="${HOST:-juice.lab.local}"

paths=(
  /
  /#/
  /rest/products/search?q=juice
  /api/Challenges/
  /assets/public/favicon_js.ico
)

echo "=== browse_clean (Week 1 stub) Host=${HOST} ==="
for p in "${paths[@]}"; do
  echo "-> GET ${p}"
  curl -sS -o /dev/null -w "   HTTP %{http_code}\n" \
    -H "Host: ${HOST}" \
    "${BASE_URL}${p}" || true
  sleep 0.2
done
echo "=== done ==="
