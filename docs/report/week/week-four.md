# Tuần 4 — Chi tiết giải pháp đề xuất

| Trường | Giá trị |
|--------|---------|
| Môn | IT6027 · nhóm G21 · lớp 26.1A01 |
| Nguồn | `docs/solution-design.md` v1.6 · `application.yaml` · `IncidentManager` · `RiskScorer` · `V2__seed_detection_rules.sql` · `datasets/probes` · `metrics.md` · báo cáo tuần 3 |
| Mục tuần 4 | Mô tả giải pháp đã chốt thành từng khối có tham số, ví dụ số, và chỗ đứng so với bài báo cùng hướng |
| Trạng thái | Pipeline detect → alert → incident → SSE → SOC đã có trong repo. Coverage offline đã đo. Replay 10.000 dòng và bảng FP/FN Juice **chưa** phải số của báo cáo này |

---

## 4. Chi tiết giải pháp đề xuất

Tuần 3 đặt ba câu hỏi: coverage trên log có lệch quá 15 điểm so với held-out hay không (RQ1), số incident có giảm ít nhất 15% so với một alert một incident hay không (RQ2), và p95 tới SSE có dưới 5 giây trên luồng lớn hay không (RQ3). Tuần 4 không trả số cho ba câu đó. Tuần 4 mô tả **giải pháp dùng để trả**, ở mức đủ để người khác dựng lại cùng tham số.

Giải pháp là một log analyzer đứng sau Nginx, không phải WAF chặn request. Detector là 14 regex đã qua cổng precision ≥ 0,80 và FPR ≤ 0,02. Điểm 0–100 quyết định alert. Incident chỉ mở theo hai nhánh IMMEDIATE và AGGREGATE. LLM đọc incident sau khi transaction ingest đã commit, và không được đổi điểm.

---

### 4.1 Bài toán từng khối phải đóng

| Khối | Việc khối đó làm | Việc khối đó không làm | Câu hỏi tuần 3 |
|------|------------------|------------------------|----------------|
| Rule engine | Khớp field `query` hoặc `path`, lưu hit kèm `rule_id`, weight, evidence | Không gọi LLM, không chặn request | RQ1 |
| Scoring | Cộng weight, cộng bonus tần suất và status, cắt 100 | Không học lại trọng số | RQ1, ngưỡng alert của RQ2 |
| Alert | Một alert cho một event khi score ≥ ngưỡng app | Không mở incident | Mẫu số RQ2 |
| Incident | Gộp theo `(app_id, client_ip)` trong 5 phút | Không giải thích, không đổi score | RQ2 |
| SSE + SOC | Đẩy alert và incident tới dashboard, triage ACK/CLOSED | Không đọc Kafka từ trình duyệt | RQ3 |
| Ollama | Viết đoạn giải thích từ tối đa 5 alert đã có hit | Không nằm trên thread Kafka | Ngoài đồng hồ RQ3 |
| Probe lab | 24 payload có nhãn `alert` và 50 request sạch | Không đo replay 10.000 dòng | Tầng S của tuần 3 |

---

### 4.2 Luồng xử lý một request

Một request tới `juice.lab.local` đi qua bảy bước. Mỗi bước có chỗ dừng riêng, để lỗi Ollama không kéo theo mất log.

```text
Nginx JSON access log
  → Filebeat → Kafka topic raw-web-logs
  → Spring: parse → WebEvent (PostgreSQL)
  → 14 rule (Redis cache rule đang bật) → DetectionHit
  → score 0–100, cửa sổ tần suất 5 phút theo client_ip
  → Alert nếu score ≥ risk_threshold (mặc định 60)
  → IncidentManager (W = 5 phút, K = 3)
  → Kafka topic security-alerts + SSE /api/stream/alerts và /api/stream/incidents
  → (sau commit, @Async) Ollama chỉ khi incident vừa được tạo và profile llm bật
```

Tham số vận hành đang nằm trong `application.yaml`:

