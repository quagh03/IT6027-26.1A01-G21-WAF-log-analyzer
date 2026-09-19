from __future__ import annotations

from pathlib import Path

from .compile import PRODUCTION_FIELDS, production_only
from .config import EXPORT, PRODUCTION
from .io_utils import ensure_dirs, read_json, write_text

SQL_PATH = EXPORT / "V2__seed_detection_rules.sql"


def _sql_str(value: object) -> str:
    text = str(value).replace("'", "''")
    return f"'{text}'"


def _sql_bool(value: object) -> str:
    return "TRUE" if bool(value) else "FALSE"


def export_flyway(src: Path | None = None) -> Path:
    ensure_dirs()
    folder = src or PRODUCTION
    files = sorted(folder.glob("*.json"))
    if not files:
        raise SystemExit(f"No production JSON in {folder}")
    values: list[str] = []
    for path in files:
        rule = production_only(read_json(path))
        missing = [k for k in PRODUCTION_FIELDS if k not in rule]
        if missing:
            raise SystemExit(f"{path.name} missing {missing}")
        values.append(
            "("
            + ", ".join(
                [
                    _sql_str(rule["code"]),
                    _sql_str(rule["name"]),
                    _sql_str(rule["category"]),
                    _sql_str(rule["pattern"]),
                    _sql_str(rule["target_field"]),
                    str(int(rule["weight"])),
                    _sql_bool(rule["enabled"]),
                    _sql_str(rule["description"]),
                    _sql_str(rule["source"]),
                    _sql_str(rule["generator_rule_id"]),
                    _sql_str(rule["rule_version"]),
                ]
            )
            + ")"
        )
    joined = ",\n".join(values)
    sql = (
        "-- Generated from detection-rule-generator/rules/production\n"
        "-- Copy into backend/src/main/resources/db/migration/ when seeding Spring.\n"
        "-- Provenance columns: source, generator_rule_id, rule_version (solution-design §7.3.6)\n"
        "\n"
        "INSERT INTO detection_rules (\n"
        "    code, name, category, pattern, target_field, weight, enabled,\n"
        "    description, source, generator_rule_id, rule_version\n"
        ") VALUES\n"
        f"{joined};\n"
    )
    write_text(SQL_PATH, sql)
    print(f"exported {len(files)} rules -> {SQL_PATH}")
    return SQL_PATH
