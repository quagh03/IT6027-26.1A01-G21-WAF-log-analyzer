from __future__ import annotations

import json

import pandas as pd

from .cluster import classify_subtype
from .config import (
    BENIGN_CSV,
    CLUSTERED,
    CLUSTER_NEG_EXAMPLES,
    CLUSTER_POS_EXAMPLES,
    DEFAULT_TARGET_FIELD,
    GENERATION_CSV,
    GENERATION_FRAC,
    HELD_OUT_CSV,
    LABEL_ATTACK,
    LABEL_BENIGN,
    RANDOM_SEED,
)
from .io_utils import ensure_dirs, write_json


def stratified_split(df: pd.DataFrame, frac: float = GENERATION_FRAC) -> tuple[pd.DataFrame, pd.DataFrame]:
    parts: list[pd.DataFrame] = []
    for _, group in df.groupby(["category", "label"], dropna=False):
        n = max(1, int(round(len(group) * frac))) if len(group) else 0
        n = min(n, len(group))
        parts.append(group.sample(n=n, random_state=RANDOM_SEED) if n else group.iloc[0:0])
    generation = pd.concat(parts) if parts else df.iloc[0:0]
    held_out = df.drop(generation.index)
    return generation.sort_index(), held_out.sort_index()


def _sample_list(series: pd.Series, n: int) -> list[str]:
    if series.empty:
        return []
    take = min(n, len(series))
    return series.sample(n=take, random_state=RANDOM_SEED).astype(str).tolist()


def _suggested_rule_id(category: str, subtype: str) -> str:
    prefix = {"SQLI": "SQLI", "XSS": "XSS", "PATH_TRAVERSAL": "PT"}[category]
    token = {
        "BOOLEAN_TAUTOLOGY": "BOOLEAN",
        "UNION": "UNION",
        "COMMENT_EVASION": "COMMENT",
        "STACKED": "STACKED",
        "TIME_BASED": "TIME",
        "SCRIPT_TAG": "SCRIPT",
        "EVENT_HANDLER": "EVENT",
        "JS_URI": "JSURI",
        "ENCODED": "ENCODED",
        "DOTDOT_SLASH": "DOTDOT",
        "URL_ENCODED": "ENCODED",
        "DOUBLE_ENCODED": "DOUBLE",
        "BACKSLASH": "BACKSLASH",
        "OTHER": "OTHER",
    }.get(subtype, subtype)
    return f"{prefix}-{token}-001"


def write_cluster_cards(generation: pd.DataFrame, benign: pd.DataFrame) -> None:
    ensure_dirs()
    attacks = generation[generation["label"] == LABEL_ATTACK].copy()
    attacks["subcategory"] = [
        classify_subtype(str(cat), str(can), str(pay))
        for cat, can, pay in zip(attacks["category"], attacks["canonical"], attacks["payload"])
    ]
    attacks.to_csv(CLUSTERED / "generation_with_subtype.csv", index=False)

    for (category, subtype), group in attacks.groupby(["category", "subcategory"]):
        sources = sorted(group["source"].dropna().unique().tolist())
        positives = _sample_list(group["payload"], CLUSTER_POS_EXAMPLES)
        # Near-benign: same-source HTTP params, else any HttpParams benign.
        pool = benign
        if pool.empty:
            negatives: list[str] = []
        else:
            negatives = _sample_list(pool["payload"], CLUSTER_NEG_EXAMPLES)
        card = {
            "cluster_id": f"{category}-{subtype}",
            "category": category,
            "subcategory": subtype,
            "suggested_rule_id": _suggested_rule_id(str(category), str(subtype)),
            "suggested_target_field": DEFAULT_TARGET_FIELD.get(str(category), "query"),
            "count": int(len(group)),
            "positive_examples": positives,
            "negative_examples": negatives,
            "source_datasets": sources,
            "notes": (
                "Use this card with ai/prompts/rule-generator.md. "
                "Emit one abstract rule JSON only. Do not paste the full corpus. "
                "Do not overfit a single payload. Rules must apply to access-log "
                "fields path/query/ua/raw — not POST body."
            ),
        }
        write_json(CLUSTERED / f"{category}-{subtype}.json", card)

    summary = (
        attacks.groupby(["category", "subcategory"])
        .size()
        .reset_index(name="count")
        .to_dict(orient="records")
    )
    write_json(CLUSTERED / "summary.json", summary)
    print(json.dumps(summary, indent=2))


def split(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    ensure_dirs()
    generation, held_out = stratified_split(df)
    http_benign = df[
        (df["label"] == LABEL_BENIGN) & (df["source"] == "HttpParamsDataset")
    ]
    generation.to_csv(GENERATION_CSV, index=False)
    held_out.to_csv(HELD_OUT_CSV, index=False)
    http_benign.to_csv(BENIGN_CSV, index=False)
    write_cluster_cards(generation, http_benign)
    print(
        f"split generation={len(generation)} held_out={len(held_out)} "
        f"http_benign={len(http_benign)}"
    )
    return generation, held_out, http_benign
