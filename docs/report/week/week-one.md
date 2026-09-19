# Week 1 — Báo cáo tóm tắt đề xuất

| Trường | Giá trị |
|--------|---------|
| Nguồn | `docs/solution-design.md` v1.6 · `docs/rule-creation.md` v1.1 |
| Trạng thái design | **Approved for implementation** |
| Mục tuần 1 | Chốt design + outline báo cáo (NIST) + schema Flyway |

---

## Đề tài

**Xây dựng hệ thống giám sát và phát hiện tấn công web dựa trên phân tích access log (WAF Log Analyzer), tích hợp pipeline realtime Kafka và hỗ trợ giải thích cảnh báo bằng LLM**

Định vị: **WAF Log Analyzer / Web Attack Detection Platform** — không phải WAF inline blocking trên reverse proxy. Hệ thống thu thập access log, phát hiện pattern tấn công bằng rule engine tự viết, tạo **Alert → Incident**, dashboard gần realtime (SSE), và dùng LLM theo **hai vai trò tách biệt** (offline rule mining + online incident explain).

---

## Bài toán cần giải quyết

Access log Nginx có giá trị phát hiện tấn công web (SQLi, XSS, Path Traversal) nhưng thường chỉ được lưu, thiếu pipeline realtime, chuẩn hoá, scoring, triage và nguồn rule có đánh giá. Đề tài nhằm biến log thành **cảnh báo và incident có thể hành động** trên lab local, phục vụ học quản trị an ninh theo **NIST CSF** (ISO 27001 phụ lục).

---

## Từ khóa đề tài

Web attack monitoring, access log analysis, WAF Log Analyzer, Kafka, rule engine, **AI-assisted rule mining**, risk scoring, **Incident promotion**, SOC dashboard, SSE, **LLM incident explanation**, Ollama, NIST CSF.

---

## Đóng góp chính

1. **Pipeline end-to-end (runtime):** Juice Shop → Nginx (JSON access log) → Filebeat → Kafka (`raw-web-logs`) → Spring Boot (normalize → detect → score → alert → **incident**) → PostgreSQL → React SOC (**SSE** alert + incident).
2. **Lab offline rule mining:** module `detection-rule-generator/` — corpora → normalize/dedupe/cluster → AI gen/review/optimize → compiler → regex `DetectionRule` → evaluator → `rules/production/` → Flyway seed / admin import.
3. **Rule engine + scoring tự viết** trên runtime; **LLM không thay detector**.
4. **Incident phân cấp + tổng hợp** (1 incident : N alerts); triage trên Incident.
5. Map hệ thống vào **NIST CSF** (Detect / Respond chính).

---

## Điểm khác biệt

| Khía cạnh | Cách làm |
|-----------|----------|
| Tách tầng | Workload (Juice Shop + Nginx + Filebeat) **reuse OSS**; security backend + rule-generator **tự viết** |
| Không fork SIEM/WAF nặng | Không clone ELK đầy đủ; không dùng ModSecurity làm detector chính |
| LLM phạm vi hẹp, hai vai trò | **Offline:** AI hỗ trợ sinh rule từ corpora. **Online:** Ollama chỉ **giải thích Incident** (async), grounded bằng rule hits |
| Alert ≠ Incident | Alert 1-1 với event; Incident chỉ mở khi đủ policy promote — giảm noise cho SOC |

---

## Yêu cầu kỹ thuật cần đáp ứng (outcome O1–O11)

| # | Outcome | Tiêu chí chính |
|---|---------|----------------|
| O1 | Ingest realtime | Log → Kafka `raw-web-logs` **&lt; 5 giây** |
| O2 | Normalize | Schema `WebEvent` thống nhất; `host` → `app_id` |
| O3 | Detect | SQLi / XSS / Path Traversal + evidence; rule từ rule-generator đã eval |
| O4 | Score | 0–100; alert khi `score ≥ threshold` (mặc định **60**); cửa sổ freq **5 phút** |
| O5 | Scope | Juice Shop theo `app_id` / `host` (DVWA = phase 2) |
| O6 | Dashboard | Timeline, top attack/IP/path; SSE alert + incident |
| O7 | Demo dataset | Compose + Attack Scenario Runner (`datasets/probes`) |
| O8 | Đánh giá 2 tầng | (A) corpus metrics rule-generator; (B) FP/FN thô ~50 clean + ~30 probe |
| O9 | Incident + LLM | Promote IMMEDIATE/AGGREGATE; Ollama explain **async** khi Incident mới tạo |
| O10 | Rule mining | Pipeline offline chạy được → export khớp `DetectionRule` |
| O11 | Báo cáo | Kỹ thuật + NIST CSF + hạn chế hệ thống |

**Ngoài MVP:** WAF inline, ML/LLM zero-day detector online, cloud LLM cho demo explain, triage LLM / weekly narrative, DVWA multi-app, export PDF.

---

## Phạm vi triển khai (MVP đã khoá)

- Juice Shop sau Nginx JSON log; Kafka (`raw-web-logs`, `security-alerts`); Spring Boot 3 (Java 25); PostgreSQL + Flyway; React + Vite + SSE; JWT admin.
- **8–15 production rule** (SQLI / XSS / PATH_TRAVERSAL) từ `detection-rule-generator` + human review; admin CRUD rule.
- Incident: `OPEN` / `ACK` / `CLOSED`; quan hệ **1 : N** alerts.
- Ollama local (profile `llm`) — explain async; rule-generator **không** bắt buộc trong Compose.
- Lộ trình gốc 6–8 tuần (design chốt tuần 1); kế hoạch vận hành nén **5 tuần** trong `docs/weeks/`.

