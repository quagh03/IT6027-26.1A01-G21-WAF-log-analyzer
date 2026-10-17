# Cheatsheet bảo vệ — WAF Log Analyzer (IT6027 · G21)

> Đọc trước khi lên: 1 trang kiến trúc + số liệu + câu hỏi khó.  
> Báo cáo: `docs/report/final-report.pdf` · Slide bảo vệ: `docs/slides/defense-presentation.html`  
> Reproduce: `README.md`

---

## 30 giây — đề tài là gì?

**Bài toán:** Giám sát và phát hiện tấn công web (SQLi, XSS, Path Traversal) từ **Nginx access log**, phục vụ SOC lab — **không chặn inline** như WAF.

**Giải pháp:** Pipeline log → Kafka → Spring (14 regex rule + score + Alert + Incident) → React dashboard (SSE realtime). LLM chỉ **giải thích** sự cố (async), **không** tham gia phát hiện.

**Một câu khác biệt:** Đây là *attack-attempt detector trên access log*, không phải scanner lỗ hổng mã nguồn, cũng không phải ModSecurity CRS đặt trước app.

---

## Elevator pitch (45–60 giây)

> Hệ thống thu access log JSON của Nginx trước OWASP Juice Shop, đẩy qua Filebeat/Kafka vào Spring Boot. Backend chuẩn hóa event, áp 14 chữ ký đã mine offline, chấm điểm, tạo Alert khi ≥ 60, rồi tương quan thành Incident (CRITICAL ngay, hoặc MEDIUM/HIGH đủ 3 alert / 5 phút). Analyst xem dashboard realtime qua SSE, triage ACK/CLOSED. Đánh giá tách 3 tầng: rule offline, Juice Shop (recall 77,4%, FP=0), và OWASP Benchmark (score 0 do lệch ontology). Latency request→Alert ~11–15s, chưa đạt mục tiêu <5s.

---

## Kiến trúc 1 dòng

```text
Client/probe
  → Nginx (proxy + JSON access log) → Juice Shop
  → Filebeat → Kafka topic raw-web-logs
  → Spring Boot (ingest → detect → score → Alert → Incident)
  → PostgreSQL (+ Redis cache)
  → React SOC (REST + SSE)
  → (optional) Ollama explain async
  → Kafka security-alerts (tap ngoài; UI không consume)
```

**Network:** Docker `waf-net` · port quản trị bind `127.0.0.1`

| Port | Service |
|-----:|---------|
| 80 | Nginx |
| 3001 | SOC dashboard |
| 8080 | Spring API |
| 5432 / 6379 | Postgres / Redis |
| 29092 / 8088 | Kafka host / Kafka UI |

**Login lab:** `admin/admin` · `analyst/analyst` · `viewer/viewer`

---

## Stack & version (nhớ vài cái)

| Lớp | Công nghệ |
|-----|-----------|
| Host lab | macOS · Docker 27 · Compose 2.31 · Python 3.9 · Node 22 |
| Edge | Nginx 1.27-alpine · Juice Shop v20.1.1 |
| Stream | Filebeat 8.17.4 · Kafka 4.1.2 KRaft |
| Backend | Java 25 · Spring Boot 4.1.1 · Flyway · JWT HS256 |
| Data | PostgreSQL 16 · Redis 7.4 |
| Frontend | React 19 · Vite 6 · Recharts · Nginx static |
| AI (tùy chọn) | Ollama 0.32 · Spring AI · mặc định `qwen3.5:9b` |

---

## Luồng chính (nhắc khi hỏi “hệ thống chạy thế nào?”)

1. Request qua Nginx → ghi JSON log → Filebeat → Kafka  
2. Spring parse + resolve Application theo `Host` → insert `web_events` (`raw_ref` chống trùng)  
3. Match 14 rule → `detection_hits` → `risk_score` = tổng weight  
4. `score ≥ threshold` (mặc định **60**) → tạo **1 Alert / event**  
5. Promote Incident: **CRITICAL (≥85) ngay**; MEDIUM/HIGH nếu đủ **K=3** trong **W=5 phút** cùng `(appId, clientIp)`  
6. After-commit → SSE (và Kafka `security-alerts`)  
7. Analyst triage ACK/CLOSED; explain LLM async hoặc `SKIPPED`

**Severity bands:** LOW `<40` · MEDIUM `≥40` · HIGH `≥70` · CRITICAL `≥85`

---

## Use case — nhớ theo 3 nhóm