| Tham số | Giá trị | Hệ quả |
|---------|---------|--------|
| `scoring.window-minutes` | 5 | `freq_bonus` chỉ nhìn hit cùng IP trong 5 phút |
| `incident.window-minutes` | 5 | Incident OPEN cũ hơn cửa sổ bị đóng tự động trước khi xét promote |
| `incident.aggregate-threshold` | 3 | Dưới 3 alert MEDIUM/HIGH cùng khoá thì chưa có incident |
| `risk_threshold` app | 60 | Score 59 không thành alert |
| Severity | &lt;40 LOW, 40–69 MEDIUM, 70–84 HIGH, ≥85 CRITICAL | CRITICAL mới đi nhánh IMMEDIATE |
| `llm.enabled` | false mặc định | `explanation_status = SKIPPED`, không HTTP |
| `llm.model` | `llama3.2` | Chỉ khi bật profile |
| `llm.timeout-seconds` | 20 | Quá hạn → FAILED, incident vẫn xem được |
| `llm.max-alerts` | 5 | Prompt không nhận quá 5 alert mới nhất |
| JWT access | 480 phút | Một ca demo không phải login lại giữa chừng |
| Redis rule đang bật | TTL 10 phút, xóa key khi admin sửa rule | Sửa weight có hiệu lực không cần đợi hết TTL |
| Kafka producer `acks` | 1 | Leader ghi xong là producer trả, không đợi ISR |

Cache rule: TTL 10 phút là lưới an toàn. `CompiledRuleCache` và `EnabledRulesRedisCache` xóa key Redis khi rule đổi, nên lần event sau dùng pattern mới. Weight trên `detection_hits` là bản sao lúc khớp; sửa weight sau không viết lại hit cũ.

---

### 4.3 Giải pháp phát hiện

#### Bộ rule đang seed

14 dòng trong `V2__seed_detection_rules.sql`, `source = AI_MINED`, `rule_version = 1.0.0`. Bốn rule path traversal soi field `path`. Sáu rule SQLi và bốn rule XSS soi field `query`. Không có rule command injection, dù HttpParams có 89 mẫu cmdi. Không có rule đọc POST body.

| Mã | Weight | Một mình trên HTTP 200 |
|----|-------:|------------------------|
| SQLI-TIME-001 (`sleep` / `pg_sleep` / `benchmark` / `waitfor delay`) | 60 | Đạt ngưỡng 60, severity MEDIUM |
| SQLI-UNION-001 | 55 | 55 &lt; 60, chưa alert |
| SQLI-STACKED-001 | 50 | Chưa alert |
| PT-DOUBLE-001, XSS-SCRIPT-001 | 55 | Chưa alert |
| PT-ENCODED-001, XSS-JSURI-001 | 50 | Chưa alert |
| PT-BACKSLASH-001, PT-DOTDOT-001, XSS-EVENT-001 | 45 | Chưa alert |
| SQLI-BOOLEAN-001, SQLI-BOOLEAN-002, XSS-ENCODED-001 | 40 | Chưa alert |
| SQLI-COMMENT-001 | 35 | Chưa alert |

Hệ quả cố ý của ngưỡng 60: **chỉ TIME-001 tự mình tạo alert** khi response là 200 và chưa có hit khác trong cửa sổ. UNION, XSS script, hay `../` cần thêm hit cùng request, hoặc `status_bonus`, hoặc `freq_bonus`, mới qua 60. Đây là chỗ giải pháp khác một bộ chữ ký “khớp là chặn” của WAF inline: khớp rule yếu vẫn được ghi hit, nhưng chưa thành việc SOC.

Coverage đã đo trên CSV, nhắc lại để gắn với bộ weight này, không phải số mới:

| Category | Held-out | Adversarial | FPR benign |
|----------|----------|-------------|------------|
| PATH_TRAVERSAL | 48,00% trên 175 | 51,36% trên 808 | 0 |
| SQLI | 50,52% trên 6.598 | 49,58% trên 32.851 | 0 |
| XSS | 68,12% trên 229 | 69,85% trên 1.111 | 0 |

`PT-DOUBLE-001` nặng 55 điểm nhưng recall held-out 0/175. Trọng số cao không đồng nghĩa phủ nhiều mẫu. Giải pháp giữ rule này vì adversarial có 2/808 hit và precision trên benign vẫn 1; báo cáo đo RQ1 phải tách coverage khỏi trọng số.

#### Công thức điểm

```text
base         = min(100, tổng weight các hit của event)
freq_bonus   = 0 nếu số DetectionHit cùng client_ip trong 5 phút < 2
               ngược lại min(20, 2 × (số hit − 1))
status_bonus = 5 nếu status ∈ {403, 404, 500} và base > 0
score        = min(100, base + freq_bonus + status_bonus)
```

