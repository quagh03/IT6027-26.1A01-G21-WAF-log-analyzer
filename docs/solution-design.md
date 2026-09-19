# Solution Design — Web Attack Monitoring & Detection Platform (WAF Log Analyzer)

> **Source of truth** cho bài tập lớn môn *Chính sách & Quản trị An ninh mạng*.  
> Mọi quyết định scope, kiến trúc, stack, outcome lấy từ file này. Cập nhật file khi đổi hướng.

| Trường | Giá trị |
|--------|---------|
| Phiên bản | `1.6` |
| Ngày | 2026-09-18 |
| Trạng thái | **Approved for implementation** (đã chốt quyết định) |
| Tác giả | (điền tên sinh viên) |

---

## 1. Tóm tắt đề tài

### 1.1 Tiêu đề (final)

**Xây dựng hệ thống giám sát và phát hiện tấn công web dựa trên phân tích access log (WAF Log Analyzer), tích hợp pipeline realtime Kafka và hỗ trợ giải thích cảnh báo bằng LLM**

### 1.2 Định vị sản phẩm

Hệ thống **không** phải WAF inline blocking trên reverse proxy. Đây là **WAF Log Analyzer / Web Attack Detection Platform**:

1. Thu thập access log từ Nginx (phía trước ứng dụng web lab)
2. Đẩy log realtime qua Kafka
3. Spring backend chuẩn hoá → phát hiện pattern tấn công bằng rule engine tự viết → chấm điểm bất thường
4. Scope theo domain/ứng dụng, cảnh báo → **tạo Incident**, dashboard gần realtime (SSE)
5. LLM **bắt buộc trong deliverable**, **hai vai trò tách biệt**:
   - **Offline (rule mining):** AI hỗ trợ sinh/review detection rule từ attack corpora trong `detection-rule-generator/` — **không** nằm trên critical path runtime
   - **Online (incident explain):** Ollama giải thích Incident **bất đồng bộ** sau khi Incident được tạo — **không** thay rule engine

Đóng góp chính của BTL nằm ở **tầng security backend** (detection, scoring, alert, incident, dashboard, LLM explain async, báo cáo chính sách) và **lab offline rule mining** (dataset → AI → rule → eval → seed runtime). Tầng workload (UI app + Nginx + Filebeat) **reuse open-source / tự compose**.

### 1.3 Vai trò các tầng

| Tầng | Vai trò | Ai làm |
|------|---------|--------|
| Workload | OWASP Juice Shop — sinh traffic HTTP thật | Reuse OSS |
| Edge logging | Nginx reverse proxy ghi access log JSON | Tự config trong Compose |
| Shipper | Filebeat tail log → Kafka | Adapt mẫu SIEM-in-a-box |
| Bus | Apache Kafka (KRaft single-node) | Infra Docker |
| **Rule mining (offline)** | Dataset → normalize/cluster → AI gen rule → eval → export `DetectionRule` | **Tự viết (`detection-rule-generator/`)** |
| **Security backend** | Ingest, normalize, detect (load compiled rules), score, alert, incident, API, LLM explain async | **Tự viết (Spring Boot 3)** |
| **SOC UI** | Dashboard, incident triage | **Tự viết (React + Vite)** |

---

## 2. Mục tiêu & outcome

### 2.1 Mục tiêu học thuật / kỹ thuật

- Xây pipeline giám sát web attack từ access log theo hướng detection engineering
- Xây lab offline **AI-assisted rule mining** (coverage cao, FP thấp) rồi đưa rule đã evaluate vào runtime
- Áp dụng kiến thức security (OWASP, logging, FP/FN, triage) vào báo cáo môn quản trị
- Map hệ thống vào khung chính sách: **NIST CSF là khung chính**, ISO 27001 là phụ lục/đối chiếu
- Demo được end-to-end trên lab local (Docker Compose)

### 2.2 Outcome đo được (definition of done)

| # | Outcome | Tiêu chí chấp nhận |
|---|---------|-------------------|
| O1 | Ingest realtime | Log Nginx xuất hiện trên Kafka topic `raw-web-logs` trong **&lt; 5 giây** sau request |
| O2 | Normalize | Mọi event map vào schema `WebEvent` thống nhất (method, path, query, status, UA, IP, time, host, app_id) — normalize **in-process** trong Spring |
| O3 | Detect | Rule engine phát hiện tối thiểu **SQLi, XSS, Path Traversal** với `rule_id` + evidence; rule production lấy từ `detection-rule-generator` (đã eval + human review) |
| O4 | Score | Mỗi **request** có risk score 0–100; alert khi `score >= 60` (threshold theo app, mặc định 60) |
| O5 | Scope | Lọc/cảnh báo theo `app_id` / `host` trên **Juice Shop** (MVP). Multi-app DVWA = phase 2 |
| O6 | Dashboard | Timeline, top attack type, top IP, top path; realtime qua **SSE** (alert + incident) |
| O7 | Demo dataset | Compose + **Attack Scenario Runner** (`datasets/probes`) + traffic browse sạch; reproduce được |
| O8 | Đánh giá | **Hai tầng:** (A) corpus metrics từ rule-generator (precision/recall/FPR/coverage trên held-out); (B) FP/FN thô trên Juice probes (~50 clean + ~30 probe) |
| O9 | Incident + LLM | **Promote có phân cấp** (immediate + aggregate); LLM explain **async** khi Incident được tạo (profile `llm`); UI ghi rõ AI-assisted |
| O10 | Rule mining | Pipeline offline trong `detection-rule-generator/` chạy được: raw → cluster → AI candidate rules → eval → `rules/production/` export khớp schema `DetectionRule` |
| O11 | Báo cáo môn học | Chương kỹ thuật + chương chính sách (NIST CSF chính) + hạn chế hệ thống |

### 2.3 Demo flow chuẩn (8–10 phút)

1. Mở Juice Shop qua Nginx → tạo traffic bình thường  
2. Chạy script probe (SQLi/XSS/path traversal) trong lab  
3. Filebeat đẩy log → Kafka → Spring detect → **alert** lên dashboard; **incident** xuất hiện khi đủ điều kiện promote  
4. Drill-down incident → danh sách alert liên quan + rule hits + score  
5. Chờ (hoặc xem trạng thái) **LLM explain async** cập nhật lên incident (không chặn bước 3)  
6. Xem thống kê theo `app_id` / host trên dashboard (không bắt buộc export PDF trong MVP)