| Nhóm | UC chính |
|------|----------|
| SOC UI | UC-01 login JWT · UC-03..07 xem · UC-08 triage · UC-09 explain · UC-11 SSE |
| Pipeline tự động | UC-12 log/Kafka · UC-13 ingest · UC-14 detect · UC-15 Alert · UC-16 Incident · UC-17 fan-out |
| Vận hành/eval | UC-19 mine rule offline · UC-20 Juice · UC-21 Benchmark · UC-22 latency · UC-23 lab up/down |

**RBAC:** VIEWER đọc + SSE · ANALYST + triage/explain · ADMIN + CRUD rule

---

## 14 rule (3 category)

| Family | Ví dụ rule | Weight gợi ý |
|--------|------------|--------------|
| SQLI | BOOLEAN, COMMENT, STACKED, TIME, UNION | 35–60 |
| XSS | ENCODED tag, EVENT handler, JS URI, SCRIPT | 40–55 |
| PATH | Dot-dot / encoded traversal | (seed traversal rules) |

- Rule mine **offline** (`detection-rule-generator/`), vào production qua Flyway/API  
- Runtime: signature regex trên `PATH|QUERY|UA|RAW` — **không** ML, **không** LLM detect  
- **Không thấy POST body / cookie / header** trên schema access log → blind spot theo thiết kế

---

## Threat model nhanh (môn quản trị)

| Tấn công | Cơ chế ngắn | Hệ thống bắt gì? |
|----------|-------------|------------------|
| **SQLi** | Chèn SQL qua input (boolean, UNION, time, stacked) | Pattern trong query/path |
| **XSS** | Chèn script/HTML/event handler | Tag, `onerror`, `javascript:` |
| **Path Traversal** | `../` đọc file ngoài root | Dot-dot / encoded `..%2f` |

**Phòng ngừa đúng chỗ:** parameterized query / encode output / canonicalize path ở **ứng dụng**. Analyzer chỉ **phát hiện attempt**, không vá lỗ hổng.

**NIST CSF:** Detect (log, rule, score) + Respond (Incident, triage, SSE) · Govern/Identify/Protect ở mức lab policy.  
**ISO 27001:** A.8.15 Logging · A.8.16 Monitoring · A.5.24–28 Incident/evidence.

---

## Số liệu đánh giá — học thuộc

### A. Offline ruleset
- **14** rule pass cổng precision ≥ 0,80 · FPR ≤ 0,02  
- Coverage held-out: SQLI **50,5%** · XSS **68,1%** · Path **48,0%**  
- Benign FPR trên corpora = **0** (không chứng minh FPR production = 0)

### B. Juice Shop (kịch bản vận hành chính)
| | TP | FP | FN | TN | Prec | Recall | FPR |
|---|---:|---:|---:|---:|-----:|-------:|----:|
| Gộp | 24 | 0 | 7 | 56 | 1,000 | **0,774** | 0 |

- 87 request: 50 browse sạch + 37 probe (31 cần alert)  
- Recall theo loại (probe): SQLI **0,455** · XSS **1,0** · Path **1,0**  
- FN chính: TIME SQLi / vài tổ hợp dưới ngưỡng 60

### C. OWASP Benchmark v1.2 (external)
| | Giá trị |
|--|--------:|
| Case chấm (log-visible) | 780 / 2740 |
| TP / FP / FN / TN | 467 / 313 / 0 / 0 |
| TPR / FPR / **Score** | 1 / 1 / **0** |

**Vì sao score = 0?**  
Benchmark hỏi: *code có lỗ hổng không?*  
Hệ thống trả lời: *request có dấu hiệu tấn công không?*  
Gửi cùng payload tấn công cho cả case `true` và `false` → alert hết → TPR=FPR=1 → score=0.  
**Không phải** “không phát hiện được gì” — vẫn alert **780/780** attempt.

### D. Latency
- Request → Alert (API): **≈ 11,5–15,1 s**  
- Mục tiêu: **< 5 s** → **trượt** (bottleneck ship/ingest, không phải regex)

---

## Tool bắn traffic hiện có (ngoài Benchmark)

| Tool | Hành vi | Liên tục? |
|------|---------|-----------|
| `datasets/probes/run.sh` | Full probe set (MEDIUM/HIGH/CRITICAL + clean) | **Không** |
| `datasets/probes/run_medium_high.sh` | Chỉ MEDIUM + HIGH (demo severity) | **Không** |
| `datasets/probes/simulate_monitoring.sh` | Random chain các script trên | **Có** — Ctrl+C dừng |
| `python3 run.py --severity MEDIUM` | Lọc theo band | **Không** |
| `datasets/probes/browse_clean.sh` | Traffic browse sạch | **Không** |
| `datasets/eval/score_juice.py` | Gửi + chấm Juice | **Không** |
| `datasets/eval/owasp-benchmark/run_eval.py` | Replay 780 GET | **Không** |
| `datasets/eval/measure_latency.py` | Đo latency SQLi | **Không** |

