# Solution Design — Web Attack Monitoring & Detection Platform (WAF Log Analyzer)

> **Source of truth** cho bài tập lớn môn *Chính sách & Quản trị An ninh mạng*.  
> Mọi quyết định scope, kiến trúc, stack, outcome lấy từ file này. Cập nhật file khi đổi hướng.

| Trường | Giá trị |
|--------|---------|
| Phiên bản | `1.0` |
| Ngày | 2026-09-12 |
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
4. Scope theo domain/ứng dụng, cảnh báo, dashboard gần realtime (SSE)
5. LLM **bắt buộc trong deliverable**, phạm vi hẹp: **giải thích alert** (không thay rule engine)

Đóng góp chính của BTL nằm ở **tầng security backend** (detection, scoring, alert, dashboard, LLM explain, báo cáo chính sách). Tầng workload (UI app + Nginx + Filebeat) **reuse open-source / tự compose**.

### 1.3 Vai trò các tầng

| Tầng | Vai trò | Ai làm |
|------|---------|--------|
| Workload | OWASP Juice Shop — sinh traffic HTTP thật | Reuse OSS |
| Edge logging | Nginx reverse proxy ghi access log JSON | Tự config trong Compose |
| Shipper | Filebeat tail log → Kafka | Adapt mẫu SIEM-in-a-box |
| Bus | Apache Kafka (KRaft single-node) | Infra Docker |
| **Security backend** | Ingest, normalize, detect, score, alert, API, LLM explain | **Tự viết (Spring Boot 3)** |
| **SOC UI** | Dashboard, alert triage | **Tự viết (React + Vite)** |

---

## 2. Mục tiêu & outcome

### 2.1 Mục tiêu học thuật / kỹ thuật

- Xây pipeline giám sát web attack từ access log theo hướng detection engineering
- Áp dụng kiến thức security (OWASP, logging, FP/FN, triage) vào báo cáo môn quản trị
- Map hệ thống vào khung chính sách: **NIST CSF là khung chính**, ISO 27001 là phụ lục/đối chiếu
- Demo được end-to-end trên lab local (Docker Compose)

### 2.2 Outcome đo được (definition of done)

| # | Outcome | Tiêu chí chấp nhận |
|---|---------|-------------------|
| O1 | Ingest realtime | Log Nginx xuất hiện trên Kafka topic `raw-web-logs` trong **&lt; 5 giây** sau request |
| O2 | Normalize | Mọi event map vào schema `WebEvent` thống nhất (method, path, query, status, UA, IP, time, host, app_id) — normalize **in-process** trong Spring |
| O3 | Detect | Rule engine phát hiện tối thiểu **SQLi, XSS, Path Traversal** với `rule_id` + evidence |
| O4 | Score | Mỗi **request** có risk score 0–100; alert khi `score >= 60` (threshold theo app, mặc định 60) |
| O5 | Scope | Lọc/cảnh báo theo `app_id` / `host` trên **Juice Shop** (MVP). Multi-app DVWA = phase 2 |
| O6 | Dashboard | Timeline, top attack type, top IP, top path; realtime qua **SSE** |
| O7 | Demo dataset | Compose + hướng dẫn reproduce; script probe lab (curl/k6) + traffic browse bình thường |
| O8 | Đánh giá | Bảng FP/FN thô trên bộ test tự định nghĩa (~50 clean + ~30 probe) |
| O9 | LLM | **Alert explanation** bắt buộc; UI ghi rõ AI-assisted |
| O10 | Báo cáo môn học | Chương kỹ thuật + chương chính sách (NIST CSF chính) + hạn chế hệ thống |

### 2.3 Demo flow chuẩn (8–10 phút)

1. Mở Juice Shop qua Nginx → tạo traffic bình thường  
2. Chạy script probe (SQLi/XSS/path traversal) trong lab  
3. Filebeat đẩy log → Kafka → Spring detect → alert xuất hiện trên dashboard (SSE)  
4. Drill-down request + rule hits + score  
5. Bấm **Explain** → LLM giải thích dựa trên rule hits  
6. Xem thống kê theo `app_id` / host trên dashboard (không bắt buộc export PDF trong MVP)

---

## 3. Scope

### 3.1 In-scope (MVP) — đã khoá

