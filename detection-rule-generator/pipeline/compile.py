from __future__ import annotations

import re
from pathlib import Path

from .config import (
    AI_OUTPUTS,
    CANDIDATES,
    CATEGORIES,
    PRODUCTION,
    RULE_VERSION,
)
from .io_utils import ensure_dirs, read_json, write_json
from .templates import TEMPLATES, resolve_template_key

PRODUCTION_FIELDS = (
    "code",
    "name",
    "category",
    "pattern",
    "target_field",
    "weight",
    "enabled",
    "description",
    "source",
    "generator_rule_id",
    "rule_version",
)

JAVA_UNSAFE = re.compile(
    r"\(\?P<|\\g<|\(\?R\)|\(\?>|\\A|\\Z|\\s<|\\p\{"
)


def _validate_pattern(pattern: str) -> None:
    if JAVA_UNSAFE.search(pattern):
        raise ValueError(f"pattern uses Python-only/Java-unsafe syntax: {pattern}")
    re.compile(pattern)


def compile_abstract(abstract: dict) -> dict:
    key = resolve_template_key(abstract)
    if key is None:
        raise ValueError(f"Cannot resolve compiler template for {abstract.get('id')}")
    tmpl = TEMPLATES[key]
    code = str(abstract.get("id") or tmpl["code"])
    target = str(
        abstract.get("suggested_target_field")
        or tmpl["target_field"]
    ).lower()
    if target not in {"path", "query", "ua", "raw"}:
        raise ValueError(f"invalid target_field {target}")
    category = str(abstract.get("category") or tmpl["category"]).upper()
    if category not in CATEGORIES:
        raise ValueError(f"invalid category {category}")
    weight = int(abstract.get("suggested_weight") or tmpl["weight"])
    if weight <= 0:
        raise ValueError("weight must be > 0")
    pattern = tmpl["pattern"]
    _validate_pattern(pattern)
    compiled = {
        "code": code,
        "name": str(abstract.get("name") or tmpl["name"]),
        "category": category,
        "pattern": pattern,
        "target_field": target,
        "weight": weight,
        "enabled": True,
        "description": str(abstract.get("description") or tmpl["description"]),
        "source": "AI_MINED",
        "generator_rule_id": code,
        "rule_version": str(abstract.get("rule_version") or RULE_VERSION),
    }
    return compiled


def production_only(compiled: dict) -> dict:
    return {field: compiled[field] for field in PRODUCTION_FIELDS}


def compile_outputs(source_dir: Path | None = None) -> list[dict]:
    ensure_dirs()
    src = source_dir or AI_OUTPUTS
    paths = sorted(src.glob("*.json"))
    if not paths:
        raise SystemExit(
            f"No abstract JSON in {src}.\n"
            "Save a Cursor abstract as ai/outputs/<id>.json "
            "(see ai/prompts/rule-generator.md), then rerun compile."
        )
    compiled_rules: list[dict] = []
    for path in paths:
        abstract = read_json(path)
        if "abstract" in abstract and "compiled" in abstract:
            compiled = abstract["compiled"]
            _validate_pattern(compiled["pattern"])
        else:
            compiled = compile_abstract(abstract)
        payload = {"abstract": abstract.get("abstract", abstract), "compiled": compiled}
        write_json(CANDIDATES / f"{compiled['code']}.json", payload)
        compiled_rules.append(compiled)
        print(f"compiled {compiled['code']} <- {path.name}")
    return compiled_rules


def write_production_rule(compiled: dict, dest_dir: Path = PRODUCTION) -> None:
    write_json(dest_dir / f"{compiled['code']}.json", production_only(compiled))
