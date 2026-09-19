# Rule Creation — AI-assisted Detection Rule Mining (Offline Lab)

> Spec cho module **`detection-rule-generator/`** trong monorepo.  
> **Không** thay Spring runtime. Consumer cuối cùng là entity `DetectionRule` trong `docs/solution-design.md` (§7.1 / §7.2.1).

| Trường | Giá trị |
|--------|---------|
| Phiên bản | `1.1` |
| Ngày | 2026-09-18 |
| Trạng thái | **Aligned with solution-design v1.6** |
| Runtime SoT | `docs/solution-design.md` |

---

## 0. Vai trò trong hệ thống

Module này là **offline laboratory**:

```text
Attack corpora
      → normalize / dedupe / cluster / split
      → AI (Cursor / local LLM): gen → review → optimize
      → abstract rule → compiler → regex DetectionRule JSON
      → evaluator (held-out + benign + adversarial)
      → rules/production/
              │
              ▼
     Flyway seed / admin import → Spring rule engine (runtime)
```

| Việc | Ai làm | Ghi chú |
|------|--------|---------|
| Sinh / review rule | AI + human | Offline only |
| Detect trên Kafka log | Spring regex engine | **Không** gọi LLM |
| Giải thích Incident | Ollama async | Vai trò LLM **khác** — xem solution-design §9 |

**Định vị sản phẩm phụ:** *AI-assisted Security Rule Mining & Evaluation*  
Mục tiêu: **coverage cao + false positive thấp + rule tổng quát** (không overfit exact payload), rồi xuất **8–15 production rule** đủ seed MVP.

---

## 1. Cấu trúc thư mục (chốt)

```text
detection-rule-generator/
│
├── data/
│   ├── raw/
│   │   ├── sqli/                 # e.g. Modified_SQL_Dataset.csv (~30k)
│   │   ├── xss/
│   │   ├── path-traversal/
│   │   └── benign/               # HttpParams benign + samples sạch
│   │
│   ├── normalized/
│   ├── clustered/
│   └── evaluation/               # held-out, adversarial, reports inputs
│
├── pipeline/
│   ├── ingest
│   ├── normalize
│   ├── deduplicate
│   ├── cluster
│   └── split                     # generation vs held-out (AI không thấy held-out)
│
├── ai/
│   ├── prompts/
│   │   ├── rule-generator
│   │   ├── rule-reviewer
│   │   └── rule-optimizer
│   └── outputs/
│
├── rules/
│   ├── candidates/               # abstract + compiled thô
│   ├── reviewed/                 # sau AI reviewer + human gate
│   └── production/               # chỉ rule pass eval → import Spring
│
├── evaluator/
│   ├── positive-tests/
│   ├── negative-tests/
│   └── reports/                  # precision / recall / FPR / coverage
│
└── README.md                     # cách tải dataset, chạy pipeline, export
```

Runtime Spring / Kafka / Compose **không** bắt buộc để chạy lab này.

---

## 2. Ba vai trò dataset

Không gom mọi payload vào một đống.

### A. Generation Dataset

AI **được** nhìn để tìm pattern và sinh rule.

| Category | PRIMARY | SUPPLEMENTARY |
|----------|---------|---------------|
| SQLI | Dataset SQLi ~30k hiện có; HttpParamsDataset; FuzzDB (SQL) | SQLi payload collections |
| XSS | HttpParamsDataset; FuzzDB (XSS); XSS-specific corpus | XSS polyglot / filter-evasion |
| PATH_TRAVERSAL | HttpParamsDataset; FuzzDB (traversal); path lists | Encoding / OS-specific variants |

**Ràng buộc access-log (khớp solution-design §5.5):**  
Payload dùng để mine rule phải có ý nghĩa trên **`path` / `query` / `ua` / `raw`**. Không thiết kế rule phụ thuộc POST body.

### B. Evaluation Dataset (held-out)