---

## 3. Scope

### 3.1 In-scope (MVP) — đã khoá

- Parse **Nginx JSON access log** only
- Canonical event model + persistence (**PostgreSQL + Flyway**)
- Rule engine tự viết (regex/signature + weight); **8–15 production rule** (categories: SQLI, XSS, PATH_TRAVERSAL) lấy từ output đã evaluate của `detection-rule-generator` + **human review**, seed qua Flyway (có thể bổ sung tay nếu thiếu category)
- **AI-assisted rule mining (offline)** trong `detection-rule-generator/`: corpora → normalize/dedupe/cluster → AI gen abstract rule → compiler → regex `DetectionRule` → eval (held-out + benign + adversarial)
- Risk scoring: rule weight + frequency window **N = 5 phút**
- Alert lifecycle: vẫn **1 alert gắn 1 event**; alert có thể **chưa** thuộc incident (`incident_id` nullable)
- **Incident (phân cấp + tổng hợp):** không còn 1 alert = 1 incident. Promote theo policy §7.5; triage trên Incident (`OPEN` / `ACK` / `CLOSED`); quan hệ **1 incident : N alerts**
- Application scoping theo `host` → `app_id` (có thể dùng thêm host giả lập để demo multi-scope trên 1 app)
- Rule seed qua Flyway; **admin rule CRUD API** nằm trong MVP (bật/tắt, sửa weight); tuỳ chọn import JSON từ `detection-rule-generator/rules/production/`
- REST API + **React** SOC dashboard
- Realtime: **SSE** (`GET /api/stream/alerts`, `GET /api/stream/incidents`)
- Auth SOC: **JWT** (1 role `admin` trong MVP)
- Kafka topics: **`raw-web-logs`** + **`security-alerts`**
- LLM **online**: **Ollama local**, use case runtime = **incident explain**; gọi **bất đồng bộ** sau khi tạo Incident (không block alert/SSE); có API retry thủ công
- LLM **offline**: dùng trong rule-generator (Cursor/local LLM) để gen/review/optimize rule — **không** gọi trong Kafka consumer / detect path
- Docker Compose chạy toàn bộ lab (không bắt buộc Kafka UI / không bắt buộc Ollama luôn bật nếu máy yếu — có profile). Rule-generator **không** bắt buộc chạy trong Compose (tooling offline)
- **Attack Scenario Runner** trong `datasets/probes` (scenarios có nhãn + script chạy tự động qua Nginx)
- Tài liệu: SOLUTION_DESIGN + `docs/rule-creation.md` + README + báo cáo PDF + slide

### 3.2 Out-of-scope / trì hoãn — đã khoá

| Hạng mục | Quyết định |
|----------|------------|
| Inline WAF blocking (ModSecurity/Coraza làm detector chính) | Out |
| So sánh ModSecurity DetectionOnly | **Không làm** (tránh phình) |
| Parse Apache combined | **Không làm** trong BTL |
| Full packet capture / network IDS | Out |
| Unsupervised ML / zero-day detector / LLM làm detector online | Out — AI chỉ **sinh rule offline**; runtime vẫn signature/regex |
| Multi-tenant SaaS | Out |
| Deep inspection response body / TLS MITM | Out |
| DVWA app #2 | **Phase 2** (sau MVP) |
| Topic `normalized-web-events` | **Không dùng** — normalize in-process |
| Session/IP correlation phức tạp (device fingerprint, multi-hop) | Ngoài MVP |
| Incident correlation vượt ngoài key `(app_id, client_ip)` + cửa sổ thời gian | Ngoài MVP (giữ rule §7.5) |
| Analyst False Positive mark + auto-adjust weight | Ngoài MVP |
| Export PDF/CSV báo cáo tuần | Ngoài MVP |
| LLM triage assistant + weekly narrative | Ngoài MVP (chỉ explain async trên Incident) |
| Cloud LLM cho **demo explain** (OpenAI/Gemini) | Không dùng cho demo chính (Ollama). Cloud LLM **được phép** cho bước offline rule mining nếu cần (không nằm runtime) |
| OWASP Benchmark làm eval bắt buộc MVP | **Phase 2** — MVP dùng held-out + benign + adversarial trong rule-generator |
| Thymeleaf FE / WebSocket | Không chọn |
| Liquibase | Không chọn (dùng Flyway) |

### 3.3 Phase 2 (sau khi MVP ổn)

- Thêm DVWA + multi-app thật
- Correlation giàu hơn (cùng path pattern / category / UA cluster)
- FP feedback loop
- Weekly narrative LLM + export báo cáo
- OWASP Benchmark / eval corpus mở rộng cho rule-generator
- (Tuỳ chọn) Kafka UI cố định trong compose

### 3.4 Quyết định đã chốt (single source)

| Quyết định | Lựa chọn chốt |
|------------|----------------|
| Realtime bus | Apache Kafka (KRaft single-node) |
| Security backend | Spring Boot 3, **Java 25** |
| Tầng workload | **Tự compose** Juice Shop + Nginx JSON log |
| Log shipper | Filebeat → Kafka theo mẫu **SIEM-in-a-box** |
| Không clone nguyên elk-lab | Chỉ tham khảo ý tưởng; tự kiểm soát Compose |
| FE | **React + Vite** (+ Recharts hoặc Chart.js) |
| Realtime UI | **SSE** |
| Auth | **JWT**, role `admin` |
| Migration | **Flyway** |
| LLM online | **Bắt buộc**, **Ollama**, **incident explain async** (không block detect/alert) |
| LLM offline | **AI-assisted rule mining** trong `detection-rule-generator/` (Cursor/local LLM); không trên detect path |
| Nguồn production rules | Output đã eval + human review từ rule-generator → Flyway seed (8–15 rule); CRUD admin vẫn có |
| Incident | **Phân cấp + tổng hợp:** CRITICAL promote ngay; MEDIUM/HIGH gom theo `(app_id, client_ip)` + cửa sổ; **1 incident : N alerts**; triage trên Incident |
| Kafka topics | `raw-web-logs`, `security-alerts` |
| Normalize | In-process trong Spring |
| Scoring | Per-request; threshold mặc định **60**; cửa sổ freq **5 phút** |
| ModSec so sánh | Không |
| Khung báo cáo quản trị | **NIST CSF chính**, ISO 27001 phụ |
| Repo | **Monorepo** như §14 |

