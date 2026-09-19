from __future__ import annotations

import hashlib

import pandas as pd

from .config import DEDUPED_CSV, INGESTED_CSV, NORMALIZED_CSV
from .io_utils import ensure_dirs
from .normalize import as_logged, canonical


def _hash_key(category: str, label: str, canon: str) -> str:
    blob = f"{category}|{label}|{canon}".encode("utf-8")
    return hashlib.sha256(blob).hexdigest()[:16]


def normalize_frame(df: pd.DataFrame) -> pd.DataFrame:
    out = df.copy()
    out["canonical"] = out["payload"].map(canonical)
    out["as_logged"] = out["payload"].map(as_logged)
    out = out[out["canonical"].str.len() > 0]
    return out


def normalize() -> pd.DataFrame:
    ensure_dirs()
    raw = pd.read_csv(INGESTED_CSV)
    normalized = normalize_frame(raw)
    normalized.to_csv(NORMALIZED_CSV, index=False)
    print(f"normalized {len(normalized)} rows -> {NORMALIZED_CSV}")
    return normalized


def deduplicate(df: pd.DataFrame | None = None) -> pd.DataFrame:
    ensure_dirs()
    if df is None:
        df = pd.read_csv(NORMALIZED_CSV)
    work = df.copy()
    work["dedupe_key"] = [
        _hash_key(str(c), str(lab), str(can))
        for c, lab, can in zip(work["category"], work["label"], work["canonical"])
    ]
    deduped = work.drop_duplicates(subset=["dedupe_key"], keep="first")
    deduped.to_csv(DEDUPED_CSV, index=False)
    print(f"deduped {len(work)} -> {len(deduped)} rows -> {DEDUPED_CSV}")
    return deduped
