"""Shared helpers for Week 5 lab evaluation scripts."""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

API = "http://127.0.0.1:8080"
BASE = "http://127.0.0.1"
HOST = "juice.lab.local"


def token(username: str = "admin", password: str = "admin") -> str:
    body = json.dumps({"username": username, "password": password}).encode()
    req = urllib.request.Request(
        f"{API}/api/oauth/tokens",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.load(resp)["accessToken"]


def api_get(path: str, bearer: str, params: dict[str, str] | None = None) -> Any:
    url = f"{API}{path}"
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {bearer}"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def send_get(path: str, query: str, host: str = HOST, timeout: float = 8.0) -> int:
    # + for spaces (form style). %20 glued to alnum breaks \\b in SQLI regexes.
    encoded = urllib.parse.quote_plus(query, safe="=&%") if query else ""
    url = BASE.rstrip("/") + path
    if encoded:
        url = f"{url}?{encoded}"
    req = urllib.request.Request(url, method="GET", headers={"Host": host})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            resp.read(64)
            return int(resp.status)
    except urllib.error.HTTPError as err:
        return int(err.code)
    except Exception:
        return 0


def wait_healthy(timeout_s: float = 180.0) -> None:
    deadline = time.time() + timeout_s
    last_err = ""
    while time.time() < deadline:
        try:
            token()
            send_get("/", "")
            return
        except Exception as exc:  # noqa: BLE001
            last_err = str(exc)
            time.sleep(2)
    raise RuntimeError(f"lab not healthy: {last_err}")


def confusion(expected_attack: bool, got_alert: bool) -> str:
    if expected_attack and got_alert:
        return "TP"
    if expected_attack and not got_alert:
        return "FN"
    if (not expected_attack) and got_alert:
        return "FP"
    return "TN"


def rates(tp: int, fp: int, fn: int, tn: int) -> dict[str, float]:
    precision = tp / (tp + fp) if (tp + fp) else 0.0
    recall = tp / (tp + fn) if (tp + fn) else 0.0
    fpr = fp / (fp + tn) if (fp + tn) else 0.0
    score = recall - fpr
    return {
        "precision": precision,
        "recall": recall,
        "tpr": recall,
        "fpr": fpr,
        "score": score,
    }