`freq_bonus` đếm **dòng hit**, không đếm request. Trần 20 đạt khi có 11 hit trong cửa sổ, vì `2 × (11 − 1) = 20`. Bonus này không kéo một payload lạ thành CRITICAL nếu base thấp: comment SQL weight 35, dù trần tần suất và status, tối đa 35 + 20 + 5 = 60, đúng ngưỡng MEDIUM.

Ba ví dụ tính tay, cùng công thức trong `RiskScorer`:

| Tình huống | Tính | Score | Alert | Severity | Việc SOC |
|------------|------|------:|------|----------|----------|
| `q=apple`, không hit, HTTP 200 | base 0 | 0 | Không | LOW | Không incident |
| Một hit TIME-001, HTTP 200, cửa sổ còn 1 hit | 60 + 0 + 0 | 60 | Có | MEDIUM | Chưa incident nếu đây là alert MEDIUM thứ nhất hoặc thứ hai |
| TIME-001 và UNION-001 cùng request | min(100, 60+55) = 100 | 100 | Có | CRITICAL | IMMEDIATE, một incident |
| Ba request TIME-001 cùng IP trong 5 phút | Lần 1: 60. Lần 2: 60+2. Lần 3: 60+4 | 60, 62, 64 | 3 alert | Cả ba MEDIUM | Lần 3 đủ K = 3 → một incident AGGREGATE |

Ba request TIME cùng IP: baseline một-một ra **3** incident, giải pháp ra **1**. Mức giảm (3−1)/3 = **66,7%**, trên mốc 15% của RQ2, với điều kiện detector đúng weight 60 và ba request nằm trong một cửa sổ. Một TIME từ một IP lạ chỉ còn lại alert, `incident_id` null. Đó là hành vi đúng của K = 3, không phải sót lỗi.

Status 404 cộng thêm 5. UNION-001 một mình trên 404 thành 55+5 = 60, MEDIUM. UNION-001 trên 200 vẫn 55, không alert. Giải pháp vì thế nhạy với mã HTTP của Juice Shop, không chỉ với chuỗi payload.

---

### 4.4 Giải pháp Incident

`IncidentManager.promote` chạy sau khi alert đã có. LOW bỏ qua. Alert đã gắn incident thì không xét lại.

Thứ tự đúng như code:

1. Tìm incident `OPEN` cùng `(app_id, client_ip)`.
2. Nếu `opened_at` cũ hơn 5 phút, đóng incident đó (`CLOSED`, ghi work note), coi như không còn incident mở.
3. Nếu còn incident mở trong cửa sổ, gắn alert vào đó. Tăng `alert_count`, `severity` lấy max, `score` lấy max. **Không** tạo incident mới, **không** gọi explain lại.
4. Nếu chưa có incident mở và severity là CRITICAL, tạo incident `promote_reason = IMMEDIATE`.
5. Nếu MEDIUM hoặc HIGH, đếm alert cùng khoá, cùng hai severity đó, chưa gắn incident, trong 5 phút. Đủ 3 thì tạo `AGGREGATE` và gắn cả nhóm. Chưa đủ thì alert đứng một mình.

Explain chỉ xếp hàng khi cờ `created = true`. Gắn thêm alert vào incident cũ không gọi Ollama. Đúng khác với các lab “mỗi alert một lần hỏi LLM”.

Work note hệ thống ghi câu ngắn lúc promote và lúc gắn thêm, nguồn `SYSTEM`, để UI có vết không phụ thuộc đoạn văn của model.

Một burst đã tính ở tuần 3 vẫn khớp luật này: 6 CRITICAL cùng IP thành 1 incident IMMEDIATE; 9 MEDIUM thành 3 incident AGGREGATE; 3 HIGH thành 1. Tổng 18 alert → 5 incident, giảm 72,2%, **nếu** điểm thực tế rơi đúng các khoảng 60–69, 70–84 và ≥85. Với bộ weight hiện tại, một payload TIME đơn không vào nhóm CRITICAL. Kịch bản 6 CRITICAL chỉ xảy ra khi một request ăn nhiều rule (ví dụ TIME + UNION = 100) hoặc khi tổng weight và bonus chạm 85. Báo cáo đo RQ2 phải dùng severity **sau scoring**, không dùng nhãn “payload nặng” thay cho score.

---

### 4.5 Giải pháp thời gian thực và mặt SOC

