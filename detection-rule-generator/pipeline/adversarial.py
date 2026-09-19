from __future__ import annotations

from urllib.parse import quote

import pandas as pd

from .config import ADVERSARIAL_CSV, HELD_OUT_CSV, LABEL_ATTACK
from .normalize import as_logged, canonical


def _variants(payload: str, category: str) -> list[tuple[str, str]]:
    raw = str(payload)
    out: list[tuple[str, str]] = [
        (raw.upper(), "case"),
        (raw.lower(), "case"),
        re_ws(raw),
        (quote(raw, safe=""), "url_encode"),
        (quote(quote(raw, safe=""), safe=""), "double_encode"),
    ]
    if category == "PATH_TRAVERSAL":
        if "../" in raw:
            out.append((raw.replace("../", "..\\"), "separator"))
        if "..\\" in raw or "..\\\\" in raw:
            out.append((raw.replace("..\\", "../"), "separator"))
        out.append((raw.replace("../", "%2e%2e%2f").replace("..\\", "%2e%2e%5c"), "obfuscation"))
        out.append((raw.replace(".", "%2e").replace("/", "%2f").replace("\\", "%5c"), "obfuscation"))
    if category == "SQLI":
        out.append((raw.replace(" ", "/**/"), "obfuscation"))
        out.append((raw.replace("=", "%3d").replace("'", "%27"), "obfuscation"))
    if category == "XSS":
        out.append((raw.replace("<", "%3c").replace(">", "%3e"), "obfuscation"))
        out.append((raw.replace("<", "%253c"), "double_encode"))
    # unique non-empty, skip identical to original
    seen = {raw}
    uniq: list[tuple[str, str]] = []
    for text, kind in out:
        if text and text not in seen:
            seen.add(text)
            uniq.append((text, kind))
    return uniq


def re_ws(raw: str) -> tuple[str, str]:
    return ("  ".join(raw.split()), "whitespace")


def generate_adversarial(held_out: pd.DataFrame | None = None, per_row: int = 5) -> pd.DataFrame:
    df = held_out if held_out is not None else pd.read_csv(HELD_OUT_CSV)
    attacks = df[df["label"] == LABEL_ATTACK]
    rows: list[dict] = []
    preferred_pt = (
        "double_encode",
        "url_encode",
        "obfuscation",
        "separator",
        "case",
        "whitespace",
    )
    for _, rec in attacks.iterrows():
        category = str(rec["category"])
        variants = _variants(str(rec["payload"]), category)
        if category == "PATH_TRAVERSAL":
            ordered: list[tuple[str, str]] = []
            for kind in preferred_pt:
                ordered.extend([item for item in variants if item[1] == kind])
            leftover = [item for item in variants if item not in ordered]
            variants = (ordered + leftover)[:per_row]
        else:
            variants = variants[:per_row]
        for text, kind in variants:
            rows.append(
                {
                    "payload": text,
                    "category": rec["category"],
                    "label": LABEL_ATTACK,
                    "source": f"adversarial:{kind}",
                    "raw_id": f"{rec.get('raw_id', 'adv')}-{kind}",
                    "canonical": canonical(text),
                    "as_logged": as_logged(text),
                    "origin_payload": rec["payload"],
                }
            )
    out = pd.DataFrame(rows)
    out.to_csv(ADVERSARIAL_CSV, index=False)
    print(f"adversarial {len(out)} variants -> {ADVERSARIAL_CSV}")
    return out
