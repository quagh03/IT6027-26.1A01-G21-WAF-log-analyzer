#!/usr/bin/env bash
# 50 benign Juice Shop requests through local Nginx. No attack payloads.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1}"
HOST="${HOST:-juice.lab.local}"

paths=(
  /
  /rest/products/search?q=apple
  /rest/products/search?q=juice
  /rest/products/search?q=orange
  /rest/products/search?q=banana
  /rest/products/search?q=shirt
  /rest/products/search?q=bike
  /rest/products/search?q=carrot
  /rest/products/search?q=green
  /rest/products/search?q=water
  /api/Challenges
  /rest/user/whoami
  /rest/admin/application-configuration
  /rest/languages
  /metrics
  /ftp
  /encryptionkeys
  /rest/captcha
  /rest/continue-code
  /snippets
  /rest/products/1
  /rest/products/2
  /rest/products/3
  /rest/products/4
  /rest/products/5
  /rest/products/6
  /rest/products/7
  /rest/products/8
  /rest/products/9
  /rest/products/10
  /assets/public/favicon_js.ico
  /main.js
  /polyfills.js
  /runtime.js
  /vendor.js
  /styles.css
  /3rdpartylicenses.txt
  /api/Quantitys
  /rest/deluxe-membership
  /rest/memories
  /api/Users
  /rest/products/search?q=laptop
  /rest/products/search?q=mug
  /rest/products/search?q=hat
  /rest/products/search?q=book
  /rest/products/search?q=pen
  /video
  /promotion
  /rest/wallet/balance
  /rest/saveLoginIp
)

echo "=== browse_clean ${#paths[@]} requests Host=${HOST} ==="
echo "Ethics: local Juice Shop behind Nginx only."
for path in "${paths[@]}"; do
  echo "-> GET ${path}"
  curl -sS -o /dev/null -w "   HTTP %{http_code}\n" \
    -H "Host: ${HOST}" \
    "${BASE_URL}${path}" || echo "   curl failed"
  sleep 0.15
done
echo "=== done ${#paths[@]} ==="
