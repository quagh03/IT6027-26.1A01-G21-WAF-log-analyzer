# Plan hoàn thiện Week 5

> Trạng thái: **Done** (2026-10-11) — số đo trong `datasets/eval/`, báo cáo `docs/report/final-report.*`, slide `docs/slides/demo-week05.md`.

Đóng DoD trong `docs/weeks/week-05.md` và deliverable `docs/solution-design.md` §13. Phụ thuộc Week 4 `Done` (demo E2E trên Compose).

OWASP Benchmark trong solution-design §3.2 và `docs/rule-creation.md` §2.B là Phase 2, không bắt buộc MVP. Plan này vẫn đo, như eval ngoài. Không đưa payload Benchmark vào prompt sinh rule. Không sửa rule sau khi xem kết quả. Nếu có sửa, lần đo sau không còn là external eval và báo cáo phải ghi rõ.

Đo trước, viết báo cáo sau.

## 1. Tầng B — Juice Shop

Chạy trên lab sạch, sau Week 4:

```bash
./datasets/probes/browse_clean.sh
./datasets/probes/run.sh
```

Đối chiếu `datasets/ground_truth.csv` (50 browse `expected=clean`, 30 probe gồm 24 `alert` và 6 `clean`) với alert thực tế.

Bảng số tuyệt đối, tách tập sạch và tập probe:

| | Có alert | Không alert |
|---|---|---|
| Nhãn tấn công | TP | FN |
| Nhãn sạch | FP | TN |

Thêm precision, recall, FPR, coverage SQLI / XSS / PATH_TRAVERSAL. Hạn chế ghi trong bảng: payload chỉ nằm trong POST body không có trên access log, tính là giới hạn detector chứ không phải rule hỏng.

Artifact: bảng trong báo cáo và một file kết quả dưới `datasets/` (ví dụ `datasets/eval/juice-lab.md`).

## 2. OWASP Benchmark — chỉ đo

Nguồn: OWASP Benchmark v1.2, file `expectedresults`. Không commit corpus nếu license không cho. README module ghi lệnh tải.

Phạm vi chấm:

- Vào mẫu số: test case category SQL Injection, Cross-Site Scripting, Path Traversal, và payload xuất hiện trên query hoặc path của access log.
- `body_invisible`: payload chỉ trong POST body. Không replay như thể log phải thấy. Đếm riêng, không kéo TPR.
- `out_of_scope`: command injection, LDAP, XPath, crypto, hash, cookie, weak random, trust boundary. Không tính FN.

Cách chạy: replay `GET` qua Nginx lab (`Host: juice.lab.local`), đợi ingest, so alert với nhãn Benchmark.

| Nhãn Benchmark | Alert | Ô |
|---|---|---|
| `true` | có | TP |
| `true` | không (payload có trên log) | FN |
| `false` | có | FP |
| `false` | không | TN |

Score, từng category và gộp ba category:

```text
TPR = TP / (TP + FN)
FPR = FP / (FP + TN)
score = TPR - FPR
```

Báo cáo kèm số `body_invisible` và `out_of_scope`. So sánh ngắn với tầng A đã có trong `detection-rule-generator/evaluator/reports/metrics.md` (14 rule pass, held-out / benign / adversarial). Hai tầng không trộn một mẫu số.

Artifact: `datasets/eval/owasp-benchmark.md`.

## 3. Latency

Một probe nhìn thấy trên log tới lúc SSE alert. Ghi số giây. Mục tiêu solution-design §11.2 là dưới 5 giây. Trượt thì giải thích trong báo cáo, không để trống.

## 4. Báo cáo PDF

`docs/report/`, tiếng Việt. Mục lục bắt buộc:

- Kỹ thuật: OWASP trên access log, signature so với anomaly, workflow Detect → Alert → Incident → Explain → Triage
- NIST CSF: map Detect (DE) và Respond (RS) vào module (ingest, rule, alert, incident, SSE, explain)
- Phụ lục ISO 27001 ngắn cho logging / monitoring
- O8: tầng A (`metrics.md`) và tầng B (mục 1)
- OWASP Benchmark: bảng mục 2, kèm hạn chế body và category ngoài scope
- Hạn chế hệ thống, ethics (chỉ probe Juice Shop local)
- Policy lab: logging/monitoring, retention 30 ngày, escalation (detect → incident → explain → ACK → escalate nếu CRITICAL), RACI một trang

Báo cáo tuần trong `docs/report/week/` không thay file nộp này.

## 5. Slide

`docs/slides/`, 8–10 phút, đúng solution-design §2.3:

1. Traffic sạch
2. Runner probe
3. Alert SSE và incident
4. Drill-down hits / score
5. Explain, đường chính là `SKIPPED` khi không có Ollama; `READY` chỉ là thêm
6. Stats theo `app_id`

Bảng Benchmark để cuối slide, không chiếm demo live.

## 6. README và dry-run

Người khác clone repo, không cần chat nội bộ:

```bash
docker compose --profile ui up -d --build
```

Login, chạy probe, thấy incident. README có mục tắt LLM (`SKIPPED`) và mục bật profile `llm`.

Dry-run một lần đồng hồ 8–10 phút. Gãy bước nào thì sửa bug chặn demo (ingest, score, promote, SSE, login), không thêm feature.

## Xong khi

- Mọi AC `docs/weeks/week-05.md` §6 pass
- Đủ 6 mục nộp §13: monorepo + compose, README, Nginx/Filebeat, rule-generator kèm eval, PDF, slide, solution design
- `docs/weeks/week-05.md` để trạng thái `Done`
- Không thêm DVWA, ModSecurity, export PDF/CSV từ UI, vòng chỉnh weight, cloud LLM, correlation ngoài `(app_id, client_ip)`

## Không làm

Sinh rule mới từ Benchmark. Retune weight sau khi xem FP Juice Shop, trừ bug chứng minh được là sai regex và được ghi trong báo cáo là đã sửa sau đo.
