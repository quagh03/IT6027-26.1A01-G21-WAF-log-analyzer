# Week 03 — Alert, Incident, JWT & SSE

| Trường | Giá trị |
|--------|---------|
| Tuần | `03` / `5` |
| Theme | Alert 1-1 event + promote Incident + realtime + auth |
| Phụ thuộc | Week 02 DoD (score + hits ổn định) |
| Map outcome | **O5**, **O6** (SSE/API), **O9** (promote; LLM chưa) |
| Trạng thái | `Not started` |

---

## 1. Problem statement

Đã có điểm rủi ro trên từng request nhưng SOC chưa có đối tượng làm việc: không Alert thì không thấy “vừa tấn công”, không Incident thì không triage, không SSE thì dashboard tuần sau không realtime, không JWT thì API hở. Tuần này đóng gap **từ score → cảnh báo → incident phân cấp → luồng realtime có auth**. LLM cố ý để Week 4.

## 2. Goal

Khi `score >= threshold` hệ thống tạo Alert (1-1 event), promote Incident theo policy §7.5, phát SSE, bảo vệ API bằng JWT role `admin`.

## 3. Outcome

| ID | Outcome tuần | Tiêu chí đo |
|----|--------------|-------------|
| W3-O1 | Alert lifecycle | 1 alert ↔ 1 event; `incident_id` nullable (O5/O9 nền) |
| W3-O2 | Incident phân cấp | IMMEDIATE (CRITICAL) + AGGREGATE (MEDIUM/HIGH, K=3, W=5p, key `app_id+client_ip`) (O9 trừ LLM) |
| W3-O3 | Realtime | SSE `GET /api/stream/alerts` và `/api/stream/incidents`; publish topic `security-alerts` (O6) |
| W3-O4 | Auth SOC | `POST /api/oauth/tokens` → JWT; API (trừ login) yêu cầu role `admin` |
| W3-O5 | Rule admin | GET/POST/PATCH `/api/rules` (bật/tắt, sửa weight) |

## 4. Scope

### In-scope

- Tạo Alert khi `score >= application.risk_threshold` (default 60)
- Severity gợi ý §7.4 trên alert (MEDIUM/HIGH/CRITICAL); không tạo alert nếu dưới threshold
- Incident Manager đúng bảng §7.5; `W=5p`, `K=3` cấu hình `application.yml`
- Triage Incident: `PATCH /api/incidents/{id}` status `ACK`/`CLOSED`
- REST: alerts, incidents (kèm danh sách alerts), apps, rules, stats overview (có thể KPI thô)
- Kafka producer `security-alerts`; SSE fan-out từ Spring (FE chưa bắt buộc)
- JWT một user `admin` seed lab
- `explanation_status = SKIPPED` (hoặc tương đương) vì profile `llm` chưa bật

### Out-of-scope (cấm phình trong tuần này)

- Ollama / gọi LLM / UI React hoàn chỉnh (skeleton FE được phép nếu không ăn thời gian detect)
- Auto re-explain, weekly narrative, export PDF
- Correlation ngoài `(app_id, client_ip)` + cửa sổ W
- DVWA, Kafka UI mặc định

## 5. Work breakdown

| # | Việc | Ghi chú / artifact |
|---|------|---------------------|
| 1 | Alert manager + persist + 1-1 event | `incident_id` null cho đến khi promote |
| 2 | Publish `security-alerts` | Payload có `incident_id` nếu đã gắn |
| 3 | Incident policy IMMEDIATE / AGGREGATE | Module `incident`; không gọi LLM |
| 4 | REST incidents/alerts/apps/rules/stats | Khớp bảng §8 |
| 5 | SSE alerts + incidents | `GET /api/stream/*` |
| 6 | Spring Security + JWT | Login + bảo vệ API |
| 7 | Test policy: CRITICAL 1 alert → incident; 2 MEDIUM → chưa; 3 MEDIUM → incident | Test tự động bắt buộc |

## 6. Acceptance Criteria (AC)

