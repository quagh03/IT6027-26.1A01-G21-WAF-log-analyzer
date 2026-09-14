# Week 04 — SOC UI, Attack Runner & LLM explain

| Trường | Giá trị |
|--------|---------|
| Tuần | `04` / `5` |
| Theme | React dashboard + probes có nhãn + Ollama async |
| Phụ thuộc | Week 03 DoD (API + SSE + Incident) |
| Map outcome | **O6**, **O7**, **O9** |
| Trạng thái | `Not started` |

---

## 1. Problem statement

Backend đã tạo Alert/Incident realtime nhưng chưa có SOC để demo 8–10 phút, chưa có runner tái lập tấn công có nhãn, và chưa có LLM explain — hạng mục bắt buộc O9. Thiếu ba thứ này thì chưa demo được flow chuẩn §2.3. Tuần này đóng gap **mặt SOC + dataset reproduce + explain async**, chưa viết báo cáo NIST đầy đủ.

## 2. Goal

Chạy Compose (+ profile `llm`), một lệnh probe, thấy timeline/alert/incident trên React qua SSE, và Incident mới có phần giải thích Ollama (hoặc trạng thái FAILED/SKIPPED rõ ràng) không chặn ingest.

## 3. Outcome

| ID | Outcome tuần | Tiêu chí đo |
|----|--------------|-------------|
| W4-O1 | Dashboard gần realtime | Timeline, top attack type, top IP, top path; SSE alert + incident (O6) |
| W4-O2 | Demo dataset | `datasets/probes` + browse sạch; một lệnh qua Nginx (O7) |
| W4-O3 | Incident + LLM | Explain async khi Incident **được tạo**; UI ghi AI-assisted; retry API (O9) |
| W4-O4 | Triage trên UI | SOC đổi ACK/CLOSED trên Incident; drill-down N alerts + hits + score |

## 4. Scope

### In-scope

- React + Vite SOC: login JWT, dashboard KPI/chart, list Alert, list Incident, chi tiết Incident
- SSE client cho alerts và incidents (kể cả cập nhật `explanation_status`)
- Scope filter theo `app_id` / host (Juice Shop MVP)
- `datasets/probes/scenarios.yaml` (hoặc JSON): `id`, `category`, `method`, `path`, `query`, `expected`, `note`
- `run.sh` hoặc `run.py` + `browse_clean.sh` (~50 path sạch)
- Payload trên query/path/UA; README ethics (chỉ lab local)
- Profile Compose `llm` + Ollama; Spring `@Async` after commit; sanitize input
- `POST /api/incidents/{id}/explain` retry async
- UI: *AI-assisted, may be inaccurate* + `explanation_status`
- Copy UI tiếng Việt ưu tiên

### Out-of-scope (cấm phình trong tuần này)

- Cloud LLM, weekly narrative, auto re-explain mỗi lần gắn alert
- Export PDF/CSV, FP mark tự chỉnh weight
- ZAP / Nuclei / sqlmap làm runner chính
- Chương NIST hoàn chỉnh (outline được; viết đầy đủ Week 5)
- DVWA

## 5. Work breakdown

| # | Việc | Ghi chú / artifact |
|---|------|---------------------|
| 1 | App React + auth JWT | `frontend/` |
| 2 | Dashboard charts + filter app | Recharts hoặc Chart.js |
| 3 | Alert feed + Incident list/detail/triage | Gắn SSE |
| 4 | Attack Scenario Runner + ~30 probe có nhãn | `datasets/probes/` |
| 5 | `browse_clean.sh` ~50 request | Juice Shop paths |
| 6 | `ground_truth.csv` sơ bộ (nhãn kỳ vọng) | Hoàn thiện đo Week 5 nếu thiếu cột |
| 7 | LLM module + profile `llm` | Không block consumer |
| 8 | Compose service `ollama` profile | Máy yếu: SKIPPED |
| 9 | Disclaimer + trạng thái explain trên UI | |

## 6. Acceptance Criteria (AC)