---

## 4. Kiến trúc tổng thể

### 4.1 Sơ đồ luồng dữ liệu

```text
┌─────────────────────────────────┐
│  OWASP Juice Shop (target app)  │
└────────────────┬────────────────┘
                 ▼
      ┌─────────────────────┐
      │  Nginx reverse      │
      │  proxy + JSON       │
      │  access.log         │
      └──────────┬──────────┘
                 ▼
      ┌─────────────────────┐
      │  Filebeat           │
      │  → Kafka            │
      └──────────┬──────────┘
                 ▼
      ┌─────────────────────┐
      │  Kafka              │
      │  raw-web-logs       │
      └──────────┬──────────┘
                 ▼
┌────────────────────────────────────────────────────────────┐
│              Spring Security Backend (core BTL)            │
│  Ingest → Normalize (in-process) → Rules → Scorer          │
│       → Alert Manager → Incident Manager → persist PG      │
│       → publish → SSE fan-out                              │
│       → (async) LLM Explain (Ollama) → update Incident     │
└───────────────┬─────────────────────────────┬──────────────┘
                │                             │
                ▼                             ▼
     ┌──────────────────┐          ┌─────────────────────┐
     │  PostgreSQL      │          │  Kafka              │
     │  events/alerts/  │          │  security-alerts    │
     │  incidents/rules │          │                     │
     └──────────────────┘          └──────────┬──────────┘
                                              ▼
                                   ┌─────────────────────┐
                                   │  Spring SSE bridge  │
                                   │  → React SOC UI     │
                                   └─────────────────────┘
```

Nguồn rule (offline — xem `docs/rule-creation.md`):

```text
detection-rule-generator/
  raw corpora → normalize → cluster → AI rule builder
       → abstract rule → compiler → DetectionRule JSON
       → evaluator (held-out / benign / adversarial)
       → rules/production/
              │
              ▼
     Flyway seed / admin import → Spring rule engine (runtime)
```

Luồng Alert → Incident + LLM (MVP):

```text
score ≥ threshold
  → tạo Alert (1-1 event) + SSE alert ngay
  → Incident Manager đánh giá policy §7.5:

      [IMMEDIATE] severity = CRITICAL
          → nếu có Incident OPEN cùng key (app_id, client_ip) trong cửa sổ W
                gắn alert vào incident đó (cập nhật severity/score/alert_count)
          → ngược lại tạo Incident mới + gắn alert
          → enqueue LLM async (lần đầu tạo; hoặc khi CRITICAL mới gắn — xem §9)

      [AGGREGATE] severity ∈ {MEDIUM, HIGH}
          → nếu có Incident OPEN cùng key trong W: gắn alert vào incident (không bắt buộc re-explain)
          → nếu chưa có: đếm alert chưa gắn incident cùng key trong W
                nếu count ≥ K (=3): tạo Incident + gắn các alert đó + enqueue LLM async
                nếu count < K: chỉ giữ Alert (chưa thành Incident)

  → SSE incident khi tạo mới / cập nhật gắn alert
  → LLM (profile llm) chạy async, không block detect/alert
```

### 4.2 Kafka topics (chốt)

| Topic | Producer | Consumer | Nội dung |
|-------|----------|----------|----------|
| `raw-web-logs` | Filebeat | Spring Ingest | Dòng log Nginx JSON (+ metadata Filebeat) |
| `security-alerts` | Spring Alert/Incident Manager | Spring SSE bridge (cùng app hoặc listener) | Alert JSON; có thể kèm `incident_id` nếu đã promote |

Frontend **không** đọc Kafka. React nhận alert/incident qua **SSE** từ Spring.

### 4.3 Thành phần Spring (logical modules)

| Module | Trách nhiệm |
|--------|-------------|
| `ingest` | Kafka consumer `raw-web-logs`, validate; offset Kafka là nguồn tiến độ (không dedupe hash phức tạp) |
| `normalize` | Parse Nginx JSON → `WebEvent`; map `host` → `app_id` |
| `rules` | Load rule từ DB (seed từ rule-generator + CRUD), evaluate regex theo `target_field` |
| `scoring` | `base + freq_bonus(5m) + status_bonus` → score 0–100 |
| `alert` | Nếu score ≥ threshold app → tạo alert 1-1 với event, publish/SSE |
| `incident` | Policy promote §7.5 (immediate/aggregate); gắn N alerts; lifecycle triage; trigger LLM khi Incident mới |
| `api` | REST events/alerts/incidents/apps/rules/stats |
| `realtime` | SSE fan-out alert + incident |
| `llm` | Async explain khi Incident được **tạo**; `POST .../incidents/{id}/explain` để retry (profile `llm`) — **không** gen rule |
| `security` | Spring Security + JWT |

> Module **`detection-rule-generator/`** nằm ngoài Spring process: tooling Python/script offline; chi tiết pipeline trong `docs/rule-creation.md`.

---

## 5. Tầng trên (reuse) — cách triển khai đã chọn

### 5.1 Chiến lược (chốt)

> **Tự compose** Juice Shop + Nginx (JSON access log) + Filebeat output Kafka, cấu hình Filebeat adapt theo mẫu **SIEM-in-a-box**.  
> **Không** clone nguyên `elk-lab` làm nền (tránh kéo ELK/detection sẵn). Có thể đọc tham khảo cấu hình Nginx của elk-lab nếu cần.

### 5.2 Nguồn tham chiếu

| Thành phần | Project / nguồn | URL | Cách dùng |
|------------|-----------------|-----|-----------|
| Target app | OWASP Juice Shop | https://owasp.org/www-project-juice-shop/ | App MVP |
| Filebeat → Kafka | SIEM-in-a-box | https://github.com/ieeta-pt/SIEM-in-a-box | Adapt collector nginx |
| Tham khảo Nginx+Juice | elk-lab | https://github.com/luisantoniio1998/elk-lab | Tham khảo config only |
| Tham khảo Spring+Kafka | Watch-Tower | https://github.com/chethanhrx/Watch-Tower | Reference kiến trúc — **không fork** |
| DVWA | digininja/DVWA | https://github.com/digininja/DVWA | **Phase 2 only** |