Trình duyệt không subscribe Kafka. Hai luồng SSE:

- `GET /api/stream/alerts` — alert mới, kể cả alert chưa đủ K.
- `GET /api/stream/incidents` — incident mới, alert được gắn, và đổi `explanation_status`.

Dashboard React (`frontend/src/App.jsx`) có bốn mặt: đăng nhập JWT, overview, danh sách alert, danh sách và chi tiết incident. Overview lọc theo `app_id`, vẽ timeline, top loại tấn công, top IP, top path. Chi tiết incident hiện N alert, từng hit `ruleCode`, category, weight, evidence, và ba nút: Xác nhận (`ACK`), Đóng (`CLOSED`), Giải thích lại. Dòng cố định trên khối giải thích: “AI hỗ trợ, có thể không chính xác.”

Độ trễ RQ3 đo từ lúc request tới Nginx tới lúc event SSE alert tới, không đo tới lúc `explanation_status` thành `READY`. Timeout Ollama 20 giây dài hơn hạn 5 giây của O1; nếu cộng hai đồng hồ, mọi ca bật LLM đều trượt RQ3 dù detect đã xong.

JWT role `admin` đứng trước API đọc incident. Ca lab seed một user. Access token 480 phút. Thiếu token thì `GET /api/incidents` trả 401; luồng này không đổi công thức điểm.

---

### 4.6 Giải pháp giải thích bằng LLM

Ollama là khối tùy chọn. Mặc định tắt.

| Việc | Cách làm |
|------|----------|
| Khi nào gọi | Chỉ incident **mới tạo**, sau `AFTER_COMMIT`, trên thread `@Async` |
| Khi tắt | `SKIPPED`, không mở HTTP, alert và incident vẫn hiện |
| Khi lỗi hoặc quá 20 giây | `FAILED`, ghi lỗi cắt 500 ký tự, consumer Kafka không nhận exception |
| Retry | `POST /api/incidents/{id}/explain` trả 202, xếp hàng lại, không giữ connection chờ model |
| Prompt | Tối đa 5 alert. Title cắt 180 ký tự, path 180, query 240, evidence 180, IP 64. Ký tự điều khiển bị thay bằng space |
| Đầu ra | Cắt 4.000 ký tự. Dòng `CONFIDENCE: LOW\|MEDIUM\|HIGH` tách khỏi thân; thiếu dòng thì confidence mặc định MEDIUM |
| Việc model không được làm | Không ghi `risk_score`, không bật/tắt rule, không đóng incident |

So với GenXSS, LLM ở đây không sinh SecRule và không quyết định chặn. So với WAFBooster, không có shadow RNN trên đường request. So với các lab Wazuh + Ollama triage mọi alert, model chỉ thấy incident đã promote và các hit do chính 14 rule tạo ra. Gắn alert thứ tư vào incident AGGREGATE không làm model viết lại.

---

### 4.7 Giải pháp dữ liệu để demo và để đo tầng S

Hai lệnh, cả hai đi qua Nginx, không gửi POST body.

| Lệnh | Số request | Nhãn |
|------|------------|------|
| `datasets/probes/scenarios.yaml` + `run.sh` | **30** scenario | **24** `expected: alert` (8 SQLi, 8 XSS, 8 path traversal), **6** `expected: clean` |
| `browse_clean.sh` | **50** path Juice Shop | Sạch: trang chủ, search `apple`/`juice`/…, API whoami, 10 sản phẩm, asset tĩnh |

24 probe `alert` không đồng nghĩa 24 alert sau scoring. Payload chỉ khớp rule weight 35–55 trên HTTP 200 nằm dưới ngưỡng. Ground truth kỳ vọng và alert thực tế là hai cột; tuần 5 mới đối chiếu. Ethics ghi trong runner: chỉ lab Juice Shop tự host.

Tầng L của tuần 3 (10.000 dòng, 80% benign, 20% tấn công, 20 sự kiện/giây) **không** nằm trong runner tuần 4. Runner là tầng S khoảng 50 sạch + 30 scenario, sát thiết kế O8 (~50 + ~30).

---

### 4.8 Chỗ giải pháp đứng so với hướng tương tự

