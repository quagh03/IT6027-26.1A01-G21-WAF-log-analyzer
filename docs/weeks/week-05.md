# Week 05 — Đánh giá, báo cáo, slide & nộp

| Trường | Giá trị |
|--------|---------|
| Tuần | `05` / `5` |
| Theme | FP/FN + NIST CSF + slide demo + polish |
| Phụ thuộc | Week 04 DoD (demo E2E chạy được) |
| Map outcome | **O8**, **O10** (+ đóng O1–O9 nếu còn hở) |
| Trạng thái | `Not started` |

---

## 1. Problem statement

Hệ thống lab đã demo được nhưng chưa có bằng chứng hiệu quả (TP/FP/FN), chưa có báo cáo quản trị (NIST CSF chính, ISO phụ), chưa có slide 8–10 phút và README nộp. Thiếu các artifact này thì BTL chưa nộp được dù code chạy. Tuần này đóng gap **đánh giá + tài liệu + ổn định demo**, không mở feature mới.

## 2. Goal

Nộp đủ deliverable §13: monorepo chạy được, bảng FP/FN trên bộ ~50 sạch + ~30 probe, báo cáo PDF (kỹ thuật + NIST), slide demo, README reproduce.

## 3. Outcome

| ID | Outcome tuần | Tiêu chí đo |
|----|--------------|-------------|
| W5-O1 | Đánh giá detector | Bảng TP/FP/FN thô trên tập A (~50) + tập B (~30) có nhãn (O8) |
| W5-O2 | Báo cáo môn | Chương kỹ thuật + NIST CSF (DE/RS) + ISO 27001 phụ + hạn chế + ethics (O10) |
| W5-O3 | Demo tape | Slide 8–10 phút bám flow §2.3 |
| W5-O4 | Gói nộp | Compose + README + config + solution design; latency request→alert SSE mục tiêu &lt; 5 giây |

## 4. Scope

### In-scope

- Chạy `browse_clean` + `run.sh`; đối chiếu `datasets/ground_truth.csv` với Alert thực tế
- Bảng TP/FP/FN + coverage 3 category; ghi MTTD/latency nếu đo được trong demo
- Báo cáo tiếng Việt: OWASP trên log, hạn chế access log (thiếu POST body), signature vs anomaly, alert fatigue, workflow Detect→Triage
- Policy artifacts: logging/monitoring ngắn, retention 30 ngày, escalation matrix, RACI 1 trang
- Threat model tự do (JWT, không expose Kafka, sanitize LLM)
- Slide 8–10 phút
- Polish README, ethics, profile `llm`, Java/Docker pin
- Bugfix **chặn demo** (ingest, score, promote, SSE, login)
- Cập nhật `docs/solution-design.md` changelog nếu lệch nhỏ so với code

### Out-of-scope (cấm phình trong tuần này)

- Feature mới: DVWA, ModSec, PDF export, FP-loop, cloud LLM, correlation phức tạp
- Refactor lớn không phục vụ demo/báo cáo
- Thêm category rule ngoài SQLI/XSS/PATH_TRAVERSAL

## 5. Work breakdown

| # | Việc | Ghi chú / artifact |
|---|------|---------------------|
| 1 | Chốt ground truth + chạy đo | `datasets/ground_truth.csv` + bảng kết quả |
| 2 | Viết chương kỹ thuật | `docs/report/` |
| 3 | Map NIST CSF DE/RS + ISO phụ lục | Cùng báo cáo |
| 4 | Policy 4 artifact + RACI + threat/ethics | |
| 5 | Slide 8–10 phút | `docs/slides/` |
| 6 | README nộp (up, probe, incident, explain, tắt llm) | `README.md` |
| 7 | Bugfix blocker demo + đo latency O1/O6 | |
| 8 | Dry-run demo 1 lần đồng hồ 8–10 phút | Buffer cuối tuần |

## 6. Acceptance Criteria (AC)