- Parse **Nginx JSON access log** only
- Canonical event model + persistence (**PostgreSQL + Flyway**)
- Rule engine tự viết (regex/signature + weight), seed **8–15 rule** (categories: SQLI, XSS, PATH_TRAVERSAL)
- Risk scoring: rule weight + frequency window **N = 5 phút**
- Alert lifecycle: `OPEN` / `ACK` / `CLOSED` — **1 alert gắn 1 event** (không aggregate trong MVP)
- Application scoping theo `host` → `app_id` (có thể dùng thêm host giả lập để demo multi-scope trên 1 app)
- Rule seed qua Flyway; **admin rule CRUD API** nằm trong MVP (bật/tắt, sửa weight)
- REST API + **React** SOC dashboard
- Realtime: **SSE** (`GET /api/stream/alerts`)
- Auth SOC: **JWT** (1 role `admin` trong MVP)
- Kafka topics: **`raw-web-logs`** + **`security-alerts`**
- LLM: **Ollama local**, use case duy nhất MVP = **alert explain**
- Docker Compose chạy toàn bộ lab (không bắt buộc Kafka UI / không bắt buộc Ollama luôn bật nếu máy yếu — có profile)
- Tài liệu: SOLUTION_DESIGN + README + báo cáo PDF + slide

### 3.2 Out-of-scope / trì hoãn — đã khoá

| Hạng mục | Quyết định |
|----------|------------|
| Inline WAF blocking (ModSecurity/Coraza làm detector chính) | Out |
| So sánh ModSecurity DetectionOnly | **Không làm** (tránh phình) |
| Parse Apache combined | **Không làm** trong BTL |
| Full packet capture / network IDS | Out |
| Unsupervised ML / zero-day detector | Out |
| Multi-tenant SaaS | Out |
| Deep inspection response body / TLS MITM | Out |
| DVWA app #2 | **Phase 2** (sau MVP) |
| Topic `normalized-web-events` | **Không dùng** — normalize in-process |
| Session/IP correlation phức tạp | Ngoài MVP (chỉ dùng freq trong 5 phút cho scoring) |
| Analyst False Positive mark + auto-adjust weight | Ngoài MVP |
| Export PDF/CSV báo cáo tuần | Ngoài MVP |
| LLM triage assistant + weekly narrative | Ngoài MVP (chỉ explain) |
| Cloud LLM (OpenAI/Gemini) | Không dùng cho demo chính (Ollama) |
| Thymeleaf FE / WebSocket | Không chọn |
| Liquibase | Không chọn (dùng Flyway) |

### 3.3 Phase 2 (sau khi MVP ổn)

- Thêm DVWA + multi-app thật
- FP feedback loop
- Weekly narrative LLM + export báo cáo
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
| LLM | **Bắt buộc**, **Ollama**, chỉ **alert explain** |
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
│       → Alert Manager → persist PG → publish               │
│       → SSE fan-out → LLM Explain (Ollama)                 │
└───────────────┬─────────────────────────────┬──────────────┘
                │                             │
                ▼                             ▼
     ┌──────────────────┐          ┌─────────────────────┐
     │  PostgreSQL      │          │  Kafka              │
     │  events/alerts   │          │  security-alerts    │
     └──────────────────┘          └──────────┬──────────┘
                                              ▼
                                   ┌─────────────────────┐
                                   │  Spring SSE bridge  │
                                   │  → React SOC UI     │
                                   └─────────────────────┘