### 5.3 Công việc cụ thể tầng trên

1. Juice Shop sau Nginx  
2. Nginx access log **JSON** ra shared volume  
3. Filebeat → `kafka:9092`, topic `raw-web-logs`  
4. Metadata: `host`, `log_type=access`; `app_id` resolve ở Spring từ `host`  
5. Không đặt detection logic trong Filebeat/Nginx  
6. Có **Attack Scenario Runner** (§5.5) trong `datasets/` để sinh log tấn công có kiểm soát  

### 5.4 Nginx JSON log — schema chốt

Dùng các field sau (tên cố định trong config Nginx):

| Field | Ý nghĩa |
|-------|---------|
| `time_iso` | Timestamp ISO8601 |
| `remote_addr` | Client IP |
| `host` | Host header / server_name |
| `request_method` | Method |
| `uri` | Path không gồm query |
| `args` | Query string (không gồm `?`) |
| `status` | HTTP status |
| `body_bytes_sent` | Bytes |
| `http_user_agent` | UA |
| `http_referer` | Referer |
| `request_time` | Latency upstream/request |

**Map `app_id` (MVP):** bảng `Application` map `host` pattern → `app_id`.  
Để demo multi-scope trước phase 2: cấu hình thêm 1 server_name giả (ví dụ `juice.lab.local` và `shop.lab.local`) cùng upstream Juice Shop.

### 5.5 Attack Scenario Runner (upstream — chốt)

Juice Shop **không** tự sinh kịch bản tấn công. Lab cần **runner** phía client (qua Nginx) để tạo access log có nhãn, phục vụ demo + đo FP/FN.

**Chiến lược MVP (đã chọn):** tự viết script trong monorepo — **không** phụ thuộc Metasploit / scanner nặng.

| Thành phần | Vai trò |
|------------|---------|
| `datasets/probes/scenarios.yaml` (hoặc JSON) | Danh sách kịch bản: `id`, `category` (`SQLI`\|`XSS`\|`PATH_TRAVERSAL`), `method`, `path`, `query`, `expected` (`alert`\|`clean`), `note` |
| `datasets/probes/run.sh` hoặc `run.py` | Đọc scenarios → gọi HTTP tới host lab (qua Nginx), có delay nhẹ, log kết quả chạy |
| `datasets/probes/browse_clean.sh` | Traffic “sạch” (~50 request browse path phổ biến Juice Shop) |
| `datasets/ground_truth.csv` | Nhãn kỳ vọng để đối chiếu detector |

**Yêu cầu runner:**

1. Chỉ nhắm URL lab local (Juice Shop qua Nginx) — ghi rõ trong README ethics  
2. Cover đủ 3 category detect + một phần request sạch  
3. Payload nằm trên **query/path/UA** (đúng tầm nhìn access log; không phụ thuộc POST body)  
4. Có thể chạy một lệnh sau khi Compose up: `./datasets/probes/run.sh`  
5. Tuỳ chọn: dùng **k6** nếu cần lặp/tải nhẹ; mặc định **curl/bash hoặc Python requests** là đủ  

**Không chọn làm runner chính MVP:** OWASP ZAP full scan, Nuclei mass template, sqlmap tự động — dễ phình scope, khó gắn ground truth từng request, và có thể sinh quá nhiều noise.

**Luồng demo:**

```text
Compose up → browse_clean (tuỳ chọn)
           → run.sh (probe scenarios)
           → Nginx JSON log → Filebeat → Kafka → Spring detect
           → Alert / Incident trên SOC UI
```

---

## 6. Công nghệ & stack

### 6.1 Stack chính (chốt)

| Lớp | Công nghệ |
|-----|-----------|
| Language / Framework | **Java 25**, Spring Boot 3 |
| Messaging | Apache Kafka (KRaft single-node) |
| DB | PostgreSQL 16 + **Flyway** |
| Security (SOC) | Spring Security + **JWT** |
| Realtime | **SSE** |
| Frontend | **React + Vite** + Recharts/Chart.js |
| Log shipper | Filebeat |
| Edge | Nginx |
| Target app (MVP) | OWASP Juice Shop |
| LLM | **Ollama** (local), feature profile `llm` |
| Runtime | Docker + Docker Compose |
| Repo | Monorepo |

### 6.2 Compose services

```text
juice-shop
nginx
filebeat
kafka                 # KRaft single-node
postgres
spring-security-backend
soc-dashboard         # React (container riêng)
ollama                # profile: llm (bật khi demo explain)
```

Kafka UI: **không** nằm trong compose mặc định (tránh nặng máy chấm bài).

### 6.3 Dependency Spring chính

- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-kafka`
- `spring-boot-starter-security` (+ JWT)
- `spring-boot-starter-validation`
- PostgreSQL driver
- Flyway
- WebClient (gọi Ollama)

---

## 7. Mô hình dữ liệu & scoring (chốt)

### 7.1 Entities MVP

**Application** — `id`, `name`, `host_pattern`, `risk_threshold` (default 60), `enabled`, `created_at`

**WebEvent** — `id`, `app_id`, `event_time`, `client_ip`, `method`, `path`, `query`, `status`, `user_agent`, `referer`, `raw_ref`, `host`, `risk_score`, `created_at`

**DetectionRule** — `id`, `code`, `name`, `category` (`SQLI`|`XSS`|`PATH_TRAVERSAL`), `pattern`, `target_field` (`path`|`query`|`ua`|`raw`), `weight`, `enabled`, `description`, `source` (`HAND`|`AI_MINED`, default `AI_MINED` khi import từ generator), `generator_rule_id` (nullable), `rule_version` (nullable)

**DetectionHit** — `id`, `event_id`, `rule_id`, `evidence`, `weight`, `created_at`

**Alert** — `id`, `app_id`, `event_id` (**NOT NULL**, quan hệ 1-1 event), `incident_id` (**nullable** — null = chưa promote), `client_ip`, `severity`, `score`, `title`, `summary`, `status` (`OPEN`|`ACK`|`CLOSED`), `created_at`, `updated_at`

**Incident** — `id`, `app_id`, `client_ip` (correlation key), `severity` (max của alerts thành viên), `score` (max), `alert_count`, `title`, `status` (`OPEN`|`ACK`|`CLOSED`), `promote_reason` (`IMMEDIATE`|`AGGREGATE`), `explanation` (nullable), `explanation_confidence` (nullable: `LOW`|`MEDIUM`|`HIGH`), `explanation_status` (`PENDING`|`READY`|`FAILED`|`SKIPPED`), `explanation_error` (nullable), `explained_at` (nullable), `opened_at`, `created_at`, `updated_at`

> Quan hệ: **1 Incident : N Alerts**. SOC triage trên **Incident**. Alert luôn persist; chỉ một phần alert đủ điều kiện mới thành / gắn vào Incident.

### 7.2 Rule categories MVP

Chỉ 3 category: `SQLI`, `XSS`, `PATH_TRAVERSAL`.  
Production seed **8–15 rule** qua Flyway, ưu tiên lấy từ `detection-rule-generator/rules/production/` (đã pass eval + human review). CRUD API để bật/tắt và sửa weight.  
Rule chỉ nhắm field nhìn thấy trên **access log**: `path` / `query` / `ua` / `raw` — **không** phụ thuộc POST body (khớp §5.5).

Chi tiết pipeline mining, abstract rule, compiler và metric: **`docs/rule-creation.md`**.

### 7.2.1 Hợp đồng export từ rule-generator → Spring

Mỗi file JSON trong `rules/production/` (hoặc bản gộp) phải map 1-1 sang entity `DetectionRule` runtime:

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

`category` **bắt buộc** dùng enum runtime (`SQLI`|`XSS`|`PATH_TRAVERSAL`), không dùng tên dài kiểu `SQL_INJECTION`.

### 7.3 Java class design (chốt — backend)

Outcome của `detection-rule-generator` **không** dừng ở JSON: artifact `rules/production/*.json` map 1-1 sang **JPA entity / enum / DTO** trong Spring. Section này là SoT cho package Java MVP.

#### 7.3.1 Package layout

```text
com.huylq.it6027.backend
├── domain
│   ├── entity          # JPA entities §7.1
│   └── enums           # RuleCategory, TargetField, RuleSource, …
├── rules
│   ├── RuleEngine
│   ├── RuleEvaluator
│   ├── DetectionRuleRepository
│   └── dto             # RuleCreateRequest, RuleUpdateRequest, RuleResponse, RuleImportItem
├── scoring
├── ingest / normalize / alert / incident / api / realtime / llm / security
└── persistence         # Flyway dưới resources/db/migration
```

#### 7.3.2 Enums (Java)

```java
public enum RuleCategory { SQLI, XSS, PATH_TRAVERSAL }

public enum TargetField { PATH, QUERY, UA, RAW }
// JSON/DB: path|query|ua|raw — map lowercase khi serialize (@JsonValue / AttributeConverter)

public enum RuleSource { HAND, AI_MINED }

public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

public enum TriageStatus { OPEN, ACK, CLOSED }   // Alert + Incident

public enum PromoteReason { IMMEDIATE, AGGREGATE }

public enum ExplanationStatus { PENDING, READY, FAILED, SKIPPED }

public enum ExplanationConfidence { LOW, MEDIUM, HIGH }
```

#### 7.3.3 Entity `DetectionRule` (consumer của rule-generator)

```java
@Entity
@Table(name = "detection_rules",
       uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class DetectionRule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;                 // e.g. SQLI-BOOLEAN-001

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pattern;              // Java regex

    @Enumerated(EnumType.STRING)
    @Column(name = "target_field", nullable = false, length = 16)
    private TargetField targetField;

    @Column(nullable = false)
    private int weight;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RuleSource source = RuleSource.AI_MINED;

    @Column(name = "generator_rule_id", length = 64)
    private String generatorRuleId;      // nullable; id bên lab offline

    @Column(name = "rule_version", length = 32)
    private String ruleVersion;          // nullable; semver từ generator

    // getters/setters hoặc Lombok — tuỳ convention repo
}
```

**Map JSON production → entity:**

| JSON field | Java field | Ghi chú |
|------------|------------|---------|
| `code` | `code` | Unique; dùng làm idempotent import key |
| `name` | `name` | |
| `category` | `RuleCategory` | `SQLI`/`XSS`/`PATH_TRAVERSAL` |
| `pattern` | `pattern` | Compile `Pattern.compile` khi evaluate (cache) |
| `target_field` | `TargetField` | `query` → `QUERY`, … |
| `weight` | `weight` | |
| `enabled` | `enabled` | |
| `description` | `description` | |
| `source` | `RuleSource` | mặc định `AI_MINED` khi import từ generator |
| `generator_rule_id` | `generatorRuleId` | thường = `code` lúc gen |
| `rule_version` | `ruleVersion` | |

Metadata lab (`coverage`, `confidence`, `metrics`, abstract `conditions`) **không** persist vào entity runtime — chỉ nằm trong `detection-rule-generator/`.

#### 7.3.4 Các entity còn lại (tóm tắt class)

```java
@Entity
public class Application {
    Long id;
    String name;
    String hostPattern;
    int riskThreshold = 60;
    boolean enabled;
    Instant createdAt;
}

@Entity
public class WebEvent {
    Long id;
    Long appId;
    Instant eventTime;
    String clientIp;
    String method;
    String path;
    String query;
    int status;
    String userAgent;
    String referer;
    String rawRef;
    String host;
    int riskScore;
    Instant createdAt;
}

@Entity
public class DetectionHit {
    Long id;
    Long eventId;
    Long ruleId;
    String evidence;          // đoạn khớp
    int weight;               // copy từ rule lúc hit
    Instant createdAt;
}

@Entity
public class Alert {
    Long id;
    Long appId;
    Long eventId;             // NOT NULL, 1-1 event
    Long incidentId;          // nullable
    String clientIp;
    Severity severity;
    int score;
    String title;
    String summary;
    TriageStatus status;
    Instant createdAt;
    Instant updatedAt;
}

@Entity
public class Incident {
    Long id;
    Long appId;
    String clientIp;
    Severity severity;
    int score;
    int alertCount;
    String title;
    TriageStatus status;
    PromoteReason promoteReason;
    String explanation;
    ExplanationConfidence explanationConfidence;
    ExplanationStatus explanationStatus;
    String explanationError;
    Instant explainedAt;
    Instant openedAt;
    Instant createdAt;
    Instant updatedAt;
}
```

Quan hệ JPA gợi ý MVP: FK cột `*_id` đủ dùng; `@ManyToOne` optional. **1 Incident : N Alert**; **1 WebEvent : 1 Alert** (khi có alert); **1 WebEvent : N DetectionHit**.

#### 7.3.5 DTO & service quanh rule (import từ generator)

```java
/** Body POST /api/rules hoặc item trong import batch */
public record RuleCreateRequest(
    String code,
    String name,
    RuleCategory category,
    String pattern,
    TargetField targetField,
    int weight,
    boolean enabled,
    String description,
    RuleSource source,
    String generatorRuleId,
    String ruleVersion
) {}

