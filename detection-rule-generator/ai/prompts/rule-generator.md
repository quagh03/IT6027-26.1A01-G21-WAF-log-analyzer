# Rule Generator — abstract detection rule

You are mining **WAF access-log** detection rules offline for this lab.

## Hard constraints

- Output **one JSON object only** (no markdown fence unless asked). This is an **abstract rule**, not production regex.
- Do **not** emit a final Java regex. A compiler maps `type` / `conditions` → `DetectionRule.pattern`.
- Payloads are parameter values visible on access logs: `path` / `query` / `ua` / `raw`. **Never** depend on POST body.
- Generalize. Do **not** overfit a single payload or one cluster member.
- Do **not** paste or request the full corpus. Use only the attached **cluster card**.
- Category enum: `SQLI` | `XSS` | `PATH_TRAVERSAL`.
- `suggested_target_field`: `query` (default SQLi/XSS) or `path` (traversal) or `ua` / `raw` if justified.
- `suggested_weight` (runtime: alert if sum(weights) ≥ 60):
  - 15–25 weak/generic
  - 30–45 clear (MEDIUM)
  - 50–70 strong (HIGH)
  - 85–100 very distinctive (CRITICAL)

## Input

A cluster card JSON with `category`, `subcategory`, `positive_examples`, `negative_examples`, `source_datasets`.

## Output schema

```json
{
  "id": "SQLI-BOOLEAN-001",
  "type": "BOOLEAN_TAUTOLOGY",
  "category": "SQLI",
  "subcategory": "BOOLEAN_BASED",
  "name": "short human name",
  "description": "one sentence, access-log scope",
  "normalization": ["URL_DECODE", "LOWERCASE", "WHITESPACE_NORMALIZE"],
  "conditions": ["SQL_BOOLEAN_OPERATOR", "COMPARISON_EXPRESSION", "TAUTOLOGY"],
  "suggested_target_field": "query",
  "suggested_weight": 40,
  "positive_examples": [],
  "negative_examples": [],
  "source_datasets": ["modified-sql-dataset", "HttpParamsDataset", "FuzzDB"]
}
```

`type` must be one of: `BOOLEAN_TAUTOLOGY`, `BOOLEAN_STRING`, `UNION`, `COMMENT_EVASION`, `STACKED`, `TIME_BASED`, `SCRIPT_TAG`, `EVENT_HANDLER`, `JS_URI`, `ENCODED`, `DOTDOT_SLASH`, `URL_ENCODED`, `DOUBLE_ENCODED`, `BACKSLASH`.

Copy 3–5 positives and 3–5 negatives from the card (truncate long strings). Save as `ai/outputs/<id>.json`.

Then a human runs: `python -m pipeline compile`.