---

## Phương án đề xuất

### A. Runtime (critical path — không gọi LLM detect)

```text
Ingest Kafka → normalize (Spring in-process) → rule engine (regex)
  → scoring (base + freq_bonus 5p + status_bonus)
  → Alert 1-1 khi score ≥ threshold → SSE alert
  → Incident Manager (policy §7.5) → SSE incident
  → (async) Ollama explain chỉ khi Incident mới tạo
```

**Scoring (chốt):**  
`base = min(100, sum(hit.weight))`; `freq_bonus ≤ 20`; `status_bonus = 5` nếu status ∈ {403,404,500} và base > 0; `score = min(100, base + …)`.

**Severity gợi ý:** LOW &lt;40 · MEDIUM 40–69 · HIGH 70–84 · CRITICAL ≥85 (alert vẫn chỉ khi vượt threshold app).

### B. Incident promotion (không còn 1 alert = 1 incident)

| Nhánh | Điều kiện | Hành vi |
|-------|-----------|---------|
| **IMMEDIATE** | severity = CRITICAL | Tạo / gắn Incident ngay theo key `(app_id, client_ip)` trong cửa sổ **W = 5 phút** |
| **AGGREGATE** | MEDIUM / HIGH | Gom theo cùng key; đủ **K = 3** alert chưa gắn → tạo Incident |
| Chưa promote | AGGREGATE, count &lt; K | Chỉ Alert; **chưa** gọi LLM |

Triage SOC trên **Incident**. Gắn thêm alert vào Incident đã có: cập nhật `alert_count` / max severity-score; **không** auto re-explain (retry bằng API).

### C. AI tạo rule — offline laboratory (`detection-rule-generator/`)

```text
Attack corpora (HttpParams, FuzzDB, SQLi ~30k, …)
  → normalize / dedupe / cluster / split (held-out ẩn với AI)
  → AI: gen abstract rule → review → optimize
  → compiler → DetectionRule JSON (regex Java-compatible)
  → evaluator (held-out + benign + adversarial)
  → human gate → rules/production/
  → Flyway seed / POST /api/rules → Spring runtime
```

- Mục tiêu: coverage cao, FPR thấp, rule tổng quát (không overfit 1 payload).
- AI **không** nằm trên Kafka consumer / detect path.
- Provenance rule: `source = AI_MINED | HAND`, `generator_rule_id`, `rule_version`.
- Chi tiết: `docs/rule-creation.md`.

### D. LLM online — chỉ explain Incident

Analyst / hệ thống enqueue sau khi Incident **được tạo**: Spring WebClient → Ollama (local), input = tóm tắt Incident + tối đa 5 alert (event + hits đã sanitize). UI ghi rõ *AI-assisted*; lỗi Ollama → `FAILED` / `SKIPPED`, không chặn detect/SSE.

---

## Kiến trúc tóm tắt

**Runtime:** Juice Shop → Nginx → Filebeat → Kafka → Spring (ingest → rules → score → alert → incident → SSE) → PostgreSQL / React SOC.

**Offline:** `detection-rule-generator/` → `rules/production/` → seed runtime.

Frontend **không** đọc Kafka; nhận alert/incident qua SSE (`/api/stream/alerts`, `/api/stream/incidents`).

---

## Đánh giá và triển khai

| Tầng | Cách đo |
|------|---------|
| A — Rule mining | Precision / recall / FPR / coverage trên held-out + benign + adversarial trước khi seed |
| B — Lab Juice | FP/FN thô trên ~50 request sạch + ~30 probe qua Nginx |
| Latency | Request → alert SSE **&lt; 5 giây** (O1 + demo) |

**MVP đạt** khi demo **8–10 phút** đủ: traffic sạch → probe → detect → alert → **incident promote** → (async) explain → triage; kèm báo cáo NIST.

**Demo flow chuẩn:** mở Juice → chạy probes → alert trên dashboard → incident khi đủ IMMEDIATE/AGGREGATE → drill-down alerts + hits → chờ/xem LLM explain → stats theo `app_id`.

---

## Khung chính sách (báo cáo môn học)

- **NIST CSF** là khung chính — map module vào Detect (DE) và Respond (RS).
- ISO 27001 đối chiếu phụ lục.
- Nhấn: logging/monitoring có kiểm soát, JWT bảo vệ SOC, không expose Kafka ra ngoài host, sanitize prompt LLM.

---

## Lộ trình (tham chiếu)

| Gốc design (§12) | Kế hoạch 5 tuần (`docs/weeks/`) |
|------------------|----------------------------------|
| Tuần 1 chốt design | (done) — báo cáo này |
| Compose + ingest | Week 01 |
| Rule + scoring (+ rule-generator seed) | Week 02 |
| Alert + Incident + SSE + JWT | Week 03 |
| SOC UI + probes + Ollama explain | Week 04 |
| FP/FN + báo cáo NIST + slide | Week 05 |

---

## Kết luận tuần 1

Đã chốt scope, kiến trúc, stack, scoring, **Incident policy**, **hai vai trò LLM** (AI rule mining offline + incident explain online), và tiêu chí O1–O11. Tuần tiếp theo tập trung hiện thực lab Compose và đường ingest/normalize theo `docs/weeks/week-01.md`.