| Hướng | Giải pháp của họ | Giải pháp đồ án trên cùng việc |
|-------|------------------|--------------------------------|
| WAFBooster, true rejection 21% → 96% trên 8 WAF | Shadow RNN sinh payload, cụm chuỗi, regex vá WAF đang chặn | Cụm và regex làm **offline**, 14 rule Java, weight 35–60, không vá WAF, không đo true rejection |
| GenXSS, 15 SecRule, khoảng 86% bypass XSS | GPT-4o viết rule ModSecurity cho XSS | 14 rule ba category, compiler ra regex trước khi seed, LLM online chỉ giải thích incident, model local `llama3.2`, timeout 20 giây |
| ML-WAF trên CSIC khoảng 61.000 request | Một nhãn normal/attack cho cả request | Nhãn vận hành là hit có mã rule. CSIC không có timestamp nên không chạy được K và W; đồ án gán thời gian ở tầng replay, còn lab Juice thì thời gian có sẵn trên log Nginx |
| SIEM một alert một case | Mỗi cảnh báo là một việc | 3 alert MEDIUM cùng IP trong 5 phút là 1 incident. 18 alert burst thiết kế thành 5 incident (giảm 72,2% khi severity đúng) |
| LLM triage mọi alert | Model được hỏi trên alert đơn | Model chỉ chạy khi `created = true`. Hai alert MEDIUM chưa đủ K không tốn một lần gọi Ollama |

Điểm giải pháp chấp nhận kém hơn bài báo inline: không chặn request, không thấy body, coverage SQLi held-out 50,52% thấp hơn các accuracy phân loại thường thấy trên split CSV. Đổi lại, mỗi hit truy được về mã rule và evidence, và đồng hồ detect không chứa thời gian model.

---

### 4.9 Giải pháp đáp ứng từng câu hỏi bằng cách nào

Chưa có kết quả chạy RQ1–RQ3 trong báo cáo này. Bảng dưới chỉ nói **cơ chế nào sẽ bị kết luận đụng tới** khi tuần 5 đo.

| Câu hỏi | Cơ chế sẽ bị đo | Tiêu chí đã chốt từ tuần 3 |
|---------|-----------------|----------------------------|
| RQ1 | 14 regex trên access log, không đổi weight giữa chừng | Coverage không thấp hơn held-out trừ 15 điểm: SQLI ≥ 35,52%, XSS ≥ 53,12%, PT ≥ 33,00%. FPR ≤ 2% |
| RQ2 | `IncidentManager` với K = 3, W = 5, ngưỡng 60, CRITICAL ≥ 85 | Giảm số incident ≥ 15% so với đếm một-một trên cùng các alert; CRITICAL bỏ sót = 0 |
| RQ3 | Kafka → score → alert → SSE, LLM ở `@Async` sau commit | p95 request → SSE alert &lt; 5 giây, tại ≥ 20 sự kiện/giây, trên ≥ 10.000 dòng |

Nếu ba probe TIME cùng IP ra 3 alert và 1 incident, H1 đúng trên burst đó (giảm 66,7%). Nếu 24 probe `expected: alert` rơi nhiều score dưới 60, mẫu số RQ2 nhỏ đi và kết luận phải ghi số alert thực, không ghi 24. Nếu p95 đo tới `READY` của Ollama, số đó không được đưa vào RQ3.

---

## Kết luận tuần 4

Giải pháp đề xuất là pipeline out-of-band với tham số đã khóa trong code: 14 rule AI_MINED, điểm cắt 100, ngưỡng 60, bonus tần suất tối đa 20, bonus status 5, incident K = 3 trong 5 phút, SSE tách khỏi Ollama. Chỉ `SQLI-TIME-001` (weight 60) tự tạo alert trên HTTP 200; các rule 35–55 cần hit cộng dồn hoặc bonus. LLM `llama3.2` timeout 20 giây, tối đa 5 alert, cắt thân 4.000 ký tự, tắt thì `SKIPPED`. Demo tầng S có 30 scenario (24 kỳ vọng alert, 6 sạch) và 50 request `browse_clean`.

So với WAFBooster và GenXSS, giải pháp không lấy 96% true rejection hay 86% bypass làm chỉ tiêu. So với SIEM một-một, cùng một IP bắn ba TIME trong 5 phút ra một incident thay vì ba. Số coverage offline và phép đếm 66,7% / 72,2% là mốc thiết kế. Bảng FP/FN trên Juice và replay 10.000 dòng thuộc tuần đo, không thuộc báo cáo giải pháp này.
