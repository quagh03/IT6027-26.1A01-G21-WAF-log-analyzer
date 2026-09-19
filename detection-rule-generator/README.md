# Detection Rule Generator (offline lab)

AI-assisted **security rule mining** for the WAF Log Analyzer. This module is **not** on the Kafka / Spring detect path. Runtime still matches Java regex; LLM online only explains incidents.

```text
Attack corpora
  → normalize / dedupe / cluster / split
  → Cursor (prompts): gen → review → optimize
  → abstract rule → compiler → DetectionRule JSON
  → evaluator (held-out + HttpParams benign + adversarial)
  → rules/production/  →  Flyway seed / POST /api/rules
```

Spec: [`docs/rule-creation.md`](../docs/rule-creation.md) · contract: [`docs/solution-design.md`](../docs/solution-design.md) §7.2.1.

## Ethics

HttpParams, FuzzDB, and the SQLi CSV are **attack corpora for this lab**. Do not treat every string as “always malicious in every context”. XSS and path traversal are context-heavy — every production rule must have `negative_examples` and a measured FPR on benign HTTP parameter values. Probe only self-hosted lab targets (Juice Shop), never third-party sites.

## Layout

```text
detection-rule-generator/
├── data/raw/                 # SQLi CSV + downloaded extracts
├── data/normalized/
├── data/clustered/           # compact cluster cards (not the full 30k)
├── data/evaluation/          # generation / held-out / benign / adversarial
├── pipeline/                 # Python CLI — no LLM API
├── ai/prompts/               # generator, reviewer, optimizer
├── ai/outputs/               # abstract JSON from Cursor
├── rules/candidates|reviewed|production/
└── evaluator/reports/
```

Full upstream clones stay in `data/raw/_upstream/` (gitignored). FuzzDB is **extracted by category only** — never dumped into a prompt.

## Setup

```bash
cd detection-rule-generator
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Run

```bash
python -m pipeline download    # HttpParams + FuzzDB extracts
python -m pipeline run         # ingest → cluster cards → compile → eval → production → Flyway SQL
```

Step by step:

```bash
python -m pipeline ingest
python -m pipeline normalize
python -m pipeline dedupe
python -m pipeline split       # 70% generation / 30% held-out; cards in data/clustered/
python -m pipeline adversarial
python -m pipeline compile     # bắt buộc: ai/outputs/*.json → rules/candidates/
python -m pipeline evaluate --promote

`prepare` **dừng trước compile**. `evaluate` cần `ai/outputs/*.json` (repo đã có 14 file mẫu). Chạy từ `detection-rule-generator/` trong monorepo — bản copy thiếu `ai/outputs/` sẽ fail.
python -m pipeline export-flyway
```

Cursor loop (no API keys):

1. Open one file under `data/clustered/*.json` (do **not** paste the full corpus).
2. Follow `ai/prompts/rule-generator.md` → save `ai/outputs/<id>.json`.
3. `python -m pipeline compile`.
4. `python -m pipeline evaluate` — if FPR is high use `rule-reviewer.md` / `rule-optimizer.md`.
5. `--promote` copies gate-passing rules to `rules/production/`.

## Sources (download order)

| Phase | Source | Role |
|-------|--------|------|
| 1 | [HttpParamsDataset](https://github.com/Morzeux/HttpParamsDataset/) | Benign + SQLi + XSS + Path Traversal |
| 2 | [FuzzDB](https://github.com/fuzzdb-project/fuzzdb) (sql-injection/detect, xss, path-traversal only) | Pattern diversity |
| 3 | `data/raw/sqli/modified-sql-dataset.csv` | Largest SQLi set (~30k) |

SQL dataset `Label=0` rows are kept as extra SQL-shaped text; **FPR gate uses HttpParams `norm` only** (true HTTP parameter values).

## Gates (tunable in `pipeline/config.py`)

- FPR on HttpParams benign **< 2%**
- Precision **≥ 0.80**
- ≥ 1 held-out hit (no single-payload overfit)
- MVP: **8–15** production rules, each category **≥ 2–3**

## Handoff to Spring (Week 02)

`export/V2__seed_detection_rules.sql` maps 1-1 to `DetectionRule` (`source=AI_MINED`, `generator_rule_id`, `rule_version`). Copy into `backend/src/main/resources/db/migration/` when the rule engine is wired. Do not run this lab inside Docker Compose.