public record RuleUpdateRequest(
    String name,
    String pattern,
    TargetField targetField,
    Integer weight,
    Boolean enabled,
    String description
) {}

public record RuleResponse(
    Long id,
    String code,
    String name,
    RuleCategory category,
    String pattern,
    TargetField targetField,
    int weight,
    boolean enabled,
    String description,
    RuleSource source,
    String generatorRuleId,
    String ruleVersion
) {}

public interface DetectionRuleRepository extends JpaRepository<DetectionRule, Long> {
    List<DetectionRule> findByEnabledTrue();
    Optional<DetectionRule> findByCode(String code);
}

/** Evaluate enabled rules against one WebEvent → DetectionHit drafts */
public interface RuleEngine {
    List<DetectionHit> evaluate(WebEvent event, List<DetectionRule> rules);
}

/**
 * Upsert theo code từ rules/production JSON (dev tooling hoặc admin import).
 * Không chạy trên Kafka hot path.
 */
public interface RuleImportService {
    int importFromProductionItems(List<RuleCreateRequest> items);
}
```

#### 7.3.6 Flyway — cột provenance

Bảng `detection_rules` MVP phải có tối thiểu:

```sql
code, name, category, pattern, target_field, weight, enabled, description,
source, generator_rule_id, rule_version
```

Seed ưu tiên sinh từ `detection-rule-generator/rules/production/` (script export → `V*__seed_detection_rules.sql`). Rule bổ sung tay: `source = 'HAND'`.

### 7.4 Scoring (chốt số)

```text
N = 5 phút
threshold_default = 60

base = min(100, sum(hit.weight))
freq_bonus = min(20, 2 * (số DetectionHit cùng client_ip trong N phút - 1))
             # nếu < 2 hits trong cửa sổ thì freq_bonus = 0
status_bonus = 5 nếu status ∈ {403, 404, 500} AND base > 0; else 0
score = min(100, base + freq_bonus + status_bonus)
```

Alert khi `score >= application.risk_threshold`.

Severity gợi ý: `LOW` (&lt;40 lưu event thôi), `MEDIUM` (40–69), `HIGH` (70–84), `CRITICAL` (≥85) — alert vẫn chỉ tạo khi vượt threshold app.

### 7.5 Incident promotion policy (chốt)

```text
W = 5 phút                          # cửa sổ correlation incident
K = 3                               # ngưỡng đếm cho nhánh AGGREGATE
correlation_key = (app_id, client_ip)
chỉ xét Incident.status = OPEN trong cửa sổ W
```

| Nhánh | Điều kiện alert | Hành vi |
|-------|-----------------|--------|
| **IMMEDIATE** | `severity = CRITICAL` | 1 alert đủ tạo Incident (hoặc gắn vào Incident OPEN cùng key). Promote ngay. |
| **AGGREGATE** | `severity ∈ {MEDIUM, HIGH}` | Nếu đã có Incident OPEN cùng key → gắn thêm. Nếu chưa: khi số alert **chưa gắn incident** cùng key trong W **≥ K** → tạo Incident và gắn các alert đó. |
| **Chưa promote** | AGGREGATE nhưng `count < K` | Chỉ hiển thị Alert; chưa có Incident / chưa gọi LLM. |

Số liệu chốt MVP: `W = 5 phút`, `K = 3`. Có thể cấu hình trong `application.yml` (không cần UI admin trong MVP).

Khi gắn thêm alert vào Incident đã có: cập nhật `alert_count`, `severity = max`, `score = max`. **Không** auto re-call LLM (tránh spam Ollama); analyst dùng API retry nếu cần.

---

## 8. API bề mặt (MVP)

| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/oauth/tokens` | Đổi credential → JWT |
| GET | `/api/events` | Query events (filter app, time, score) |
| GET | `/api/events/{id}` | Chi tiết + hits |
| GET | `/api/alerts` | Danh sách alert |
| GET | `/api/alerts/{id}` | Chi tiết alert |
| GET | `/api/incidents` | Danh sách incident (filter status, app, explanation_status) |
| GET | `/api/incidents/{id}` | Chi tiết incident + **danh sách alerts** + events/hits + explanation |
| PATCH | `/api/incidents/{id}` | Đổi status ACK/CLOSED (triage) |
| GET | `/api/apps` | Danh sách application scope |
| GET/POST/PATCH | `/api/rules` | Quản trị rule (admin JWT) |
| GET | `/api/stats/overview` | KPI dashboard |
| GET | `/api/stream/alerts` | **SSE** realtime alert (kể cả alert chưa thành incident) |
| GET | `/api/stream/incidents` | **SSE** realtime incident (+ cập nhật explanation / gắn alert) |
| POST | `/api/incidents/{id}/explain` | Retry LLM explain (async; profile `llm`) |

> Triage chính trên Incident. Alert feed vẫn cần để demo alert chưa đủ K / chưa promote.

---

## 9. LLM extension (hai vai trò — đã khoá)

### 9.0 Phân tách bắt buộc

| Vai trò | Khi nào | Mục đích | Có detect online? |
|---------|---------|----------|-------------------|
| **Offline rule mining** | Dev/lab, trong `detection-rule-generator/` | Gen / review / optimize rule từ corpora | **Không** — chỉ tạo artifact rule |
| **Online incident explain** | Runtime, sau khi Incident **mới tạo** | Giải thích tiếng Việt cho SOC | **Không** — chỉ explain |

Hai vai trò **không** gộp chung process. Runtime Spring **không** gọi LLM để quyết định hit/miss.

### 9.1 Use case MVP online (bắt buộc)

**Incident explanation (async)** — chỉ khi Incident **được tạo mới** (IMMEDIATE hoặc AGGREGATE đủ K), hệ thống **tự enqueue** job gọi Ollama (không block ingest/alert/SSE).

