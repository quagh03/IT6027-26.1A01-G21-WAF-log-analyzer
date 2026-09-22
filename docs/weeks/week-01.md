# Week 01 — Lab pipeline, ingest & normalize

| Trường | Giá trị |
|--------|---------|
| Tuần | `01` / `5` |
| Theme | Compose lab + Kafka `raw-web-logs` + Spring ingest/normalize |
| Phụ thuộc | `docs/solution-design.md` v1.4 Approved; skeleton `backend/` |
| Map outcome | **O1**, **O2** |
| Trạng thái | `Done` |

---

## 1. Problem statement

Chưa có đường đi từ HTTP thật trên Juice Shop tới event chuẩn trong PostgreSQL. Không có Nginx JSON log, Filebeat, Kafka topic, schema Flyway và consumer Spring thì không chứng minh được ingest realtime, cũng không có dữ liệu để detect ở các tuần sau. Tuần này đóng gap **thu thập → chuẩn hoá → lưu**, chưa đụng rule hay UI.

## 2. Goal

Chạy một lệnh Compose để request qua Nginx xuất hiện trên Kafka `raw-web-logs` trong dưới 5 giây và được Spring map thành `WebEvent` rồi persist, truy vấn được qua `GET /api/events`.

## 3. Outcome

| ID | Outcome tuần | Tiêu chí đo |
|----|--------------|-------------|
| W1-O1 | Ingest realtime | Request lab → message trên `raw-web-logs` **&lt; 5 giây** (O1) |
| W1-O2 | Normalize in-process | Mọi event hợp lệ map đủ field `WebEvent` §7.1 + `host` → `app_id` (O2) |
| W1-O3 | Persist + đọc lại | Event nằm PostgreSQL; `GET /api/events` trả đúng bản ghi vừa ingest |

## 4. Scope

### In-scope

- `docker-compose.yml`: `juice-shop`, `nginx`, `filebeat`, `kafka` (KRaft), `postgres`, `spring-security-backend`
- Nginx access log JSON đúng schema §5.4; hai `server_name` giả (`juice.lab.local`, `shop.lab.local`) cùng upstream Juice Shop
- Filebeat → `kafka:9092`, topic `raw-web-logs`; metadata `host`, `log_type=access`
- Flyway: bảng MVP §7.1 (Application, WebEvent, và placeholder các bảng còn lại nếu tránh migration vỡ Week 2)
- Seed tối thiểu 1–2 `Application` (host pattern → `app_id`, `risk_threshold=60`)
- Spring: Kafka consumer `raw-web-logs` → validate → normalize in-process → persist `WebEvent`
- `GET /api/events` (filter thô: app, time); `GET /api/events/{id}` có thể chưa có hits
- Skeleton `datasets/probes/` (file trống hoặc 2–3 request smoke, chưa ground truth)
- README tối thiểu: `compose up`, browse Juice qua Nginx, cách xem topic/event

### Out-of-scope (cấm phình trong tuần này)

- Rule engine, scoring, Alert, Incident, SSE, JWT đầy đủ, React, Ollama
- Kafka UI trong compose mặc định
- DVWA, parse Apache, detection trong Filebeat/Nginx
- Attack Scenario Runner hoàn chỉnh / `ground_truth.csv`

## 5. Work breakdown

| # | Việc | Ghi chú / artifact |
|---|------|---------------------|
| 1 | Compose + volume log Nginx | `docker-compose.yml`, `infra/nginx/` |
| 2 | Nginx JSON log + proxy Juice Shop | `infra/nginx/` đúng field §5.4 |
| 3 | Filebeat output Kafka | `infra/filebeat/`; topic `raw-web-logs` |
| 4 | Kafka KRaft single-node | `infra/kafka/` nếu tách config |
| 5 | PostgreSQL + Flyway schema + seed Application | `backend/.../db/migration` |
| 6 | Entity `WebEvent` + consumer + normalizer | Module `ingest`, `normalize` |
| 7 | REST `GET /api/events` | Chưa bắt buộc auth (auth Week 3); ghi nợ |
| 8 | Skeleton probes + README lab Week 1 | `datasets/probes/`, `README.md` |