```

### 4.2 Kafka topics (chốt)

| Topic | Producer | Consumer | Nội dung |
|-------|----------|----------|----------|
| `raw-web-logs` | Filebeat | Spring Ingest | Dòng log Nginx JSON (+ metadata Filebeat) |
| `security-alerts` | Spring Alert Manager | Spring SSE bridge (cùng app hoặc listener) | Alert JSON đã chấm điểm |

Frontend **không** đọc Kafka. React nhận alert qua **SSE** từ Spring.

### 4.3 Thành phần Spring (logical modules)

| Module | Trách nhiệm |
|--------|-------------|
| `ingest` | Kafka consumer `raw-web-logs`, validate; offset Kafka là nguồn tiến độ (không dedupe hash phức tạp) |
| `normalize` | Parse Nginx JSON → `WebEvent`; map `host` → `app_id` |
| `rules` | Load rule từ DB, evaluate regex theo field |
| `scoring` | `base + freq_bonus(5m) + status_bonus` → score 0–100 |
| `alert` | Nếu score ≥ threshold app → tạo alert 1-1 với event, publish `security-alerts` |
| `api` | REST events/alerts/apps/rules/stats |
| `realtime` | SSE fan-out alert |
| `llm` | `POST .../explain` → Ollama (profile `llm`) |
| `security` | Spring Security + JWT |

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

---

## 6. Công nghệ & stack

### 6.1 Stack chính (chốt)

| Lớp | Công nghệ |
|-----|-----------|
| Language / Framework | **Java 17**, Spring Boot 3 |
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

**DetectionRule** — `id`, `code`, `name`, `category` (`SQLI`|`XSS`|`PATH_TRAVERSAL`), `pattern`, `target_field` (`path`|`query`|`ua`|`raw`), `weight`, `enabled`, `description`

**DetectionHit** — `id`, `event_id`, `rule_id`, `evidence`, `weight`, `created_at`

**Alert** — `id`, `app_id`, `event_id` (**NOT NULL**, quan hệ 1-1 MVP), `severity`, `score`, `title`, `summary`, `status` (`OPEN`|`ACK`|`CLOSED`), `created_at`, `updated_at`

### 7.2 Rule categories MVP

Chỉ 3 category: `SQLI`, `XSS`, `PATH_TRAVERSAL`.  
Seed 8–15 rule qua Flyway. CRUD API để bật/tắt và sửa weight.

### 7.3 Scoring (chốt số)

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

---

## 8. API bề mặt (MVP)

| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/auth/login` | Đổi credential → JWT |
| GET | `/api/events` | Query events (filter app, time, score) |
| GET | `/api/events/{id}` | Chi tiết + hits |
| GET | `/api/alerts` | Danh sách alert |
| PATCH | `/api/alerts/{id}` | Đổi status ACK/CLOSED |
| GET | `/api/apps` | Danh sách application scope |
| GET/POST/PATCH | `/api/rules` | Quản trị rule (admin JWT) |
| GET | `/api/stats/overview` | KPI dashboard |
| GET | `/api/stream/alerts` | **SSE** realtime |
| POST | `/api/alerts/{id}/explain` | LLM explain qua Ollama |

---

## 9. LLM extension (phạm vi đã thu hẹp)

### 9.1 Use case MVP (bắt buộc)

**Alert explanation only** — input: `WebEvent` + `DetectionHit[]` đã sanitize; output: giải thích tiếng Việt + mức tin cậy định tính.

### 9.2 Ngoài MVP

- Triage assistant  
- Weekly report narrative  

### 9.3 Ràng buộc

- LLM **không** detect  
- Sanitize/cắt field trước khi gửi Ollama  
- UI: *AI-assisted, may be inaccurate*  
- Spring profile `llm`; tắt được khi không có Ollama  

---

## 10. Security knowledge & báo cáo quản trị

### 10.1 Nội dung kỹ thuật cần viết

- OWASP Top 10 (nhấn Injection, XSS) theo hướng **detection trên log**  
- Hạn chế access log (thiếu POST body)  
- Signature vs anomaly; FP/FN; alert fatigue  
- Workflow Detect → Triage → Report  

### 10.2 Chính sách & quản trị (chốt khung)

| Ưu tiên | Khung | Cách dùng trong báo cáo |
|---------|-------|-------------------------|
| Chính | **NIST CSF** | Map chi tiết control **Detect (DE)** và **Respond (RS)** vào module hệ thống |
| Phụ | **ISO 27001** | Bảng đối chiếu ngắn control logging/monitoring (phụ lục) |

**Policy artifacts bắt buộc có trong báo cáo (ở mức đề xuất lab):**

1. Logging & monitoring policy (ngắn)  
2. Retention đề xuất lab: **30 ngày** event/alert trong DB  
3. Escalation matrix đơn giản (detect → analyst ACK → escalate nếu CRITICAL)  
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

### 11.1 Bộ test

- Tập A: ~50 request bình thường (browse Juice Shop)  
- Tập B: ~30 request probe qua **script curl/k6** (SQLi/XSS/path traversal) trong lab  
- Gắn nhãn ground truth trước khi chạy detector (file trong `datasets/`)  
- Báo cáo: TP / FP / FN thô  