- Input: tóm tắt Incident + tối đa **N=5** alert mới nhất (mỗi alert: WebEvent + DetectionHit[] đã sanitize)  
- Output: giải thích tiếng Việt + mức tin cậy định tính → ghi vào Incident  
- Trạng thái: `PENDING` → `READY` | `FAILED`; nếu tắt profile `llm` → `SKIPPED`  
- Gắn thêm alert vào Incident đã có: **không** auto re-explain  
- API `POST /api/incidents/{id}/explain` chỉ dùng để **retry** (cũng async)

### 9.2 Use case MVP offline (bắt buộc deliverable tooling)

**AI-assisted rule mining** — xem `docs/rule-creation.md`. Output cuối phải compile được thành `DetectionRule` (§7.2.1) và pass eval trước khi seed.

### 9.3 Ngoài MVP

- Triage assistant  
- Weekly report narrative  
- Auto re-explain mỗi lần gắn alert mới  
- Correlation ngoài `(app_id, client_ip)` + cửa sổ W  
- LLM làm detector trực tiếp trên Kafka stream  

### 9.4 Ràng buộc (online explain)

- LLM **không** detect và **không** nằm trên đường critical path tạo alert/incident  
- Timeout + fallback: lỗi Ollama → `FAILED`, alert/incident vẫn xem được trên UI  
- Sanitize/cắt field trước khi gửi Ollama  
- UI: *AI-assisted, may be inaccurate*; hiển thị `explanation_status`  
- Spring profile `llm`; tắt được khi không có Ollama  
- Implementation gợi ý: `@Async` / application event sau commit transaction (tránh gọi LLM trong Kafka consumer thread)

---

## 10. Security knowledge & báo cáo quản trị

### 10.1 Nội dung kỹ thuật cần viết

- OWASP Top 10 (nhấn Injection, XSS) theo hướng **detection trên log**  
- Hạn chế access log (thiếu POST body)  
- Signature vs anomaly; FP/FN; alert fatigue  
- **Detection engineering:** AI-assisted rule mining offline → seed runtime (không train classifier online)  
- Workflow Detect → Alert → Incident → (async) Explain → Triage → Report  

### 10.2 Chính sách & quản trị (chốt khung)

| Ưu tiên | Khung | Cách dùng trong báo cáo |
|---------|-------|-------------------------|
| Chính | **NIST CSF** | Map chi tiết control **Detect (DE)** và **Respond (RS)** vào module hệ thống |
| Phụ | **ISO 27001** | Bảng đối chiếu ngắn control logging/monitoring (phụ lục) |

**Policy artifacts bắt buộc có trong báo cáo (ở mức đề xuất lab):**

1. Logging & monitoring policy (ngắn)  
2. Retention đề xuất lab: **30 ngày** event/alert trong DB  
3. Escalation matrix đơn giản (detect → incident → async explain → analyst ACK → escalate nếu CRITICAL)  
4. RACI 1 trang (ai vận hành Compose, ai triage, ai chỉnh rule)

**KPI:** MTTD (đo trong demo nếu được), số alert/phiên demo, % FP trên bộ test, coverage 3 category.

### 10.3 Threat model

Mô tả tự do theo tài sản–đe doạ–control (không bắt buộc bảng STRIDE đầy đủ). Nhấn: bảo vệ SOC JWT, không expose Kafka ra ngoài host, sanitize LLM prompt.

### 10.4 Lab ethics

Chỉ probe trên Juice Shop (và DVWA khi phase 2) tự host.

### 10.5 Ngôn ngữ

Báo cáo **tiếng Việt**; thuật ngữ kỹ thuật giữ Anh khi cần. UI SOC: tiếng Việt ưu tiên.

---

## 11. Đánh giá hiệu quả

### 11.1 Bộ test — hai tầng

**Tầng A — Rule quality (offline, `detection-rule-generator`):**

- Held-out attack payloads (không đưa vào prompt gen rule)
- Benign HTTP parameter values (đo FPR)
- Adversarial variants (encoding / case / whitespace)
- Metric: Precision, Recall, F1, FPR, Rule Coverage theo category  
- Chi tiết: `docs/rule-creation.md`

**Tầng B — Runtime E2E (Juice Shop lab):**

- Tập A: ~50 request bình thường (browse Juice Shop)  
- Tập B: ~30 request probe qua **script curl/k6** (SQLi/XSS/path traversal) trong lab  
- Gắn nhãn ground truth trước khi chạy detector (file trong `datasets/`)  
- Báo cáo: TP / FP / FN thô trên lab

### 11.2 Metrics vận hành

- E2E latency mục tiêu: **request → alert SSE &lt; 5 giây**  
- Số rule enabled / 3 categories (ưu tiên 8–15 production)  
- Số alert theo `app_id` trong phiên demo  
- Provenance: tỷ lệ rule `AI_MINED` vs `HAND` trong seed

### 11.3 Dataset nộp

- Compose + Attack Scenario Runner + ground truth trong `datasets/`  
- Corpora + báo cáo eval rule-generator trong `detection-rule-generator/` (không phụ thuộc recording tay)  
- Không bắt buộc ship full upstream corpora nếu license hạn chế — ghi rõ nguồn + script tải trong README module

---

## 12. Kế hoạch triển khai (6–8 tuần)

| Tuần | Việc | Deliverable |
|------|------|-------------|
| 1 | Chốt design (done), outline báo cáo NIST, schema Flyway | SOLUTION_DESIGN v1.6 + rule-creation + Java class §7.3 |
| 2 | Compose: Juice + Nginx JSON + Filebeat → Kafka | `raw-web-logs` có data |
| 3 | Spring ingest + normalize + persist; skeleton `datasets/probes` | API events + runner khung |
| 4 | Rule-generator MVP (normalize/cluster/AI/eval) + compile → seed; Spring rule engine + scoring | Hits + score + `rules/production/` |
| 5 | Alert + Incident + JWT + publish `security-alerts` + SSE | Alert/Incident realtime |
| 6 | React SOC + hoàn thiện Attack Scenario Runner + ground truth | Demo O1–O8, O10 |
| 7 | Ollama explain **async** + chương quản trị NIST | O9 + draft báo cáo |
| 8 | Đo FP/FN (lab + corpus), polish, slide, buffer | Nộp BTL |