AI **không** được nhìn khi generate.

MVP:

- Held-out split từ cùng nguồn (sau dedupe)
- Benign parameter values (đo FPR)
- Adversarial variants do pipeline / AI reviewer sinh

Phase 2 (tuỳ chọn): **OWASP Benchmark** làm external eval — không bắt buộc MVP.

### C. Adversarial Dataset

Sau khi có candidate rule:

```text
original → case → whitespace → encoding → separator → obfuscation
```

Đặc biệt quan trọng với Path Traversal (`%2e%2e%2f`, double encoding, `..%5c`, …).

---

## 3. Nguồn dataset & thứ tự tải

**Không** cần tải hết ngay.

| Phase | Nguồn | Mục đích |
|-------|--------|----------|
| 1 | [HttpParamsDataset](https://github.com/Morzeux/HttpParamsDataset/) | Baseline chung: benign + SQLi + XSS + Path Traversal |
| 2 | [FuzzDB](https://github.com/fuzzdb-project/fuzzdb) | Pattern diversity / bypass / edge cases (extract đúng category, không dump cả repo vào prompt) |
| 3 | SQLi ~30k trong `data/raw/sqli/` | Nguồn SQLi lớn nhất của project |
| 4 | XSS-specific + Path Traversal lists | Bổ sung 2 category yếu hơn |
| 5 (Phase 2) | OWASP Benchmark | Eval ngoài; **không** cho AI thấy |

### Ethics & license

- FuzzDB / HttpParams / payload lists là **attack corpora** dùng cho lab — không coi mọi chuỗi trong corpus là “luôn nguy hiểm trong mọi context”.
- XSS và Path Traversal phụ thuộc context mạnh; rule phải có `negative_examples` và đo FPR trên benign.
- Không bắt buộc commit full upstream corpora nếu license hạn chế: README module ghi nguồn + lệnh tải; chỉ commit subset / normalized cần thiết.

---

## 4. Pipeline hoàn chỉnh

```text
                 RAW DATA
                    │
       ┌────────────┼────────────┐
       ▼            ▼            ▼
      SQLi          XSS       Traversal (+ benign)
       │            │            │
       └────────────┼────────────┘
                    ▼
              NORMALIZATION
                    │
                    ▼
               DEDUPLICATION
                    │
                    ▼
              PATTERN MINING / CLUSTERING
                    │
                    ▼
             ┌──────────────┐
             │  Cursor / AI │
             │ Rule Builder │ 
             └──────┬───────┘
                    ▼
             Candidate (abstract)
                    │
                    ▼
             AI Rule Reviewer
                    │
                    ▼
             Rule Optimizer
                    │
                    ▼
             Rule Compiler  →  DetectionRule JSON
                    │
                    ▼
              Final candidates
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
   Held-out     Adversarial   Benign
        │           │           │
        └───────────┼───────────┘
                    ▼
                EVALUATOR
                    │
                    ▼
         Human review gate
                    │
                    ▼
         rules/production/  →  Spring Flyway / POST /api/rules
```

---

## 5. Abstract rule trước, regex sau

AI **không** nên emit regex “một phát” làm artifact cuối.

### 5.1 Abstract rule (logic)

```json
{
  "id": "SQLI-BOOLEAN-001",
  "type": "BOOLEAN_TAUTOLOGY",
  "category": "SQLI",
  "subcategory": "BOOLEAN_BASED",
  "normalization": [
    "URL_DECODE",
    "LOWERCASE",
    "WHITESPACE_NORMALIZE"
  ],
  "conditions": [
    "SQL_BOOLEAN_OPERATOR",
    "COMPARISON_EXPRESSION",
    "TAUTOLOGY"
  ],
  "suggested_target_field": "query",
  "suggested_weight": 40,
  "positive_examples": [],
  "negative_examples": [],
  "source_datasets": [
    "modified-sql-dataset",
    "HttpParamsDataset",
    "FuzzDB"
  ]
}
```

### 5.2 Compiler

```text
Abstract Rule
      ↓
Rule Compiler
      ↓
Runtime DetectionRule JSON  (§6 — hợp đồng bắt buộc)
```

Như vậy đánh giá được **logic** rule trước khi gắn regex cụ thể.

---

## 6. Hợp đồng export → Spring `DetectionRule` (bắt buộc)

Khớp `docs/solution-design.md` §7.2.1 và **Java class design §7.3**. Mọi file trong `rules/production/` phải map được 1-1 sang entity JPA.

### 6.1 Schema runtime (required fields)

```json
{
  "code": "SQLI-BOOLEAN-001",
  "name": "Boolean tautology in query",
  "category": "SQLI",
  "pattern": "(?i)(\\bor\\b|\\band\\b)\\s+\\d+\\s*=\\s*\\d+",
  "target_field": "query",
  "weight": 40,
  "enabled": true,
  "description": "Detects classic boolean-based SQLi tautologies in query string",
  "source": "AI_MINED",
  "generator_rule_id": "SQLI-BOOLEAN-001",
  "rule_version": "1.0.0"
}
```

### 6.2 Enum & ràng buộc

| Field | Giá trị hợp lệ | Java type |
|-------|----------------|-----------|
| `category` | `SQLI` \| `XSS` \| `PATH_TRAVERSAL` | `RuleCategory` |
| `target_field` | `path` \| `query` \| `ua` \| `raw` | `TargetField` (`PATH`/`QUERY`/`UA`/`RAW`) |
| `weight` | số nguyên > 0 | `int` |
| `source` | `AI_MINED` \| `HAND` | `RuleSource` |
| `pattern` | Regex Java-compatible | `String` → `Pattern.compile` khi evaluate |

### 6.3 Map sang class Java (outcome thật sự)

```text
rules/production/*.json
        │
        ▼
 RuleCreateRequest  (DTO)
        │
        ▼
 DetectionRule      (JPA entity)
        │
        ├── RuleEngine.evaluate(WebEvent) → DetectionHit[]
        └── Flyway seed / POST /api/rules
```

| JSON | `DetectionRule` field | Ghi chú |
|------|----------------------|---------|
| `code` | `code` | Unique; key upsert |
| `name` | `name` | |
| `category` | `category: RuleCategory` | |
| `pattern` | `pattern` | |
| `target_field` | `targetField: TargetField` | |
| `weight` | `weight` | |
| `enabled` | `enabled` | |
| `description` | `description` | |
| `source` | `source: RuleSource` | mặc định `AI_MINED` |
| `generator_rule_id` | `generatorRuleId` | |
| `rule_version` | `ruleVersion` | |

Chi tiết package, entity đầy đủ, `RuleImportService`, Flyway columns: **`docs/solution-design.md` §7.3**.

### 6.4 Metadata lab (optional, không persist Spring)

Giữ trong abstract / reviewed JSON để báo cáo:

```json
{
  "coverage": { "known": 0.0, "adversarial": 0.0 },
  "confidence": 0.96,
  "metrics": {
    "precision": 0.0,
    "recall": 0.0,
    "fpr": 0.0
  }
}
```

Không map vào `DetectionRule` entity — tránh phình schema runtime.

### 6.5 Đường import

1. Script export `rules/production/` → Flyway `V*__seed_detection_rules.sql` (cột đủ provenance §7.3.6)
2. Hoặc admin `POST /api/rules` / batch `RuleImportService` (JWT)
3. Mục tiêu MVP: **8–15** rule enabled covering đủ 3 category

---

## 7. Gợi ý weight (map scoring runtime)

Scoring runtime (solution-design §7.4): `base = min(100, sum(hit.weight))`, alert khi `score >= 60`.

Gợi ý khi AI propose `suggested_weight`:

| Mức | Weight gợi ý | Ý nghĩa |
|-----|--------------|---------|
| Signal yếu / generic | 15–25 | Cần nhiều hit / freq mới alert |
| Pattern rõ (thường MEDIUM) | 30–45 | Một hit có thể gần threshold |
| Pattern mạnh (HIGH) | 50–70 | Một hit thường đủ alert |
| Rất đặc trưng (CRITICAL) | 85–100 | Promote IMMEDIATE khi đủ threshold app |

Human review chỉnh weight trước khi vào `production/`.

---

## 8. Metrics đánh giá

Không chỉ accuracy.

| Metric | Công thức / ý nghĩa |
|--------|---------------------|
| Precision | `TP / (TP + FP)` — bắt nhầm? |
| Recall | `TP / (TP + FN)` — bỏ sót? |
| F1 | `2PR / (P + R)` |
| FPR | FP trên benign — ưu tiên cao |
| Rule Coverage | Unique attack payloads hit / total attack payloads (theo category) |

**Coverage không chỉ tính trên generation set** — phải báo trên held-out + adversarial.

Gate gợi ý trước `production/` (có thể chỉnh trong README module):

- FPR trên benign dưới ngưỡng team (ví dụ &lt; 1–2% tuỳ corpus)
- Mỗi category MVP có ≥ 2–3 rule pass gate
- Không seed rule chỉ match đúng 1 cluster payload cụ thể nếu không generalize trên held-out

---

## 9. FuzzDB — cách dùng

Không copy nguyên FuzzDB vào AI prompt.

```text
FuzzDB
├── SQL Injection      → data/raw/sqli/fuzzdb/
├── XSS                → data/raw/xss/fuzzdb/
├── Traversal          → data/raw/path-traversal/fuzzdb/
└── encoding patterns  → dùng cho adversarial generator
```

Normalize:

```text
raw payload → canonical representation → cluster / pattern
```

---

## 10. Output cuối của module

```text
              DATASETS
                  │
                  ▼
         ┌─────────────────┐
         │  AI Rule Miner  │
         └────────┬────────┘
                  │
       ┌──────────┼──────────┐
       ▼          ▼          ▼
     SQLI        XSS     PATH_TRAVERSAL
       │          │          │
       └──────────┼──────────┘
                  ▼
        rules/production/*.json
                  │
                  ▼
         Spring DetectionRule
                  │
                  ▼
     (runtime) score → alert → incident
```

Ba artifact nộp kèm BTL:

1. `rules/production/` (8–15 rule đã review)
2. `evaluator/reports/` (precision / recall / FPR / coverage)
3. Mô tả ngắn trong báo cáo: AI mine offline ≠ LLM detect online

---

## 11. Liên kết solution-design

| Chủ đề | File / mục |
|--------|------------|
| Entity `DetectionRule`, scoring, incident | `docs/solution-design.md` §7 |
| Java class design (enum/entity/DTO/service) | `docs/solution-design.md` §7.3 |
| LLM offline vs online | `docs/solution-design.md` §9 |
| Eval 2 tầng (corpus + Juice probes) | `docs/solution-design.md` §11 |
| Monorepo layout | `docs/solution-design.md` §14 |
| Attack Scenario Runner (E2E, không thay corpus eval) | `docs/solution-design.md` §5.5 |

---

## 12. Changelog

| Version | Date | Notes |
|---------|------|-------|
| 0.x | — | Draft độc lập (“tạm bỏ Spring”) — **không còn hiệu lực** |
| 1.0 | 2026-09-15 | Align solution-design v1.5: offline lab trong monorepo; contract `DetectionRule`; tách LLM; held-out/adversarial MVP; OWASP Benchmark = Phase 2 |
| 1.1 | 2026-09-18 | Map rõ JSON → Java (`RuleCategory`, `TargetField`, `RuleSource`, `DetectionRule`, DTO/import); scoring ref §7.4 |