| ID | AC |
|----|----|
| AC-1 | Given `score >= risk_threshold`, When pipeline xong, Then đúng **một** Alert gắn `event_id` đó |
| AC-2 | Given `score < threshold`, When pipeline xong, Then **không** có Alert |
| AC-3 | Given Alert `severity = CRITICAL` và chưa có Incident OPEN cùng key trong W, When promote, Then tạo Incident `promote_reason=IMMEDIATE` và gắn alert |
| AC-4 | Given Incident OPEN cùng `(app_id, client_ip)` trong W, When thêm CRITICAL, Then **không** tạo incident mới; gắn alert; cập nhật `alert_count`, `severity=max`, `score=max` |
| AC-5 | Given 2 Alert MEDIUM/HIGH cùng key, chưa incident, When đếm trong W, Then chưa tạo Incident (`incident_id` vẫn null) |
| AC-6 | Given Alert MEDIUM/HIGH thứ 3 cùng key trong W, When promote, Then tạo Incident `AGGREGATE`, gắn **cả nhóm** alert chưa gắn |
| AC-7 | Given client SSE mở `GET /api/stream/alerts`, When Alert mới, Then event SSE tới **không** chờ Incident |
| AC-8 | Given Incident tạo mới hoặc gắn alert, When SSE incidents, Then client nhận cập nhật |
| AC-9 | Given chưa JWT, When gọi `GET /api/incidents`, Then 401; Given token admin, Then 200 |
| AC-10 | Given `PATCH` incident `ACK` rồi `CLOSED`, When `GET` lại, Then status đúng; Alert feed vẫn list được alert chưa promote |
| AC-11 | Given admin PATCH rule `enabled=false` hoặc đổi `weight`, When event mới khớp pattern, Then evaluate dùng rule đã cập nhật |
| AC-12 | Given `llm` tắt, When Incident mới, Then không gọi HTTP LLM; `explanation_status` là `SKIPPED` (hoặc `PENDING` không bị treo vô hạn — chọn một và ghi README) |

## 7. Definition of Done (DoD)

Tuần **Done** khi **tất cả** điều sau đúng:

- [ ] Mọi AC trong §6 pass (gồm test policy K=3 và CRITICAL)
- [ ] Artifact §8 + API khớp §8 (trừ `POST .../explain` có thể 404/501 cho tới Week 4)
- [ ] Topic `security-alerts` có message khi có Alert
- [ ] Không merge Ollama client vào critical path consumer
- [ ] Demo checkpoint §10 chạy được (curl/SSE, chưa cần React)

## 8. Deliverables

| Artifact | Path gợi ý |
|----------|------------|
| Alert + Incident modules | `backend/` `alert`, `incident` |
| SSE + Kafka alerts | `realtime` + producer |
| JWT | `security` |
| REST MVP (trừ explain) | controllers §8 |
| Test promotion policy | `backend/src/test/` |

## 9. Risks

| Rủi ro | Tác động nếu trễ | Mitigation tuần này |
|--------|------------------|---------------------|
| Cài nhầm 1 alert = 1 incident | Sai thiết kế 1.4 | Test AC-5 bắt buộc trước demo |
| SSE chặn thread consumer | Ingest chậm, O1 tụt | Fan-out async; LLM/SSE không trong Kafka listener |
| JWT làm gãy smoke Week 1 | Demo ingest chết | Seed user lab; README curl login |
| Stats/API phụ ăn thời gian | Policy chưa xong | Ưu tiên AC-1…AC-6 trước CRUD đẹp |

## 10. Demo checkpoint

1. Login `POST /api/oauth/tokens` → dùng Bearer
2. Mở SSE alerts (curl `-N`)
3. Gửi 1 probe CRITICAL (score ≥ 85) → Alert + Incident IMMEDIATE trên API
4. Từ IP khác: 2 probe MEDIUM → chỉ Alert; probe thứ 3 → Incident AGGREGATE
5. `PATCH` ACK incident
6. Xác nhận alert chưa đủ K vẫn list trên `GET /api/alerts`

## 11. Handoff

- **Input cho tuần sau:** contract JSON Alert/Incident/SSE; JWT; policy ổn định để UI + LLM bám vào
- **Nợ kỹ thuật chấp nhận được:** chưa `POST /explain`; stats overview có thể tối giản; chưa React
- **Blocker phải giải trước tuần sau:** promote sai K/W hoặc SSE không đẩy incident
