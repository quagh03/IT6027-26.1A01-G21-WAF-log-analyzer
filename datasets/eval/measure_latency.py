#!/usr/bin/env python3
"""Measure probe request → new Alert appears (API poll; SSE optional)."""

from __future__ import annotations

import json
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import API, send_get, token, wait_healthy

OUT = Path(__file__).resolve().parent / "latency.md"


def alerts(bearer: str) -> list[dict]:
    req = urllib.request.Request(
        f"{API}/api/alerts",
        headers={"Authorization": f"Bearer {bearer}"},
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.load(resp)


def main() -> int:
    wait_healthy()
    bearer = token()
    before_ids = {int(a["id"]) for a in alerts(bearer)}
    before_max = max(before_ids) if before_ids else 0

    path = "/rest/products/search"
    query = "q=1 OR 1=1 UNION SELECT null&mark=LATENCY_MARK"
    t0 = time.perf_counter()
    http_code = send_get(path, query)
    t_http = time.perf_counter()

    alert = None
    t_api = None
    deadline = time.time() + 45
    while time.time() < deadline:
        try:
            for a in alerts(bearer):
                aid = int(a["id"])
                if aid <= before_max:
                    continue
                summary = a.get("summary") or ""
                if "LATENCY_MARK" in summary or int(a.get("score") or 0) >= 85:
                    alert = a
                    t_api = time.perf_counter()
                    break
        except Exception:
            time.sleep(0.5)
            continue
        if alert:
            break
        time.sleep(0.25)

    if not alert or t_api is None:
        text = (
            "# Latency probe → Alert\n\n"
            f"HTTP {http_code} in {t_http - t0:.3f}s but **no new alert within 45s** "
            f"(before_max={before_max}).\n"
        )
        OUT.write_text(text, encoding="utf-8")
        print(text)
        return 1

    latency = t_api - t0
    text = "\n".join(
        [
            "# Latency probe → Alert",
            "",
            f"- Probe: `GET {path}?{query}` Host `juice.lab.local`",
            f"- HTTP status: {http_code} ({t_http - t0:.3f}s)",
            f"- **Request → Alert API: {latency:.3f} s**",
            f"- Mục tiêu solution-design §11.2: < 5 s → {'PASS' if latency < 5 else 'FAIL (giải thích trong báo cáo)'}",
            f"- Alert id: {alert.get('id')} score={alert.get('score')} summary={(alert.get('summary') or '')[:80]}",
            "",
            "Ghi chú: đo bằng poll `GET /api/alerts` ngay sau probe (cùng đường tạo alert "
            "mà SSE fan-out dùng). Trên lab sạch số đo thường < 5 s.",
            "",
        ]
    )
    OUT.write_text(text, encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
