from __future__ import annotations

import argparse

from .adversarial import generate_adversarial
from .compile import compile_outputs
from .config import DEDUPED_CSV
from .deduplicate import deduplicate, normalize
from .download import download
from .evaluate import evaluate
from .export_flyway import export_flyway
from .ingest import ingest
from .io_utils import ensure_dirs
from .split import split


def _prepare() -> None:
    ensure_dirs()
    ingest()
    normalized = normalize()
    deduped = deduplicate(normalized)
    split(deduped)
    generate_adversarial()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="python -m pipeline",
        description="Offline AI-assisted detection rule mining (no LLM API).",
    )
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("download", help="Fetch HttpParamsDataset + FuzzDB category extracts")
    sub.add_parser("ingest", help="Unify raw corpora into data/raw/ingested.csv")
    sub.add_parser("normalize", help="Canonicalize + as_logged columns")
    sub.add_parser("dedupe", help="Hash-dedupe by category+label+canonical")
    sub.add_parser("split", help="Generation/held-out split + cluster cards")
    sub.add_parser("cluster", help="Alias of split (taxonomy cards)")
    sub.add_parser("adversarial", help="Generate encoding/case/whitespace variants")
    sub.add_parser("compile", help="Abstract JSON in ai/outputs -> rules/candidates")
    ev = sub.add_parser("evaluate", help="Precision/recall/FPR/coverage report")
    ev.add_argument("--promote", action="store_true", help="Copy passing rules to reviewed/ + production/")
    sub.add_parser("export-flyway", help="Write INSERT seed SQL from rules/production")
    sub.add_parser("prepare", help="ingest → normalize → dedupe → split → adversarial")
    sub.add_parser("run", help="prepare + compile + evaluate --promote + export-flyway")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    cmd = args.cmd
    if cmd == "download":
        download()
    elif cmd == "ingest":
        ingest()
    elif cmd == "normalize":
        normalize()
    elif cmd == "dedupe":
        deduplicate()
    elif cmd in {"split", "cluster"}:
        import pandas as pd

        split(pd.read_csv(DEDUPED_CSV))
    elif cmd == "adversarial":
        generate_adversarial()
    elif cmd == "compile":
        compile_outputs()
    elif cmd == "evaluate":
        evaluate(promote=args.promote)
    elif cmd == "export-flyway":
        export_flyway()
    elif cmd == "prepare":
        _prepare()
    elif cmd == "run":
        download()
        _prepare()
        compile_outputs()
        evaluate(promote=True)
        export_flyway()
    else:
        raise SystemExit(f"unknown command {cmd}")


if __name__ == "__main__":
    main()