## 6. Acceptance Criteria (AC)

| ID | AC |
|----|----|
| AC-1 | Given Compose đã `up`, When gửi `GET` Juice Shop qua Nginx, Then trong **&lt; 5 giây** có message trên topic `raw-web-logs` |
| AC-2 | Given một dòng log Nginx JSON hợp lệ, When Spring consume, Then persist `WebEvent` đủ: `method`, `path`, `query`, `status`, `user_agent`, `client_ip`, `event_time`, `host`, `app_id` |
| AC-3 | Given request tới `Host: juice.lab.local` và `Host: shop.lab.local`, When normalize, Then `app_id` khác nhau theo bảng Application |
| AC-4 | Given event đã persist, When `GET /api/events`, Then response chứa event đó (id, app, time, path, status) |
| AC-5 | Given log JSON thiếu field bắt buộc / không parse được, When consume, Then **không** crash consumer; offset vẫn tiến (skip + log lỗi) |
| AC-6 | Given máy sạch clone repo, When làm theo README Week 1, Then reproduce AC-1 và AC-4 không cần bước “bí mật” ngoài repo |

## 7. Definition of Done (DoD)

Tuần **Done** khi **tất cả** điều sau đúng:

- [x] Artifact §8 nằm đúng path trong monorepo (Compose, infra, Flyway, ingest/normalize/API, `datasets/probes` skeleton, README)
- [x] README đủ compose up → request → Kafka / `GET /api/events`
- [x] Không merge rule-engine evaluate / alert / UI / LLM vào critical path Week 1
- [x] Mọi AC trong §6 pass trên lab local (smoke 2026-09-22: Kafka message, persist WebEvent, dual Host → appId 1 vs 2, `GET /api/events`)
- [x] Demo checkpoint §10 chạy được một lần

## 8. Deliverables

| Artifact | Path gợi ý |
|----------|------------|
| Compose lab | `docker-compose.yml` |
| Nginx + Filebeat + Kafka config | `infra/nginx/`, `infra/filebeat/`, `infra/kafka/` |
| Flyway + seed app | `backend/src/main/resources/db/migration/` |
| Ingest + normalize + events API | `backend/` |
| Skeleton probes | `datasets/probes/` |
| Hướng dẫn chạy | `README.md` (mục lab Week 1) |

## 9. Risks

| Rủi ro | Tác động nếu trễ | Mitigation tuần này |
|--------|------------------|---------------------|
| Filebeat/Kafka cấu hình sai, không có message | O1 fail, Week 2 không có data | Smoke: 1 request → kafka-console-consumer trước khi viết Spring |
| Nginx JSON lệch tên field §5.4 | Parse brittle cả kỳ | Copy đúng tên field bảng §5.4; fixture 1 dòng log trong test |
| Java 25 / image Docker lệch máy | Compose không lên | Pin image + ghi JDK trong README |
| Scope tràn sang rule engine | Week 1 không Done | Cấm PR chứa `DetectionRule` evaluate |

## 10. Demo checkpoint

1. `docker compose up -d`
2. Browser hoặc curl Juice Shop qua Nginx (đúng Host lab)
3. Chỉ message mới trên `raw-web-logs`
4. `GET /api/events` — thấy `path`/`status`/`app_id` tương ứng
5. (Tuỳ chọn) đổi Host giả lập — `app_id` đổi

Không cần dashboard. Không cần probe tấn công.

## 11. Handoff

- **Input cho tuần sau:** topic sống, `WebEvent` ổn định, bảng Application, schema sẵn cho Rule/Hit
- **Nợ kỹ thuật chấp nhận được:** API events chưa JWT; probes mới skeleton; các bảng Alert/Incident có thể mới tồn tại trống
- **Blocker phải giải trước tuần sau:** Kafka consumer không ổn định / schema log không khớp §5.4
