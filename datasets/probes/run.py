#!/usr/bin/env python3
"""Send labelled scenarios through the local Nginx lab. Does not scan remote hosts."""

from __future__ import annotations

import argparse
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SCENARIOS = ROOT / "scenarios.yaml"

# Keep () so TIME rules like sleep(5) still match raw access-log query text.
# Keep already-encoded %xx sequences intact.
QUERY_SAFE = "=&%+/:?()"


def parse_scenarios(text: str) -> tuple[str, str, list[dict[str, str]]]:
    base_url = "http://127.0.0.1"
    default_host = "juice.lab.local"
    scenarios: list[dict[str, str]] = []
    current: dict[str, str] | None = None
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        if line.startswith("base_url:"):
            base_url = line.split(":", 1)[1].strip()
            continue
        if line.startswith("default_host:"):
            default_host = line.split(":", 1)[1].strip()
            continue
        stripped = line.strip()
        if stripped.startswith("- id:"):
            if current:
                scenarios.append(current)
            current = {"id": unquote(stripped.split(":", 1)[1].strip())}
            continue
        if current is None or ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        current[key.strip()] = unquote(value.strip())
    if current:
        scenarios.append(current)
    return base_url, default_host, scenarios


def unquote(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
        return value[1:-1]
    return value


def send(base_url: str, host: str, method: str, path: str, query: str) -> int:
    encoded = urllib.parse.quote(query, safe=QUERY_SAFE) if query else ""
    url = base_url.rstrip("/") + path
    if encoded:
        url = f"{url}?{encoded}"
    request = urllib.request.Request(url, method=method, headers={"Host": host})
    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            response.read(256)
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def main() -> int:
    parser = argparse.ArgumentParser(description="Send local Juice Shop probe scenarios")
    parser.add_argument(
        "--severity",
        default="",
        help="Comma list to filter: MEDIUM,HIGH,CRITICAL,CLEAN (empty = all)",
    )
    args = parser.parse_args()
    wanted = {part.strip().upper() for part in args.severity.split(",") if part.strip()}

    base_url, default_host, scenarios = parse_scenarios(SCENARIOS.read_text(encoding="utf-8"))
    if wanted:
        scenarios = [
            s for s in scenarios
            if (s.get("expected_severity") or ("CLEAN" if s.get("expected") == "clean" else "")).upper()
            in wanted
        ]
    if not scenarios:
        print("no scenarios", file=sys.stderr)
        return 1
    print(f"=== probes via {base_url} ({len(scenarios)} scenarios) ===")
    print("Ethics: local Juice Shop behind Nginx only. Do not retarget this script.")
    if wanted:
        print(f"filter severity={','.join(sorted(wanted))}")
    failures = 0
    for scenario in scenarios:
        host = scenario.get("host") or default_host
        method = scenario.get("method") or "GET"
        path = scenario.get("path") or "/"
        query = scenario.get("query") or ""
        expected = scenario.get("expected") or "alert"
        severity = scenario.get("expected_severity") or ("CLEAN" if expected == "clean" else "?")
        try:
            status = send(base_url, host, method, path, query)
            print(
                f"{scenario.get('id')} expected={expected} "
                f"severity={severity} http={status} host={host}"
            )
        except Exception as error:  # noqa: BLE001 — keep the rest of the run going
            failures += 1
            print(f"{scenario.get('id')} expected={expected} severity={severity} ERROR {error}")
        time.sleep(0.25)
    print(f"=== done failures={failures} ===")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
