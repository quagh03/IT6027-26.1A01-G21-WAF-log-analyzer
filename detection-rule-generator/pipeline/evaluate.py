from __future__ import annotations

import re

import pandas as pd

from .config import (
    ADVERSARIAL_CSV,
    AI_OUTPUTS,
    BENIGN_CSV,
    CANDIDATES,
    FPR_GATE,
    HELD_OUT_CSV,
    LABEL_ATTACK,
    MIN_HELD_OUT_HITS,
    PRECISION_GATE,
    PRODUCTION,
    REPORTS,
    REVIEWED,
)
from .compile import compile_outputs, write_production_rule
from .io_utils import ensure_dirs, read_json, write_json, write_text
from .normalize import match_candidates


def load_candidate_rules() -> list[dict]:
    rules: list[dict] = []
    for path in sorted(CANDIDATES.glob("*.json")):
        payload = read_json(path)
        compiled = payload.get("compiled", payload)
        compiled["_path"] = str(path)
        rules.append(compiled)
    return rules


def _compile_regex(pattern: str) -> re.Pattern[str]:
    return re.compile(pattern)


def payload_hits(regex: re.Pattern[str], payload: str) -> bool:
    try:
        return any(regex.search(candidate) for candidate in match_candidates(payload))
    except re.error:
        return False


def _safe_div(num: float, den: float) -> float:
    return float(num) / float(den) if den else 0.0


def evaluate_rule(
    rule: dict,
    held_out: pd.DataFrame,
    benign: pd.DataFrame,
    adversarial: pd.DataFrame,
) -> dict:
    regex = _compile_regex(rule["pattern"])
    cat = rule["category"]

    held_cat = held_out[(held_out["label"] == LABEL_ATTACK) & (held_out["category"] == cat)]
    adv_cat = adversarial[adversarial["category"] == cat] if not adversarial.empty else adversarial

    held_hits = int(held_cat["payload"].map(lambda p: payload_hits(regex, str(p))).sum()) if len(held_cat) else 0
    adv_hits = int(adv_cat["payload"].map(lambda p: payload_hits(regex, str(p))).sum()) if len(adv_cat) else 0
    fp = int(benign["payload"].map(lambda p: payload_hits(regex, str(p))).sum()) if len(benign) else 0

    precision = _safe_div(held_hits, held_hits + fp)
    # Encoding-specific rules (e.g. double-encoded traversal) may only appear on adversarial.
    effective_tp = held_hits if held_hits else adv_hits
    if held_hits == 0 and adv_hits > 0:
        precision = _safe_div(adv_hits, adv_hits + fp)
    recall = _safe_div(held_hits, len(held_cat))
    adv_recall = _safe_div(adv_hits, len(adv_cat))
    f1_recall = recall if held_hits else adv_recall
    f1 = _safe_div(2 * precision * f1_recall, precision + f1_recall)
    fpr = _safe_div(fp, len(benign))
    coverage_held = recall
    coverage_adv = _safe_div(adv_hits, len(adv_cat))

    pass_gate = (
        fpr <= FPR_GATE
        and precision >= PRECISION_GATE
        and effective_tp >= MIN_HELD_OUT_HITS
    )
    return {
        "code": rule["code"],
        "category": cat,
        "target_field": rule["target_field"],
        "weight": rule["weight"],
        "held_out_tp": held_hits,
        "held_out_n": int(len(held_cat)),
        "adversarial_tp": adv_hits,
        "adversarial_n": int(len(adv_cat)),
        "benign_fp": fp,
        "benign_n": int(len(benign)),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
        "fpr": round(fpr, 4),
        "coverage": {"known": round(coverage_held, 4), "adversarial": round(coverage_adv, 4)},
        "pass_gate": pass_gate,
    }


def evaluate_ruleset(
    rules: list[dict],
    held_out: pd.DataFrame,
    benign: pd.DataFrame,
    adversarial: pd.DataFrame,
) -> dict:
    compiled = [(rule, _compile_regex(rule["pattern"])) for rule in rules]
    coverage: dict[str, dict[str, float]] = {}
    for cat in sorted({r["category"] for r in rules}):
        held_cat = held_out[(held_out["label"] == LABEL_ATTACK) & (held_out["category"] == cat)]
        adv_cat = adversarial[adversarial["category"] == cat] if not adversarial.empty else adversarial

        def any_hit(payload: str, regs: list[re.Pattern[str]]) -> bool:
            return any(payload_hits(reg, payload) for _, reg in regs)

        cat_regs = [(r, rx) for r, rx in compiled if r["category"] == cat]
        held_hit = int(held_cat["payload"].map(lambda p: any_hit(str(p), cat_regs)).sum()) if len(held_cat) else 0
        adv_hit = int(adv_cat["payload"].map(lambda p: any_hit(str(p), cat_regs)).sum()) if len(adv_cat) else 0
        fp = int(benign["payload"].map(lambda p: any_hit(str(p), cat_regs)).sum()) if len(benign) else 0
        coverage[cat] = {
            "held_out_coverage": round(_safe_div(held_hit, len(held_cat)), 4),
            "adversarial_coverage": round(_safe_div(adv_hit, len(adv_cat)), 4),
            "benign_fpr": round(_safe_div(fp, len(benign)), 4),
            "held_out_n": int(len(held_cat)),
            "adversarial_n": int(len(adv_cat)),
        }
    return coverage


