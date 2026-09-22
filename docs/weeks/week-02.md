# Week 02 — Rule engine & risk scoring

| Trường | Giá trị |
|--------|---------|
| Tuần | `02` / `5` |
| Theme | Signature rules + DetectionHit + score 0–100 |
| Phụ thuộc | Week 01 DoD (event chuẩn trong DB) |
| Map outcome | **O3**, **O4**, **O5** (app threshold), **O10** (seed từ generator nếu sẵn) |
| Trạng thái | `Done` |

---

## 1. Problem statement

Event đã vào DB nhưng hệ thống chưa phân biệt request sạch và payload tấn công trên access log. Chưa có `rule_id` + evidence thì không giải thích được alert sau này, và chưa có score 0–100 thì không có ngưỡng promote. Tuần này đóng gap **detect + chấm điểm trên từng request**, chưa tạo Alert/Incident.

## 2. Goal

Mỗi `WebEvent` đi qua rule engine (SQLi, XSS, Path Traversal), ghi `DetectionHit`, tính risk score theo công thức §7.4, đọc lại được hits + score qua API.

## 3. Outcome

| ID | Outcome tuần | Tiêu chí đo |
|----|--------------|-------------|
| W2-O1 | Detect 3 category | Tối thiểu SQLi, XSS, Path Traversal; mỗi hit có `rule_id` + evidence (O3) |
| W2-O2 | Score per request | `risk_score` 0–100 = `base + freq_bonus(5m) + status_bonus` (O4, §7.4) |
| W2-O3 | Scope theo app | Threshold lấy từ `Application.risk_threshold` (default 60); score vẫn tính dù chưa tạo alert (O5) |
| W2-O4 | Seed rules | Flyway seed **8–15** rule enabled, 3 category; entity `DetectionRule` đủ provenance (`source`, `generator_rule_id`, `rule_version`) theo §7.3 |

## 4. Scope

### In-scope

- Load `DetectionRule` từ DB (`enabled=true`); evaluate regex theo `target_field` (`path`\|`query`\|`ua`\|`raw`)
- Persist `DetectionHit` (event, rule, evidence, weight)
- Scoring §7.4: `N = 5 phút`; `freq_bonus`; `status_bonus` cho 403/404/500 khi `base > 0`
- Ghi `WebEvent.risk_score`
- Seed 8–15 rule: SQLI, XSS, PATH_TRAVERSAL — ưu tiên từ `detection-rule-generator/rules/production/`; thiếu thì bổ sung `source=HAND`
- Entity/enum đúng §7.3: `RuleCategory`, `TargetField`, `RuleSource`, cột provenance
- `GET /api/events/{id}` trả hits + score
- Unit/integration test: vài payload dương + vài path sạch
- (Tuỳ chọn) `GET /api/rules` read-only — CRUD ghi/sửa để Week 3

### Out-of-scope (cấm phình trong tuần này)

- Tạo Alert / Incident / publish `security-alerts` / SSE / JWT
- React SOC, Ollama
- ML online / LLM detect trên Kafka
- Hoàn thiện full pipeline rule-generator (có thể seed tay tạm nếu production chưa đủ; O10 đóng dần)
- Rule CRUD UI; chỉnh weight từ SOC
- Runner đầy đủ ~30 probe (Week 4) — tuần này chỉ fixture test

## 5. Work breakdown

| # | Việc | Ghi chú / artifact |
|---|------|---------------------|
| 1 | Enums + entity `DetectionRule` (+ provenance) + Flyway seed 8–15 | §7.3 |
| 2 | Rule evaluator (regex, field) | Module `rules` / `RuleEngine` |
| 3 | Persist hits | `DetectionHit` |
| 4 | Scorer đúng công thức §7.4 | Module `scoring`; cửa sổ 5 phút theo `client_ip` |
| 5 | Gắn scorer vào pipeline sau normalize | Cùng process consumer |
| 6 | API event detail + hits | `GET /api/events/{id}` |
| 7 | Test dương/âm tối thiểu 3 category | Test Java, không phụ thuộc UI |

## 6. Acceptance Criteria (AC)