---

## 13. Deliverables nộp môn

1. Monorepo + `docker-compose.yml` (+ profile `llm`)  
2. README chạy lab / probe / xem incident / explain async / chạy rule-generator  
3. Config Nginx + Filebeat  
4. `detection-rule-generator/` + báo cáo eval rule (precision/recall/coverage)  
5. Báo cáo PDF (kỹ thuật + NIST CSF + ISO phụ lục + đánh giá)  
6. Slide 8–10 phút  
7. `docs/solution-design.md` + `docs/rule-creation.md`  

---

## 14. Cấu trúc repo (chốt monorepo)

```text
IT6027-26.1A01-G21-WAF-log-analyzer/
├── README.md
├── docker-compose.yml
├── infra/
│   ├── nginx/
│   ├── filebeat/
│   └── kafka/
├── backend/                         # Spring Boot security platform
├── frontend/                        # React SOC dashboard
├── detection-rule-generator/        # Offline AI-assisted rule mining lab
│   ├── data/                        # raw / normalized / clustered / evaluation
│   ├── pipeline/
│   ├── ai/                          # prompts + outputs
│   ├── rules/                       # candidates / reviewed / production
│   └── evaluator/
├── datasets/                        # ground truth + Attack Scenario Runner + sample logs
│   ├── probes/                      # scenarios.yaml + run.sh + browse_clean.sh
│   └── ground_truth.csv
└── docs/
    ├── solution-design.md           # Source of truth kiến trúc runtime
    ├── rule-creation.md             # Spec offline rule mining + export contract
    ├── report/
    └── slides/
```

---

## 15. Rủi ro & mitigations

| Rủi ro | Mitigation |
|--------|------------|
| Scope phình | Đã khoá §3.2; ModSec/DVWA/PDF/FP-loop = không MVP; OWASP Benchmark = Phase 2 |
| Máy chấm bài yếu | Ollama theo profile `llm`; không Kafka UI mặc định; rule-generator chạy offline khi cần |
| Parse log brittle | Nginx JSON schema cố định §5.4 |
| Alert noise | Threshold 60; Incident chỉ mở khi CRITICAL hoặc đủ K=3; ACK trên Incident |
| Rule overfit payload | Held-out + adversarial eval; human review trước seed; coverage không chỉ trên generation set |
| LLM chậm / sập | Gọi **async** khi Incident mới tạo; timeout; `FAILED`/`SKIPPED`; không block SSE |
| LLM bịa (explain) | Ground bằng rule hits của nhiều alert trong Incident; chỉ explain |
| LLM gen rule kém | Evaluator bắt buộc; không seed candidate chưa pass metric; CRUD tắt rule xấu |
| Trùng đồ án | Không fork Watch-Tower |

---

## 16. Open questions — đã đóng

| Câu hỏi | Quyết định |
|---------|------------|
| FE React hay Thymeleaf? | **React + Vite** |
| SSE hay WebSocket? | **SSE** |
| Auth JWT hay session? | **JWT** (role admin) |
| LLM Ollama hay cloud? | **Ollama** cho explain runtime; cloud LLM **tuỳ chọn** cho offline rule mining |
| Nguồn seed rules? | **AI-mined** từ `detection-rule-generator` + human review → Flyway (8–15); CRUD tay vẫn có |
| Eval rule bằng gì? | MVP: held-out + benign + adversarial; Juice probes E2E; OWASP Benchmark = Phase 2 |
| DVWA trong MVP? | **Phase 2** |
| ModSec so sánh? | **Không** |
| elk-lab clone hay tự compose? | **Tự compose** + Filebeat theo SIEM-in-a-box |
| Topics Kafka? | `raw-web-logs` + `security-alerts`; normalize in-process |
| Flyway hay Liquibase? | **Flyway** |
| Threshold / N? | **60** / **5 phút** |
| Khung quản trị? | **NIST CSF chính**, ISO phụ |
| Apache / FP mark / PDF export? | **Ngoài MVP** |
| Java version? | **Java 25** (khớp §3.4 / §6.1) |
| Alert 1-1 hay aggregate? | **1 alert : 1 event** (detection); Incident thì **1 : N alerts** |
| Incident? | **Phân cấp:** CRITICAL → IMMEDIATE; MEDIUM/HIGH → AGGREGATE (K=3, W=5p, key=`app_id+client_ip`) |
| LLM sync hay async? | **Async** khi Incident **mới tạo**; API explain = retry |

Còn lại chỉ là metadata hành chính (tên SV, deadline môn, có làm nhóm không) — không chặn implementation.

---

## 17. Changelog

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-09-12 | Khởi tạo draft: scope, Kafka, Spring, reuse tầng trên, LLM extension |
| 1.1 | 2026-09-12 | **Chốt quyết định:** tự compose Juice+Nginx; React+SSE+JWT; Flyway; topics raw+alerts; Ollama explain bắt buộc; DVWA/ModSec/Apache/PDF/FP-loop ngoài MVP; scoring 60/5p; java 25; NIST CSF chính; đóng open questions |
| 1.2 | 2026-09-12 | **Thêm Incident**; LLM explain **async**; API triage/explain trên Incident; SSE incidents |
| 1.3 | 2026-09-12 | **Sửa Incident:** bỏ 1–1; phân cấp IMMEDIATE (CRITICAL) + AGGREGATE (MEDIUM/HIGH, K=3, W=5p, key app_id+IP); quan hệ 1 incident : N alerts; LLM chỉ khi Incident mới tạo |
| 1.4 | 2026-09-12 | **Chốt Attack Scenario Runner** ở upstream: `datasets/probes` (scenarios có nhãn + script tự chạy qua Nginx); không dùng ZAP/Nuclei/sqlmap làm runner chính MVP |
| 1.5 | 2026-09-15 | **Gắn `detection-rule-generator`:** AI-assisted rule mining offline → compile `DetectionRule` → Flyway seed; tách LLM offline (mine) vs online (explain); eval 2 tầng; schema provenance; monorepo + `docs/rule-creation.md`; sửa mâu thuẫn Java 17→25 trong open questions |
| 1.6 | 2026-09-18 | **Java class design §7.3:** package/enum/entity/DTO/service; `DetectionRule` + provenance map từ JSON production; đánh số lại Scoring §7.4, Incident §7.5 |