**Severity probe (nhớ khi demo):** MEDIUM = TIME đơn (60) hoặc UNION+404; HIGH = BOOLEAN+COMMENT (75) / EVENT+COMMENT (80); còn lại ghép rule → CRITICAL.

**Demo monitoring liên tục:** `make simulate-monitoring` hoặc `./datasets/probes/simulate_monitoring.sh` (Ctrl+C để dừng).

---

## Demo live — checklist

```bash
make reset   # hoặc make destroy — dừng + xóa volume
# make down / make stop — chỉ dừng, giữ volume
APP_LLM_ENABLED=false make stack-ui
# Dashboard: http://127.0.0.1:3001  → admin/admin

./datasets/probes/browse_clean.sh          # sạch → ít/không alert
./datasets/probes/run.sh                   # tấn công → Alert/Incident
# hoặc: python3 datasets/eval/score_juice.py

# Hosts (tuỳ chọn): 127.0.0.1 juice.lab.local shop.lab.local
curl -H 'Host: juice.lab.local' 'http://127.0.0.1/?q=1+OR+1=1+UNION+SELECT+null'
```

**Nhìn trên UI:** Events tăng → Alerts CRITICAL → 1 Incident OPEN (cùng IP) → SSE cập nhật · AI = SKIPPED nếu tắt LLM.

---

## Câu hỏi khó — trả lời ngắn

| Hỏi | Trả lời xương sống |
|-----|-------------------|
| Khác WAF/ModSec chỗ nào? | Out-of-band, không block; phát hiện sau log; metric khác allow/block. |
| Sao không dùng ML? | Lab cần giải thích được, precision cao trên corpora sạch; rule có provenance/version. |
| Sao Benchmark = 0? | Lệch ontology vuln-code vs attack-attempt; luôn alert trên fixed payload. |
| FN SQLi TIME? | Một số probe hit nhưng score < 60 hoặc pattern chưa khớp sau normalize log. |
| Latency chậm? | Filebeat→Kafka→consumer; scoring không phải bottleneck chính trên lab đo. |
| LLM có detect không? | **Không.** Chỉ explain; tắt thì `SKIPPED`; không đổi score/verdict. |
| Alert fatigue? | Threshold 60 + promote theo IP/app (K,W) thay vì 1 alert = 1 incident. |
| Ethics? | Chỉ Juice Shop tự host trong Compose; không quét host ngoài. |
| POST body? | Access log không có → ngoài scope; FN theo thiết kế. |
| Production sẵn sàng? | Lab/demo; secret seed, không HA, latency chưa đạt SLA. |

---

## Hạn chế & hướng phát triển (chốt bài)

1. Blind spot: body / header / cookie  
2. Signature ≠ anomaly / zero-day  
3. Latency E2E chưa < 5s  
4. Benchmark không phải điểm “chính thức” OWASP  
5. Phase 2 (đã khóa / tương lai): DVWA, ModSec so sánh, FP-loop, correlation giàu hơn, continuous traffic generator

---

## Reproduce một lệnh

```bash
make help
make reset && make stack-ui     # destroy + up
make down                       # hoặc: make stop
make destroy                    # alias của reset
make simulate-monitoring        # Ctrl+C dừng
python3 datasets/eval/score_juice.py
python3 datasets/eval/owasp-benchmark/run_eval.py
python3 datasets/eval/measure_latency.py
```

**Artifact số liệu:**  
`datasets/eval/juice-lab.md` · `datasets/eval/owasp-benchmark/owasp-benchmark.md` · `datasets/eval/latency.md`

---

## Bản đồ file quan trọng

| Mục đích | Đường dẫn |
|----------|-----------|
| Báo cáo nộp | `docs/report/final-report.tex` / `.pdf` |
| Thiết kế | `docs/solution-design.md` |
| Compose | `docker-compose.yml` |
| Rule seed | `backend/.../V2__seed_detection_rules.sql` |
| Detect pipeline | `backend/src/main/java/...` (DetectionPipeline, RuleEngine, Alert, Incident) |
| SOC UI | `frontend/` |
| Probe / eval | `datasets/probes/` · `datasets/eval/` |
| Mine rule offline | `detection-rule-generator/` |

---

*In 2 trang hoặc mở trên điện thoại khi chờ bảo vệ. Ưu tiên thuộc: kiến trúc 1 dòng · threshold/K/W · Juice 0,774 · Benchmark score 0 · latency 11–15s · LLM không detect.*
