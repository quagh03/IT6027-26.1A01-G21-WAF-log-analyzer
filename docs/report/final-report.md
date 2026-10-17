# Báo cáo nộp — WAF Log Analyzer

| Trường | Giá trị |
|--------|---------|
| Môn | IT6027 · nhóm G21 · lớp 26.1A01 |
| Tiêu đề | Xây dựng hệ thống giám sát và phát hiện tấn công web dựa trên phân tích access log |
| Nguồn | `docs/solution-design.md` v1.6 · eval Week 5 · `metrics.md` |
| Ngày đo | 2026-10-11 |

---

## 1. Tóm tắt

Hệ thống là **log analyzer** (không chặn inline): Juice Shop → Nginx JSON access log → Filebeat → Kafka → Spring (normalize, 14 regex rule, score, alert, incident) → SSE → React SOC. LLM chỉ **explain async** (hoặc `SKIPPED` khi tắt profile `llm`). Rule được mine offline trong `detection-rule-generator/`, không nằm trên critical path.

Tuần 5 khóa đánh giá O8 (hai tầng + Benchmark ngoài), chính sách NIST CSF, latency, slide và README reproduce.

---

## 2. Kiến trúc kỹ thuật (OWASP trên access log)

### 2.1 Tầm nhìn detector

Chỉ thấy field access log: `path`, `query`, `ua`, `raw` (= path + query). **Không** đọc POST body → payload chỉ trong body là FN theo thiết kế.

### 2.2 Signature vs anomaly

Runtime dùng signature/regex + weight (8–15 rule, seed 14). Không unsupervised ML, không LLM detect online. Trade-off: precision cao trên corpora sạch, coverage phụ thuộc pattern đã mine; zero-day ngoài bộ chữ ký sẽ miss.

### 2.3 Workflow Detect → Triage

```text
request → log → Kafka → detect/score
  → Alert (score ≥ 60)
  → Incident (CRITICAL ngay, hoặc MEDIUM/HIGH đủ K=3 trong W=5 phút)
  → (async) Explain hoặc SKIPPED
  → Analyst ACK / CLOSED trên Incident
```

Alert fatigue giảm nhờ ngưỡng 60 và promote phân cấp (không 1 alert = 1 incident).

---

## 3. Đánh giá O8

### 3.1 Tầng A — rule-generator (offline)

Nguồn: `detection-rule-generator/evaluator/reports/metrics.md`.

| Metric | Giá trị |
|--------|---------|
| Rule pass cổng | **14** (precision ≥ 0,80 · FPR ≤ 0,02) |
| SQLI held-out coverage | 50,52% |
| XSS held-out coverage | 68,12% |
| PATH_TRAVERSAL held-out coverage | 48,00% |
| Benign FPR (ba category) | 0 |

### 3.2 Tầng B — Juice Shop lab

Nguồn: `datasets/eval/juice-lab.md` · ground truth 50 browse + 37 probe (31 `alert`, 6 `clean`).

| Tập | TP | FP | FN | TN | Precision | Recall | FPR |
|-----|---:|---:|---:|---:|----------:|-------:|----:|
| A browse (sạch) | 0 | 0 | 0 | 50 | — | — | 0 |
| B probe | 24 | 0 | 7 | 6 | 1,000 | 0,774 | 0 |
| Gộp | 24 | 0 | 7 | 56 | 1,000 | 0,774 | 0 |

Coverage probe `expected=alert`:

| Category | TP | FN | Recall |
|----------|---:|---:|-------:|
| SQLI | 5 | 6 | 0,455 |
| XSS | 9 | 0 | 1,000 |
| PATH_TRAVERSAL | 10 | 0 | 1,000 |

FN chính: một số TIME SQLi (`sleep` / `pg_sleep` / `benchmark`) và tổ hợp không đạt ngưỡng 60 sau normalize log. **FP = 0** trên browse + probe sạch.

### 3.3 OWASP Benchmark v1.2 (external eval)

Nguồn: `datasets/eval/owasp-benchmark/owasp-benchmark.md`. Lab **đã `make reset`** trước khi đo. Không đưa payload Benchmark vào mine rule; không retune sau đo.

| Bucket | Số |
|--------|---:|
| log_visible (query/param) vào mẫu số | 780 |
| header/cookie (không trên access log) | 329 |
| unknown_vector | 118 |
| out_of_scope | 1513 |

| | Có alert | Không alert |
|---|---:|---:|
| Nhãn `true` | TP=467 | FN=0 |
| Nhãn `false` | FP=313 | TN=0 |

**TPR = 1,000 · FPR = 1,000 · score = TPR − FPR = 0,000**

Giải thích: nhãn Benchmark = *code có lỗ hổng?*; detector = *request có pattern tấn công?*. Replay cùng payload tấn công cho mọi case log-visible → bắt hết attempt (TPR=1) và cũng “bắt” case `false` (FPR=1). Hai tầng A/B/Benchmark **không trộn một mẫu số**.

