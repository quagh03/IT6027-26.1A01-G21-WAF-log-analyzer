from __future__ import annotations

from pathlib import Path

import pandas as pd

from .config import INGESTED_CSV, LABEL_ATTACK, LABEL_BENIGN, RAW, UPSTREAM
from .io_utils import ensure_dirs


def _row(payload: str, category: str, label: str, source: str, raw_id: str) -> dict:
    text = str(payload).strip()
    if not text:
        return {}
    return {
        "payload": text,
        "category": category,
        "label": label,
        "source": source,
        "raw_id": raw_id,
    }


def _ingest_modified_sql() -> list[dict]:
    path = RAW / "sqli" / "modified-sql-dataset.csv"
    df = pd.read_csv(path)
    rows: list[dict] = []
    for idx, rec in df.iterrows():
        label = LABEL_ATTACK if int(rec["Label"]) == 1 else LABEL_BENIGN
        category = "SQLI" if label == LABEL_ATTACK else "BENIGN"
        item = _row(
            rec["Query"],
            category,
            label,
            "modified-sql-dataset",
            f"sql-{idx}",
        )
        if item:
            rows.append(item)
    return rows


def _ingest_httpparams() -> list[dict]:
    path = UPSTREAM / "HttpParamsDataset" / "payload_full.csv"
    if not path.exists():
        raise SystemExit("HttpParamsDataset missing. Run: python -m pipeline download")
    df = pd.read_csv(path)
    df.columns = [c.strip().strip('"') for c in df.columns]
    mapping = {
        "sqli": "SQLI",
        "xss": "XSS",
        "path-traversal": "PATH_TRAVERSAL",
        "norm": "BENIGN",
    }
    rows: list[dict] = []
    for idx, rec in df.iterrows():
        attack_type = str(rec.get("attack_type", "")).strip().lower()
        if attack_type not in mapping:
            continue
        category = mapping[attack_type]
        label = LABEL_BENIGN if category == "BENIGN" else LABEL_ATTACK
        item = _row(
            rec["payload"],
            category,
            label,
            "HttpParamsDataset",
            f"httpparams-{idx}",
        )
        if item:
            rows.append(item)
    return rows


def _read_fuzzdb_payloads(path: Path) -> list[str]:
    payloads: list[str] = []
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        text = line.strip()
        if not text or text.startswith("#") or text.startswith("//"):
            continue
        payloads.append(text)
    return payloads


def _ingest_fuzzdb() -> list[dict]:
    rows: list[dict] = []
    mapping = {
        "SQLI": RAW / "sqli" / "fuzzdb",
        "XSS": RAW / "xss" / "fuzzdb",
        "PATH_TRAVERSAL": RAW / "path-traversal" / "fuzzdb",
    }
    for category, folder in mapping.items():
        if not folder.exists():
            continue
        for file in sorted(folder.glob("*.txt")):
            for i, payload in enumerate(_read_fuzzdb_payloads(file)):
                item = _row(
                    payload,
                    category,
                    LABEL_ATTACK,
                    "FuzzDB",
                    f"fuzzdb-{category}-{file.stem}-{i}",
                )
                if item:
                    rows.append(item)
    return rows


def ingest() -> pd.DataFrame:
    ensure_dirs()
    rows = _ingest_modified_sql() + _ingest_httpparams() + _ingest_fuzzdb()
    df = pd.DataFrame(rows)
    df.to_csv(INGESTED_CSV, index=False)
    print(df.groupby(["source", "category", "label"]).size().to_string())
    print(f"ingested {len(df)} rows -> {INGESTED_CSV}")
    return df