def evaluate(promote: bool = False) -> dict:
    ensure_dirs()
    rules = load_candidate_rules()
    if not rules:
        abstracts = list(AI_OUTPUTS.glob("*.json"))
        if abstracts:
            print(f"No candidates yet; compiling {len(abstracts)} abstracts from {AI_OUTPUTS}")
            compile_outputs()
            rules = load_candidate_rules()
    if not rules:
        raise SystemExit(
            f"No candidate rules in {CANDIDATES}.\n"
            f"Abstracts found in {AI_OUTPUTS}: {len(list(AI_OUTPUTS.glob('*.json')))}.\n"
            "Save abstract JSON (ai/prompts/rule-generator.md) then:\n"
            "  python -m pipeline compile\n"
            "  python -m pipeline evaluate --promote"
        )
    held_out = pd.read_csv(HELD_OUT_CSV)
    benign = pd.read_csv(BENIGN_CSV)
    adversarial = pd.read_csv(ADVERSARIAL_CSV) if ADVERSARIAL_CSV.exists() else pd.DataFrame()

    per_rule = [evaluate_rule(rule, held_out, benign, adversarial) for rule in rules]
    passing = [row for row in per_rule if row["pass_gate"]]
    passing_rules = [r for r in rules if any(p["code"] == r["code"] and p["pass_gate"] for p in passing)]
    ruleset = evaluate_ruleset(passing_rules or rules, held_out, benign, adversarial)

    report = {
        "gates": {
            "fpr_max": FPR_GATE,
            "precision_min": PRECISION_GATE,
            "min_held_out_hits": MIN_HELD_OUT_HITS,
            "benign_source": "HttpParamsDataset",
        },
        "per_rule": per_rule,
        "ruleset_by_category": ruleset,
        "passing_codes": [row["code"] for row in passing],
        "passing_count": len(passing),
    }
    write_json(REPORTS / "metrics.json", report)
    write_text(REPORTS / "metrics.md", _render_markdown(report))

    if promote:
        REVIEWED.mkdir(parents=True, exist_ok=True)
        PRODUCTION.mkdir(parents=True, exist_ok=True)
        by_code = {r["code"]: r for r in rules}
        for row in passing:
            compiled = by_code[row["code"]]
            reviewed = read_json(CANDIDATES / f"{compiled['code']}.json")
            reviewed["metrics"] = row
            write_json(REVIEWED / f"{compiled['code']}.json", reviewed)
            write_production_rule(compiled, PRODUCTION)
        print(f"promoted {len(passing)} rules -> {PRODUCTION}")
    print(f"report -> {REPORTS / 'metrics.md'} passing={len(passing)}/{len(rules)}")
    return report


def _render_markdown(report: dict) -> str:
    lines = [
        "# Rule evaluation report",
        "",
        "Offline corpus metrics (held-out + HttpParams benign + adversarial).",
        "AI mine offline ≠ LLM detect online.",
        "",
        f"- FPR gate: `{report['gates']['fpr_max']}`",
        f"- Precision gate: `{report['gates']['precision_min']}`",
        f"- Passing: **{report['passing_count']}** (`{', '.join(report['passing_codes'])}`)",
        "",
        "## Per-rule",
        "",
        "| code | category | precision | recall | FPR | held-out hits | adv hits | pass |",
        "|---|---|---:|---:|---:|---:|---:|---|",
    ]
    for row in report["per_rule"]:
        lines.append(
            f"| {row['code']} | {row['category']} | {row['precision']:.4f} | "
            f"{row['recall']:.4f} | {row['fpr']:.4f} | {row['held_out_tp']}/{row['held_out_n']} | "
            f"{row['adversarial_tp']}/{row['adversarial_n']} | {row['pass_gate']} |"
        )
    lines += ["", "## Ruleset coverage by category", ""]
    for cat, stats in report["ruleset_by_category"].items():
        lines.append(
            f"- **{cat}**: held-out coverage `{stats['held_out_coverage']}`, "
            f"adversarial `{stats['adversarial_coverage']}`, "
            f"benign FPR `{stats['benign_fpr']}`"
        )
    lines.append("")
    return "\n".join(lines)