### 11.2 Metrics vận hành

- E2E latency mục tiêu: **request → alert SSE &lt; 5 giây**  
- Số rule enabled / 3 categories  
- Số alert theo `app_id` trong phiên demo  

### 11.3 Dataset nộp

Compose + script reproduce + mẫu raw log (nếu cần offline) trong `datasets/`. Không phụ thuộc recording tay.

---

## 12. Kế hoạch triển khai (6–8 tuần)

| Tuần | Việc | Deliverable |
|------|------|-------------|
| 1 | Chốt design (done), outline báo cáo NIST, schema Flyway | SOLUTION_DESIGN v1.1 |
| 2 | Compose: Juice + Nginx JSON + Filebeat → Kafka | `raw-web-logs` có data |
| 3 | Spring ingest + normalize + persist | API events |
| 4 | Rule engine + scoring + seed rules | Hits + score |
| 5 | Alert + JWT + publish `security-alerts` + SSE | Alert realtime |
| 6 | React SOC dashboard + datasets/scripts | Demo O1–O8 |
| 7 | Ollama explain + chương quản trị NIST | O9 + draft báo cáo |
| 8 | Đo FP/FN, polish, slide, buffer | Nộp BTL |

---

## 13. Deliverables nộp môn

1. Monorepo + `docker-compose.yml` (+ profile `llm`)  
2. README chạy lab / probe / xem alert / explain  
3. Config Nginx + Filebeat  
4. Báo cáo PDF (kỹ thuật + NIST CSF + ISO phụ lục + đánh giá)  
5. Slide 8–10 phút  
6. `SOLUTION_DESIGN.md`  

---

## 14. Cấu trúc repo (chốt monorepo)

```text
CyberSecurity/
├── SOLUTION_DESIGN.md
├── README.md
├── docker-compose.yml
├── infra/
│   ├── nginx/
│   ├── filebeat/
│   └── kafka/
├── backend/                    # Spring Boot security platform
├── frontend/                   # React SOC dashboard
├── datasets/                   # ground truth + probe scripts + sample logs
└── docs/
    ├── report/
    └── slides/
```

---

## 15. Rủi ro & mitigations

| Rủi ro | Mitigation |
|--------|------------|
| Scope phình | Đã khoá §3.2; ModSec/DVWA/PDF/FP-loop = không MVP |
| Máy chấm bài yếu | Ollama theo profile `llm`; không Kafka UI mặc định |
| Parse log brittle | Nginx JSON schema cố định §5.4 |
| Alert noise | 8–15 rule; threshold 60; ACK workflow |
| LLM bịa | Ground bằng rule hits; chỉ explain |
| Trùng đồ án | Không fork Watch-Tower |

---

## 16. Open questions — đã đóng

| Câu hỏi | Quyết định |
|---------|------------|
| FE React hay Thymeleaf? | **React + Vite** |
| SSE hay WebSocket? | **SSE** |
| Auth JWT hay session? | **JWT** (role admin) |
| LLM Ollama hay cloud? | **Ollama**; chỉ alert explain; bắt buộc deliverable |
| DVWA trong MVP? | **Phase 2** |
| ModSec so sánh? | **Không** |
| elk-lab clone hay tự compose? | **Tự compose** + Filebeat theo SIEM-in-a-box |
| Topics Kafka? | `raw-web-logs` + `security-alerts`; normalize in-process |
| Flyway hay Liquibase? | **Flyway** |
| Threshold / N? | **60** / **5 phút** |
| Khung quản trị? | **NIST CSF chính**, ISO phụ |
| Apache / FP mark / PDF export? | **Ngoài MVP** |
| Java version? | **17** |
| Alert 1-1 hay aggregate? | **1 alert : 1 event** |

Còn lại chỉ là metadata hành chính (tên SV, deadline môn, có làm nhóm không) — không chặn implementation.

---

## 17. Changelog

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-09-12 | Khởi tạo draft: scope, Kafka, Spring, reuse tầng trên, LLM extension |
| 1.1 | 2026-09-12 | **Chốt quyết định:** tự compose Juice+Nginx; React+SSE+JWT; Flyway; topics raw+alerts; Ollama explain bắt buộc; DVWA/ModSec/Apache/PDF/FP-loop ngoài MVP; scoring 60/5p; NIST CSF chính; đóng open questions |