| ID | AC |
|----|----|
| AC-1 | Given `ground_truth.csv` và một lần chạy runner + browse, When đối chiếu Alert, Then có bảng TP / FP / FN (số tuyệt đối) cho tập A và tập B |
| AC-2 | Given bảng đánh giá, When đọc, Then nêu coverage 3 category và ít nhất một hạn chế (ví dụ thiếu POST body → FN) |
| AC-3 | Given báo cáo PDF, When mở mục lục, Then có phần kỹ thuật, phần NIST CSF (DE + RS map module), phụ lục ISO 27001 ngắn, đánh giá O8, hạn chế hệ thống |
| AC-4 | Given báo cáo, When tìm policy lab, Then có logging/monitoring, retention 30 ngày, escalation, RACI |
| AC-5 | Given slide, When trình bày thử, Then nằm **8–10 phút** và đủ 6 bước flow §2.3 |
| AC-6 | Given clone sạch + README, When `compose up` và probe, Then người khác reproduce Alert/Incident không cần chat nội bộ |
| AC-7 | Given profile không `llm`, When tạo Incident, Then hệ thống vẫn demo được (SKIPPED), README có mục này |
| AC-8 | Given dry-run, When đo request probe → Alert SSE, Then ghi số đo; mục tiêu &lt; 5 giây (nếu trượt phải giải thích trong báo cáo, không im lặng) |
| AC-9 | Given diff tuần này, When review, Then không có feature nằm §3.2 out-of-scope |

## 7. Definition of Done (DoD)

Tuần **Done** khi **tất cả** điều sau đúng:

- [ ] Mọi AC trong §6 pass
- [ ] Đủ 6 deliverable nộp §13
- [ ] Dry-run demo một lần không gãy bước (LLM được phép FAILED nếu đã ghi trong slide)
- [ ] Không thêm scope phase 2
- [ ] `docs/weeks/week-05.md` đánh `Done` sau khi gói nộp khóa

## 8. Deliverables

| Artifact | Path gợi ý |
|----------|------------|
| Monorepo + compose (+ profile `llm`) | repo root |
| README lab / probe / incident / explain | `README.md` |
| Nginx + Filebeat config | `infra/` |
| Báo cáo PDF | `docs/report/` |
| Slide 8–10 phút | `docs/slides/` |
| Solution design | `docs/solution-design.md` |
| Ground truth + bảng FP/FN | `datasets/` + mục trong báo cáo |

## 9. Risks

| Rủi ro | Tác động nếu trễ | Mitigation tuần này |
|--------|------------------|---------------------|
| Viết báo cáo sớm, quên đo FP/FN | O8 thiếu số | **Đo trước**, viết sau; chặn slide đến khi có bảng |
| Feature creep “thêm cho đẹp” | Không kịp PDF/slide | Out-of-scope tuần này = freeze feature |
| FP cao trên tập sạch | Câu chuyện detector yếu | Không kịp retune lớn: giải thích hạn chế signature + 1–2 rule hẹp nếu còn giờ |
| Máy chấm không có Ollama | O9 trượt cảm tính | Slide + README path SKIPPED là chính; READY là plus |
| Demo live fail | Mất điểm trình bày | Dry-run; backup screenshot/log sample trong `datasets/` nếu cần offline |

## 10. Demo checkpoint

Dry-run nộp (đồng hồ 8–10 phút), đúng §2.3:

1. Traffic sạch Juice Shop
2. Runner probe
3. Alert SSE + Incident promote
4. Drill-down hits/score
5. Explain async hoặc status FAILED/SKIPPED
6. Stats theo `app_id`
7. (Hậu demo, không trên slide nếu thiếu giờ) mở bảng FP/FN trong báo cáo

## 11. Handoff

- **Input cho tuần sau:** không — hết 5 tuần; gói nộp
- **Nợ kỹ thuật chấp nhận được:** phase 2 (DVWA, correlation giàu, FP-loop, weekly LLM) ghi trong báo cáo là hạn chế / hướng phát triển
- **Blocker phải giải trước tuần sau:** *(n/a)* — blocker còn lại phải xử lý trong buffer Week 5 hoặc khai báo hạn chế có số liệu
