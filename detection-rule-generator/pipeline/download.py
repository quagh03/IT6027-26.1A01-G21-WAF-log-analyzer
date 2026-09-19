from __future__ import annotations

import urllib.error
import urllib.request
from pathlib import Path

from .config import FUZZDB_FILES, FUZZDB_RAW_BASE, HTTP_PARAMS_URL, RAW, UPSTREAM
from .io_utils import ensure_dirs


def _download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 0:
        print(f"skip existing {dest.relative_to(RAW.parent)}")
        return
    print(f"download {url}")
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            dest.write_bytes(response.read())
    except urllib.error.URLError as exc:
        raise SystemExit(f"Failed to download {url}: {exc}") from exc


def download() -> None:
    ensure_dirs()
    http_dest = UPSTREAM / "HttpParamsDataset" / "payload_full.csv"
    _download(HTTP_PARAMS_URL, http_dest)

    for category, rel_paths in FUZZDB_FILES.items():
        folder = {
            "SQLI": RAW / "sqli" / "fuzzdb",
            "XSS": RAW / "xss" / "fuzzdb",
            "PATH_TRAVERSAL": RAW / "path-traversal" / "fuzzdb",
        }[category]
        for rel in rel_paths:
            name = Path(rel).name
            _download(FUZZDB_RAW_BASE + rel, folder / name)

    print("download complete")