| ID | AC |
|----|----|
| AC-1 | Given đã login SOC, When Alert mới từ probe, Then dashboard nhận qua SSE **không** reload trang (O6) |
| AC-2 | Given cùng phiên, When Incident được tạo, Then list incident cập nhật qua SSE; drill-down thấy N alerts, rule hits, score |
| AC-3 | Given dashboard, When xem overview, Then có timeline, top attack type, top IP, top path (filter được `app_id`) |
| AC-4 | Given Incident OPEN, When analyst ACK/CLOSED trên UI, Then API + UI cùng status |
| AC-5 | Given Compose up, When `./datasets/probes/run.sh` (hoặc `run.py`), Then toàn bộ scenario gọi **qua Nginx** lab và log kết quả chạy |
| AC-6 | Given `browse_clean.sh`, When chạy, Then ~50 request sạch (sai số nhỏ chấp nhận, ghi số thật trong README) |
| AC-7 | Given scenario `expected=alert`, When detector Week 2–3 còn đúng, Then phần lớn tạo Alert (lệch ghi nhận — đo chính thức Week 5) |
| AC-8 | Given profile `llm` bật và Ollama sống, When Incident **mới tạo**, Then `explanation_status` đi `PENDING` → `READY` (hoặc `FAILED` nếu timeout) **không** làm trễ SSE alert |
| AC-9 | Given Ollama tắt/lỗi, When Incident mới, Then Alert/Incident vẫn xem được; status `FAILED` hoặc `SKIPPED`; không exception trên consumer |
| AC-10 | Given Incident `FAILED`, When `POST /api/incidents/{id}/explain`, Then enqueue lại async (không block HTTP request) |
| AC-11 | Given màn incident có explanation, When render, Then có cụm từ AI-assisted / có thể sai |
| AC-12 | Given README ethics, When đọc, Then nêu rõ chỉ probe Juice Shop tự host |

## 7. Definition of Done (DoD)

Tuần **Done** khi **tất cả** điều sau đúng:

- [ ] Mọi AC trong §6 pass trên lab (LLM: ít nhất một trong READY hoặc FAILED/SKIPPED đúng hành vi)
- [ ] Artifact §8 có runner một lệnh + frontend container hoặc `npm` ghi trong README
- [ ] Demo flow §2.3 bước 1–6 chạy được (bước 5 được phép FAILED nếu máy không kéo được model — phải show status)
- [ ] LLM không nằm trên Kafka consumer thread
- [ ] Không thêm DVWA / cloud LLM / export PDF

## 8. Deliverables

| Artifact | Path gợi ý |
|----------|------------|
| SOC UI | `frontend/` + service `soc-dashboard` |
| Runner + scenarios | `datasets/probes/scenarios.yaml`, `run.sh`/`run.py`, `browse_clean.sh` |
| Ground truth (draft) | `datasets/ground_truth.csv` |
| LLM async | module `llm`, profile `llm` |
| README lab đầy đủ | compose, probe, incident, explain |

## 9. Risks

| Rủi ro | Tác động nếu trễ | Mitigation tuần này |
|--------|------------------|---------------------|
| Ollama nặng máy | Kẹt cả tuần ở model | Profile tắt được; AC-9 là Done path; demo READY chỉ khi máy kéo được |
| FE phình (nhiều trang) | Không kịp runner | Chỉ login + dashboard + alerts + incident detail |
| Runner bắn POST body | Log Nginx không thấy payload | Chỉ query/path/UA |
| LLM bịa / chậm | Demo xấu | Timeout + FAILED; explain bám hits; không detect bằng LLM |
| SSE browser proxy | UI không realtime | Ghi nginx/vite proxy trong README; test curl SSE trước |

## 10. Demo checkpoint

Kịch bản ~8 phút (bản nháp demo môn):

1. Mở Juice Shop qua Nginx — traffic sạch (hoặc `browse_clean.sh`)
2. SOC dashboard: login, overview
3. Chạy `run.sh` — Alert nhảy SSE; Incident khi CRITICAL hoặc đủ K
4. Mở một Incident: alerts, evidence, score
5. Chờ explain `READY` **hoặc** chỉ `PENDING`/`FAILED` + bấm retry
6. ACK incident
7. Filter theo `app_id` / host

## 11. Handoff

- **Input cho tuần sau:** runner + ground truth draft, UI demo được, hành vi LLM đã chốt (READY/FAILED/SKIPPED)
- **Nợ kỹ thuật chấp nhận được:** chart chưa đẹp; ground truth chưa đối chiếu TP/FP; báo cáo NIST chưa viết
- **Blocker phải giải trước tuần sau:** runner không tái lập / SSE UI chết / LLM chặn ingest
