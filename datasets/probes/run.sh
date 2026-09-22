#!/usr/bin/env bash
# Week 1 smoke runner — curls via Nginx (Host header).
# Full labelled attack runner: Week 4 (see scenarios.yaml).
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1}"

hit() {
  local id="$1" host="$2" method="$3" path="$4" query="${5:-}"
  local url="${BASE_URL}${path}"
  [[ -n "$query" ]] && url="${url}?${query}"
  echo "-> [${id}] ${method} Host=${host} ${url}"
  curl -sS -o /dev/null -w "   HTTP %{http_code}\n" \
    -H "Host: ${host}" \
    -X "${method}" \
    "${url}" || echo "   curl failed (is nginx up?)"
  sleep 0.3
}

echo "=== Week 1 probe smoke against ${BASE_URL} ==="
hit smoke-home      juice.lab.local GET /
hit smoke-shop-home shop.lab.local  GET /
hit smoke-search    juice.lab.local GET /rest/products/search "q=apple"
echo "=== done. Check GET http://127.0.0.1:8080/api/events ==="