| ID | AC |
|----|----|
| AC-1 | Given query chứa payload SQLi trong seed, When ingest, Then có ≥1 `DetectionHit` category `SQLI` kèm `rule_id` và `evidence` (đoạn khớp) |
| AC-2 | Given payload XSS và path traversal (query hoặc path), When ingest, Then hit đúng category `XSS` và `PATH_TRAVERSAL` |
| AC-3 | Given request browse path thường Juice Shop không nằm pattern seed, When ingest, Then **không** có hit và `risk_score = 0` |
| AC-4 | Given một event có tổng weight hits = W, When score, Then `base = min(100, W)` và `score = min(100, base + freq_bonus + status_bonus)` |
| AC-5 | Given &lt; 2 DetectionHit cùng `client_ip` trong 5 phút, When score, Then `freq_bonus = 0` |
| AC-6 | Given ≥ 2 hits cùng IP trong 5 phút, When score event sau, Then `freq_bonus = min(20, 2 * (số_hit_cửa_sổ - 1))` |
| AC-7 | Given `base > 0` và status ∈ {403, 404, 500}, When score, Then `status_bonus = 5`; nếu `base = 0` thì bonus = 0 |
| AC-8 | Given rule `enabled=false`, When evaluate, Then rule đó không tạo hit |
| AC-9 | Given event đã chấm, When `GET /api/events/{id}`, Then thấy `risk_score` và danh sách hits |
| AC-10 | Given Flyway migrate sạch, When đếm rule enabled, Then từ **8** đến **15** và đủ 3 category |

## 7. Definition of Done (DoD)

Tuần **Done** khi **tất cả** điều sau đúng:

- [x] Mọi AC trong §6 pass trên lab local (test tự động + ingest thật: SQLI/XSS/PATH_TRAVERSAL + clean score 0)
- [x] Artifact §8 nằm đúng path (`rules/`, `scoring/`, API detail hits, `GET /api/rules`)
- [x] Công thức score ghi chú trong code/README khớp §7.4
- [x] `DetectionRule` có provenance; seed 14 rule / 3 category
- [x] Không merge Alert/Incident/SSE/UI
- [x] Demo checkpoint §10 chạy được

## 8. Deliverables

| Artifact | Path gợi ý |
|----------|------------|
| Enums + entity + seed | `domain/enums`, `domain/entity/DetectionRule`, Flyway `V*__seed_detection_rules.sql` |
| Rule engine + scorer | `backend/` modules `rules`, `scoring` |
| Hits + score trên API | `GET /api/events/{id}` |
| Test dương/âm | `backend/src/test/` |

## 9. Risks

| Rủi ro | Tác động nếu trễ | Mitigation tuần này |
|--------|------------------|---------------------|
| Regex quá rộng → toàn FP | Week 4/5 bảng FP vỡ | Seed hẹp; AC-3 bắt buộc path sạch = 0 |
| Regex quá hẹp → FN 3 category | O3 không đạt | Mỗi category ≥ 2 rule; test 1 payload “dễ” + 1 biến thể |
| Freq window implement sai (đếm event thay vì hit) | Score lệch thiết kế | Test AC-5/AC-6 với fixture thời gian |
| Làm luôn Alert “cho nhanh” | Week 3 mất ranh giới, review rối | Cấm persist bảng Alert tuần này |

## 10. Demo checkpoint

1. Compose đang chạy (từ Week 1)
2. Curl 1 URL sạch → event score 0, không hit
3. Curl 1 URL SQLi trên query (qua Nginx) → hits SQLI + score &gt; 0
4. `GET /api/events/{id}` show evidence
5. (Tuỳ chọn) 2–3 probe cùng IP liên tiếp → score tăng `freq_bonus`

Chưa cần UI. Chưa cần incident.

## 11. Handoff

- **Input cho tuần sau:** `risk_score`, hits, `Application.risk_threshold`; pipeline sau score là chỗ cắm Alert
- **Nợ kỹ thuật chấp nhận được:** chưa JWT; chưa CRUD rule ghi; chưa dataset 50+30
- **Blocker phải giải trước tuần sau:** score không tái lập được / seed rule không cover 3 category