---

## 4. Latency (O1/O6)

Nguồn: `datasets/eval/latency.md`.

| Đo | Giá trị |
|----|---------|
| Request → Alert (API poll, lab sạch runtime) | **≈ 11,5–15 s** |
| Mục tiêu §11.2 | < 5 s |
| Kết luận | **Trượt** — bottleneck Filebeat→Kafka→consumer, không phải scoring |

---

## 5. NIST CSF (khung chính) — Detect & Respond

| Function | Category (rút gọn) | Module lab |
|----------|-------------------|------------|
| **DE** | DE.CM Continuous monitoring | Nginx JSON log, Filebeat, Kafka `raw-web-logs`, ingest |
| **DE** | DE.AE Anomalies and events | Rule engine, risk score, Alert |
| **DE** | DE.DP Detection processes | Seed rule đã eval, threshold 60, admin CRUD |
| **RS** | RS.AN Analysis | Incident drill-down (alerts/hits/score), SOC UI |
| **RS** | RS.MI Mitigation | Triage ACK/CLOSED trên Incident (lab) |
| **RS** | RS.CO Communication | SSE realtime; LLM explain async (AI-assisted) |

---

## 6. Phụ lục ISO 27001 (ngắn)

| Control (ví dụ) | Ánh xạ lab |
|-----------------|------------|
| A.8.15 Logging | Nginx JSON access log bắt buộc qua reverse proxy |
| A.8.16 Monitoring | Pipeline Kafka + dashboard SSE |
| A.5.28 Collection of evidence | Persist `web_events` / `detection_hits` / `alerts` / `incidents` |
| A.5.24 Incident management planning | Policy promote IMMEDIATE/AGGREGATE + RACI |

---

## 7. Policy lab (đề xuất)

### 7.1 Logging & monitoring

Mọi traffic demo đi qua Nginx `juice.lab.local` / `shop.lab.local`. Filebeat chỉ ship access log JSON; không detection tại edge.

### 7.2 Retention

Đề xuất lab: **30 ngày** cho event/alert/incident trong PostgreSQL; log volume Docker xoay theo `max-size` Compose.

### 7.3 Escalation

```text
Detect (alert) → Incident (promote) → Explain async (nếu llm)
  → Analyst ACK → nếu CRITICAL và chưa xử lý: escalate (owner infra / giảng viên lab)
```

### 7.4 RACI (1 trang)

| Hoạt động | Sinh viên vận hành | Analyst (demo) | Owner rule | Giảng viên |
|-----------|:-----------------:|:--------------:|:----------:|:----------:|
| `compose up` / reset volume | R/A | C | I | I |
| Chạy probe / Benchmark eval | R | C | I | I |
| Triage Incident ACK/CLOSED | C | R/A | I | I |
| Bật/tắt rule, sửa weight | C | I | R/A | I |
| Chấm BTL / ethics | I | I | I | A |

---

## 8. Threat model (tóm tắt)

| Tài sản | Đe dọa | Control |
|---------|--------|---------|
| SOC API | Đoán credential / JWT giả | JWT + role; chỉ lab password seed |
| Kafka | Expose ra ngoài host | Bind `127.0.0.1`; mạng Docker nội bộ |
| Ollama prompt | Injection / lộ PII | Sanitize field; cắt N alert; không detect bằng LLM |
| Lab ethics | Probe nhầm host ngoài | README + runner chỉ `127.0.0.1` + Host lab |

---

## 9. Hạn chế & hướng phát triển

- Access log thiếu POST body; cookie/header không trong schema → blind spot (Benchmark header/cookie loại khỏi mẫu số).
- Signature không thay anomaly; Benchmark score 0 phản ánh lệch nhãn “vuln code” vs “attack attempt”.
- Latency E2E trượt mục tiêu 5 s trên lab đo được — cần tinh chỉnh Filebeat/consumer (phase 2).
- Phase 2 đã khoá: DVWA, ModSec, FP-loop, cloud LLM explain, correlation giàu hơn.

---

## 10. Ethics

Chỉ probe OWASP Juice Shop **tự host** trong Compose local. Không quét hệ thống bên thứ ba.

---

## 11. Reproduce nhanh

```bash
make reset                 # xóa volume Postgres/Redis/nginx-logs
make stack-ui              # http://127.0.0.1:3001  admin/admin
./datasets/probes/browse_clean.sh
./datasets/probes/run.sh
python3 datasets/eval/score_juice.py
# OWASP Benchmark (tải upstream — xem datasets/eval/owasp-benchmark/README.md)
python3 datasets/eval/owasp-benchmark/run_eval.py
# LLM tắt: incident explanation_status=SKIPPED
# LLM bật: make stack-llm
```
