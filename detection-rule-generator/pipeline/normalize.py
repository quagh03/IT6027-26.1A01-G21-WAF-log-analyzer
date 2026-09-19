from __future__ import annotations

import re
from urllib.parse import quote, unquote_plus


_PERCENT = re.compile(r"%[0-9A-Fa-f]{2}")
_WS = re.compile(r"\s+")


def url_decode_loop(text: str, max_times: int = 3) -> str:
    current = text
    for _ in range(max_times):
        decoded = unquote_plus(current)
        if decoded == current:
            break
        current = decoded
    return current


def canonical(text: str) -> str:
    decoded = url_decode_loop(str(text))
    return _WS.sub(" ", decoded).strip().lower()


def as_logged(text: str) -> str:
    """Approximate Nginx `args` encoding of a parameter value."""
    raw = str(text)
    if _PERCENT.search(raw):
        return raw
    return quote(raw, safe="")


def match_candidates(payload: str) -> list[str]:
    raw = str(payload)
    encoded = as_logged(raw)
    canon = canonical(raw)
    return [
        raw,
        canon,
        encoded,
        f"q={encoded}",
        f"id={raw}",
        f"file={encoded}",
    ]
