# Week 2 — Kiểm tra tính khả thi của đề xuất mới

| Trường | Giá trị |
|--------|---------|
| Nguồn | `docs/solution-design.md` v1.6 · `docs/rule-creation.md` v1.1 |
| Trạng thái design | **Approved for implementation** |
| Mục tuần 2 | Kiểm tra tính khả thi: bối cảnh mới, giải thuật mới, dữ liệu khác, đối chiếu nghiên cứu liên quan, khoảng trống so với bài báo gốc |

---

## 2. Kiểm tra tính khả thi của đề xuất mới

Tuần 1 đã chốt đề tài: **WAF Log Analyzer** — giám sát và phát hiện tấn công web từ access log, pipeline Kafka realtime, rule engine tự viết, Incident phân cấp, LLM hai vai trò (offline mine rule / online explain). Tuần này trả lời câu hỏi: **đề xuất đó có khả thi trên lab hiện có không**, khác gì so với các nghiên cứu đã công bố, và **lấp khoảng trống nào** của các bài báo/đường hướng gốc.

Kết luận ngắn: **khả thi trong phạm vi MVP đã khoá**. Ý tưởng không đòi GPU, cloud LLM, hay WAF inline; tái sử dụng dataset công khai và stack Compose local. Các bài báo “sinh rule WAF bằng AI/ML” **không nên (và phần lớn không thể) tái hiện nguyên bản** trong cùng môi trường — nhóm chỉ lấy ý tưởng detection engineering, không clone thí nghiệm của họ.

---

### 2.1 Đề xuất mới là gì

So với hướng phổ biến (WAF inline chặn request, hoặc train classifier ML trên payload), đề xuất của đồ án **đổi cả môi trường, giải thuật và cách dùng dữ liệu**.

#### 2.1.1 Bối cảnh / môi trường mới

| Khía cạnh | Hướng phổ biến trong tài liệu | Đề xuất của đồ án |
|-----------|-------------------------------|-------------------|
| Vị trí hệ thống | WAF **inline** trên reverse proxy (ModSecurity / CRS) — quyết định allow/block trước khi request vào app | **Out-of-band log analyzer**: Nginx chỉ ghi JSON access log; detection nằm **sau** Kafka, không chặn traffic |
| Mục tiêu vận hành | Bảo vệ ứng dụng (prevention) | **Giám sát – phát hiện – triage** cho SOC lab; map **NIST CSF Detect/Respond** |
| Runtime | ELK đầy đủ, Wazuh, hoặc microservice SIEM nặng (Elasticsearch, Redis, ML sidecar) | Compose gọn: Juice Shop + Nginx + Filebeat + Kafka KRaft + Spring + PostgreSQL + React; Ollama theo **profile `llm`** |
| Phạm vi nhìn thấy | Full HTTP (kể cả POST body, response) | **Chỉ access log**: `path` / `query` / `ua` / `raw` — **không** POST body (khớp §5.5) |
| Đối tượng demo | Dataset tĩnh (CSIC 2010, HttpParams) hoặc WAF production | **Lab sống**: OWASP Juice Shop qua Nginx + Attack Scenario Runner có nhãn |
| Ràng buộc tài nguyên | GPU, GPT-4o, 8 WAF thật, ELK đầy đủ | Máy sinh viên / máy chấm bài: **không** Kafka UI mặc định; rule-generator **offline**, không nằm Compose |

Môi trường mới này là lab **detection engineering + quản trị**, không phải sản phẩm WAF thương mại. Workload reuse OSS; phần tự viết là security backend và `detection-rule-generator/`.

#### 2.1.2 Giải thuật mới

Ba khối thuật toán — **không** dùng LLM/ML làm detector trên luồng Kafka.

**A. AI-assisted rule mining (offline)** — `docs/rule-creation.md`

```text
Corpora → normalize / dedupe / cluster / split (held-out ẩn với AI)
  → AI gen abstract rule (điều kiện logic, chưa regex)
  → reviewer / optimizer
  → compiler → DetectionRule JSON (regex Java-compatible)
  → evaluator (held-out + benign + adversarial)
  → human gate → rules/production/ → Flyway seed
```

Điểm khác các paper “LLM emit SecRule một phát”: AI **không** được emit regex làm artifact cuối; phải qua **abstract rule → compiler**, rồi **gate metric** (precision, recall, FPR, coverage trên held-out — không chỉ generation set). Runtime Spring chỉ `Pattern.compile` trên field đã chọn.

**B. Detect + score trên từng request (online, deterministic)**

```text
WebEvent → RuleEngine (regex theo target_field) → DetectionHit[]
  → score = min(100, base + freq_bonus(5 phút) + status_bonus)
  → Alert khi score ≥ threshold app (mặc định 60)
```

`base = min(100, sum(hit.weight))`; `freq_bonus ≤ 20`; `status_bonus = 5` nếu status ∈ {403, 404, 500} và `base > 0`. LLM **không** tham gia hit/miss.

**C. Incident promotion phân cấp + LLM explain async**

| Nhánh | Điều kiện | Hành vi |
|-------|-----------|---------|
| IMMEDIATE | severity = CRITICAL | Tạo/gắn Incident ngay theo key `(app_id, client_ip)`, cửa sổ **W = 5 phút** |
| AGGREGATE | MEDIUM / HIGH | Đủ **K = 3** alert chưa gắn cùng key → tạo Incident |
| Chưa promote | count &lt; K | Chỉ Alert; **chưa** gọi LLM |

LLM online (Ollama) **chỉ giải thích Incident mới tạo**, grounded bằng tối đa 5 alert + rule hits đã sanitize; gọi async, không block ingest/SSE. Đây là giải thuật **workflow SOC**, không phải classifier.

#### 2.1.3 Dữ liệu khác

Cùng nguồn công khai nhưng **vai trò khác** các bài báo gốc (họ train classifier; đồ án **mine rule** + **đo runtime trên log sống**).

| Vai trò | Dataset | Cách dùng trong đề xuất | Khác bài báo gốc |
|---------|---------|-------------------------|------------------|
| Generation | HttpParams (sqli/xss/path-traversal); FuzzDB extract theo category; SQLi ~30k (`modified-sql-dataset.csv`) | AI **được nhìn** để sinh abstract rule; không dump cả FuzzDB vào prompt | HttpParams gốc dùng cho **anomaly/ML WAF**; CSIC 2010 dùng full HTTP request |
| Evaluation (held-out) | 30% sau dedupe, AI **không** thấy | Precision / recall / coverage | Paper ML thường 80/20 trên cùng feature vector, không tách “AI không được nhìn” |
| Benign (FPR) | HttpParams `norm` (~19k giá trị tham số sạch) | Gate FPR &lt; 2% trước khi vào `production/` | Nhiều paper WAF đo trên traffic WAF, không có cổng FPR riêng cho rule regex |
| Adversarial | Case / whitespace / encoding / `..%2f` / double encoding | Đo generalization, đặc biệt Path Traversal | WAFBooster **sinh** payload để bypass WAF; đồ án **biến thể hoá** payload đã có để stress rule |
| Runtime E2E | Juice Shop access log + `datasets/probes` (~50 clean + ~30 probe) | FP/FN thô trên lab sống (tầng B) | CSIC 2010 **không có timestamp / host đa dạng** — không đo được correlation 5 phút |
| Không dùng MVP | OWASP Benchmark, POST body, full CSIC request | Phase 2 | Tránh phình scope |

Ràng buộc dữ liệu then chốt: payload chỉ có nghĩa trên access log. Rule phụ thuộc POST body bị loại ngay từ spec.

---

### 2.2 Nghiên cứu liên quan đã tìm được

Đã rà bốn nhóm sát “đề xuất mới”. Không có bài nào trùng **đủ** tổ hợp: log analyzer out-of-band + AI mine rule offline có held-out + Incident phân cấp + LLM explain async trên lab Juice/Kafka/Spring.

#### Nhóm 1 — Phát hiện tấn công web bằng ML trên payload (bài báo / dataset gốc)

| Công trình | Ý chính | Liên quan đề xuất |
|------------|---------|-------------------|
| **HTTP CSIC 2010** (CSIC, Tây Ban Nha) | ~36k request bình thường + ~25k bất thường; benchmark WAF/IDS; nhiều công trình anomaly detection train trên “normal only” | Nguồn gián tiếp của HttpParams (payload benign). Dataset **lab-generated**, host đồng nhất, **không timestamp** |
| **HttpParamsDataset** (Morzeux, 2015–2016) | Giá trị tham số HTTP: 19 304 `norm` + 11 763 `anom` (SQLi 10 852, XSS 532, path-traversal 290, cmdi 89); lấy từ CSIC, sqlmap, xssya, Vega, FuzzDB | **Dataset PRIMARY** của rule-generator. Gốc: đánh giá **anomaly detection** trong luận văn, không phải SOC pipeline |
| **ML-based WAF** (Stojnic et al.; SVM + TF-IDF) | Classifier SQLi/XSS/path-traversal/cmdi trên HttpParams + ECML | Cùng loại tấn công; **khác hẳn** cách làm: vector ML vs regex có `rule_id` + evidence |
| **WAF + feature engineering** (Hindawi 2022) | Parse URL/payload/header, feature (độ dài, ký tự đặc biệt, …), accuracy cao trên CSIC/HttpParams | Inline/request classifier; không Kafka, không incident, không giải thích SOC |

#### Nhóm 2 — Tự động sinh chữ ký / rule cho WAF inline (gần “AI rule mining” nhất)

| Công trình | Ý chính | Liên quan đề xuất |
|------------|---------|-------------------|
| **WAFBooster** (Wu et al., arXiv:2501.14008) | Shadow RNN bắt chước WAF đen; sinh payload đột biến; cluster substring (edit distance); sinh regex đơn giản. Đánh giá 8 WAF thật: true rejection 21% → 96%, không false rejection | Cùng mục tiêu “rule/signature từ payload”. Khác: **boost WAF inline**, cần train RNN, GPU, WAF production |
| **GenXSS** (Babaey & Ravindran, arXiv:2504.08176) | GPT-4o sinh payload XSS → validate app lỗ hổng → cluster (TF-IDF+HAC / SequenceMatcher+DBSCAN) → LLM sinh **ModSecurity SecRule**; 15 rule chặn ~86% bypass trước đó | Cùng ý “cluster → LLM sinh rule”. Khác: **chỉ XSS**, phụ thuộc GPT-4o + ModSecurity CRS + RLHF, không access log, không 3 category |

#### Nhóm 3 — SIEM / pipeline log realtime (gần “môi trường Kafka” nhất)

| Công trình / hệ thống | Ý chính | Liên quan đề xuất |
|-----------------------|---------|-------------------|
| **SIEM-in-a-box** (ieeta-pt) | Filebeat → Kafka → Logstash → Elasticsearch/Kibana; có collector Nginx | **Adapt collector Filebeat**; không clone ELK làm detector |
| **elk-lab** | Juice Shop + Nginx + ELK | Tham khảo config Nginx; **không** fork stack detection |
| **Watch-Tower** (chethanhrx) | Spring + Kafka (`raw-logs`, `security-alerts`) + React; kèm ML sidecar, Elasticsearch, Redis, WebSocket | Tham khảo kiến trúc — **không fork**. Đồ án: SSE, normalize in-process, không ML online |
| **SIEM Optimized Correlator** (PMC11314677, 2024) | Thay regex SIEM bằng Hyperscan; correlation đa lớp trên log thiết bị | Cùng tinh thần rule/regex trên log; rule **do chuyên gia viết**, không AI mining; không riêng web injection |
| Lab ELK + DVWA / Wazuh | Filebeat + rule Kibana/Wazuh XML | Rule thủ công, alert 1-1, không lab mine rule có eval 2 tầng |

#### Nhóm 4 — LLM giải thích / triage SOC (gần “LLM online” nhất)

Các hệ thống kiểu Wazuh/n8n/Ollama, Security Onion + local LLM, “LLM-assisted alert triage”: LLM **phân loại hoặc kể lại alert có sẵn** của SIEM. Điểm chung với đề xuất: Ollama local, không đẩy log lên cloud. Khác: họ **không sở hữu detector**, không mine rule từ corpora, thường để LLM ảnh hưởng verdict — đồ án **cấm** LLM trên critical path detect.

---

### 2.3 Ưu / nhược điểm và điểm mới so với nghiên cứu liên quan

#### 2.3.1 Bảng đối chiếu

| Tiêu chí | ML-WAF / CSIC–HttpParams | WAFBooster / GenXSS | SIEM ELK / Watch-Tower | **Đề xuất đồ án** |
|----------|--------------------------|---------------------|------------------------|-------------------|
| Vị trí | Classifier trên request/payload | **Inline WAF** (ModSec / WAF thật) | Thu thập log đa nguồn | **Log analyzer** sau Nginx JSON |
| Detector runtime | SVM / NN — khó giải thích từng hit | Regex/SecRule gắn vào WAF | Rule SIEM thủ công hoặc ML sidecar | Regex Java + `rule_id` + evidence; **LLM không detect** |
| Nguồn rule | Học từ feature | Tự sinh signature/SecRule | Chuyên gia / CRS | AI **abstract rule** → compiler → eval → human gate |
| Eval rule | Accuracy trên split dataset | Bypass rate trên WAF | Thường không có held-out corpora | **Hai tầng:** (A) corpus P/R/FPR/coverage; (B) Juice probes |
| Alert → việc SOC | Thường dừng ở nhãn attack/normal | Block/allow | Alert flood hoặc case TheHive | Alert 1-1 event; **Incident 1:N** theo IMMEDIATE/AGGREGATE |
| LLM | Không, hoặc chính là detector | Gen payload + gen rule (cloud GPT) | Triage assistant (một số lab) | **Tách 2 vai trò**; explain async, grounded hits |
| Tài nguyên lab SV | Train lại được trên CSV | GPU + WAF thật / GPT-4o — **không** | ELK nặng | Compose + Python offline + Ollama tắt được |
| Hạn chế tầm nhìn | Nhiều paper dùng POST/full request | Full HTTP tới WAF | Đa log | **Chủ đích** chỉ access log — FN với tấn công chỉ nằm body |

#### 2.3.2 Ưu điểm của đề xuất (trong ngữ cảnh BTL / lab)

1. **Giải thích được:** mỗi hit có pattern, evidence, weight — phù hợp báo cáo quản trị và UI SOC; classifier ML-WAF không cho `rule_id` tương đương.
2. **Tách rủi ro LLM:** mine rule offline có cổng FPR; explain online hỏng thì Incident vẫn xem được (`FAILED`/`SKIPPED`).
3. **Giảm alert fatigue ngay từ policy:** không 1 alert = 1 incident; CRITICAL promote ngay, MEDIUM/HIGH gom K=3.
4. **Nằm trong tài nguyên thật:** không ModSec so sánh, không GPU, không cloud LLM cho demo.
5. **Hợp đồng artifact rõ:** JSON `rules/production/` map 1-1 entity `DetectionRule` (Java 25 / Flyway) — paper GenXSS dừng ở SecRule ModSecurity.

#### 2.3.3 Nhược điểm / rủi ro (phải chấp nhận trong MVP)

1. **Không prevention:** attacker vẫn vào Juice Shop; hệ thống chỉ thấy log. Đây là lựa chọn scope, không phải thiếu sót ẩn.
2. **Signature không bắt zero-day:** encoding lạ, payload chỉ trong POST body, XSS DOM-only sẽ **FN**. Adversarial set giảm một phần, không thay WAFBooster.
3. **Phụ thuộc chất lượng cluster + human review:** AI có thể overfit 1 payload; gate held-out + FPR là bắt buộc, không phải tuỳ chọn.
4. **Coverage XSS/Path Traversal yếu hơn SQLi** trên HttpParams gốc (532 XSS, 290 traversal vs ~10k SQLi) — phải bổ sung FuzzDB / list riêng, không kỳ vọng recall đồng đều 3 category.
5. **Không so sánh ModSecurity CRS** (đã khoá out-of-scope) — không tuyên bố “tốt hơn WAFBooster/GenXSS” trên cùng metric bypass.

#### 2.3.4 Điểm mới (novelty ở mức đồ án, không phải paper top-conference)

Không tuyên bố “lần đầu AI sinh regex”. Điểm mới là **tổ hợp có chủ đích**, khớp khoảng trống các hướng trên:

1. Đưa **AI rule mining** từ ngữ cảnh **WAF inline / ModSecurity** sang ngữ cảnh **access-log SOC** (field `path|query|ua|raw`, regex Java, seed Flyway).
2. Bắt AI sinh **abstract rule trước**, regex sau — giảm “LLM hallucinate SecRule”.
3. **Eval 2 tầng** (corpora held-out/benign/adversarial **và** Juice E2E) ngay trong cùng monorepo.
4. **LLM online ≠ detector**; chỉ explain sau promote Incident — khác cả ML-WAF lẫn lab “Ollama triage mọi alert”.
5. **Incident policy số hoá** (W=5p, K=3, key `app_id+IP`) gắn với scoring 0–100, phục vụ chương NIST CSF chứ không chỉ dashboard ELK.

---

### 2.4 Các câu hỏi khả thi

#### 2.4.1 Ý tưởng mới có thực hiện được với ngữ cảnh / tài nguyên hiện có không?

**Có — trong MVP đã khoá (§3 solution-design).** Đối chiếu từng giả định:

| Giả định đề xuất | Tài nguyên hiện có | Khả thi? |
|------------------|--------------------|----------|
| Pipeline log realtime | Docker Compose; Filebeat mẫu SIEM-in-a-box; Kafka KRaft; Nginx JSON schema §5.4 đã chốt | Có |
| Normalize + persist | Spring Boot 3, Java 25, PostgreSQL 16, Flyway; schema `WebEvent` / `DetectionRule` §7.3 | Có |
| 8–15 rule 3 category | Module `detection-rule-generator/` đã có pipeline Python, corpora, cluster cards, prompts, evaluator; thiếu thì seed `HAND` | Có |
| Không GPU / không cloud cho demo | Runtime regex; Ollama **profile**, tắt được; rule mining bằng Cursor/local, không trên Kafka thread | Có |
| LLM explain không chặn detect | `@Async` sau commit; timeout → `FAILED`; UI ghi AI-assisted | Có |
| Đo được O8 | Tầng A: evaluator sẵn; tầng B: `datasets/probes` + ~50/~30 request — khối lượng nhỏ, không cần OWASP Benchmark | Có |
| Máy yếu | Không Kafka UI; không ELK; rule-gen chạy khi cần, không trong Compose | Đã mitigation |

Điều kiện biên (không làm giả khả thi): **không** đo zero-day, **không** so sánh CRS, **không** DVWA multi-app, **không** FP-loop tự chỉnh weight. Các hạng mục đó nằm §3.2 — nếu đưa vào, câu trả lời khả thi sẽ **không còn đúng**.

Rủi ro còn lại đã có mitigation trong design: parse log brittle (schema JSON cố định), rule overfit (held-out + human gate), LLM bịa (ground bằng hits), alert noise (threshold 60 + K=3).

#### 2.4.2 Các đề xuất trong bài báo đã công bố có thực hiện lại được không? Trong cùng môi trường ý tưởng mới?

Cùng môi trường = lab Compose local, access log Nginx, không ModSec làm detector, không GPU/GPT-4o bắt buộc, thời lượng BTL.

| Đề xuất gốc | Tái hiện nguyên bản trên lab này? | Có nên làm không? | Việc được phép lấy |
|-------------|-----------------------------------|-------------------|---------------------|
| Anomaly/ML WAF trên CSIC hoặc HttpParams (SVM, NN, feature engineering) | **Một phần:** CSV đã có, train sklearn được | **Không** — ML/LLM detector online **out of scope** §3.2 | Dùng **dữ liệu** HttpParams/FuzzDB; không dùng classifier làm runtime |
| WAFBooster (shadow RNN + 8 WAF) | **Không:** cần GPU, shadow model, WAF đen thật, payload generation RNN | Không | Ý tưởng cluster substring → regex; eval FPR; **không** copy pipeline |
| GenXSS (GPT-4o + ModSec CRS + RLHF, chỉ XSS) | **Không / rất mỏng:** cần API cloud, ModSecurity DetectionOnly (đã cấm), app Brute Logic | Không | Ý tưởng cluster → prompt LLM sinh rule; số lượng ~15 rule XSS là tham chiếu coverage, không phải metric nộp |
| SIEM-in-a-box / elk-lab đầy đủ | **Có kỹ thuật** (kéo ELK) nhưng phá ràng buộc máy chấm bài và “tự viết detector” | Không clone | **Adapt Filebeat Nginx → Kafka**; tự schema JSON |
| Watch-Tower | **Không fork** (trùng đồ án + stack nặng: ES, Redis, ML sidecar, WS) | Không | Tham khảo topic Kafka + Spring; đồ án đổi SSE, 2 topic, normalize in-process |
| Hyperscan SIEM correlator | **Không** trong MVP (native lib, không web-injection-specific) | Không | Giữ Java regex; tối ưu matching = phase sau |
| LLM SOC triage (Wazuh + Ollama) | **Một phần:** Ollama local chạy được | Chỉ lấy **explain**, không lấy auto-verdict / auto-block | Profile `llm` + sanitize prompt |

**Tóm lại:** không tái hiện thí nghiệm các paper WAF/ML như “baseline số” trên cùng lab — môi trường khác (log vs inline, regex vs NN, 3 category vs XSS-only). Baseline **nội bộ** của đồ án là: (1) rule `HAND` vs `AI_MINED` trên cùng evaluator; (2) FP/FN trên Juice probes; (3) latency request → SSE &lt; 5 giây. Đó là cách so sánh **thực hiện được**.

#### 2.4.3 Đề xuất giải quyết khoảng trống nào trong bài báo / đường hướng gốc?

“Bài báo gốc” ở đây là **đường nghiên cứu mà đồ án kế thừa dữ liệu hoặc ý tưởng**, không phải một paper duy nhất.

**Khoảng trống 1 — HttpParams / CSIC / ML-WAF**

Gốc giải bài toán: *phân loại payload/request thành normal vs attack* (thường accuracy trên split tĩnh).  
**Thiếu:** pipeline realtime; rule giải thích được; vòng đời Alert→Incident; dữ liệu có timestamp để correlation; ràng buộc access log thật; khung quản trị.  
**Đồ án lấp:** dùng lại payload làm **corpora mine rule**, rồi chạy detector trên **log sống** Juice Shop; scoring + cửa sổ 5 phút (CSIC không làm được vì thiếu time/host).

**Khoảng trống 2 — WAFBooster / GenXSS**

Gốc giải: *vá lỗ hổng chữ ký WAF inline* (bypass → signature/SecRule mới).  
**Thiếu:** ngữ cảnh **không có** ModSecurity; không có SOC triage; LLM/RNN nằm gần vòng detect; GenXSS chỉ XSS; khó chạy trên máy SV.  
**Đồ án lấp:** chuyển “cluster → sinh rule” sang **lab offline** với abstract rule, held-out, FPR trên `norm`, export Spring; runtime **không** gọi LLM; thêm XSS **và** SQLi + Path Traversal trên cùng hợp đồng `DetectionRule`.

**Khoảng trống 3 — SIEM-in-a-box / Watch-Tower / ELK lab**

Gốc giải: *thu thập và hiện thị log/alert*.  
**Thiếu:** nguồn rule có đánh giá (thường rule tay hoặc ML hộp đen); tách Alert vs Incident; LLM chỉ explain; báo cáo NIST trên chính module tự viết.  
**Đồ án lấp:** detection engineering có provenance (`source`, `generator_rule_id`, `rule_version`); policy promote số hoá; LLM hẹp; workload gọn hơn ELK.

**Khoảng trống 4 — LLM SOC explain**

Gốc giải: *kể lại alert SIEM*.  
**Thiếu:** detector và rule mining đi kèm, nên explain không gắn `DetectionHit` do chính hệ thống tạo.  
**Đồ án lấp:** explain **bắt buộc grounded** bằng hits của rule đã eval; không để LLM đổi score.

Một câu: **bài gốc cho dataset hoặc cho WAF/SIEM; đồ án ghép thành hệ giám sát access log có rule AI-mined đã đo, incident có chính sách, và LLM không được detect.**

---

## Kết luận tuần 2

| Câu hỏi | Trả lời |
|---------|---------|
| Đề xuất mới là gì? | Đổi **môi trường** (out-of-band log analyzer, lab Compose, NIST), **giải thuật** (AI mine abstract rule offline + regex score + incident phân cấp + LLM explain async), **dữ liệu** (corpora để mine/eval, Juice log để E2E — không train classifier) |
| Có nghiên cứu liên quan? | Có: ML-WAF/HttpParams/CSIC; WAFBooster; GenXSS; SIEM-in-a-box; Watch-Tower; LLM SOC triage |
| Ưu / nhược / điểm mới? | Ưu: giải thích được, vừa tài nguyên, giảm noise bằng Incident. Nhược: không block, FN body/zero-day, không so sánh CRS. Mới: tổ hợp mining→runtime log→promote→explain, không phải thuật toán chưa từng có |
| Khả thi với tài nguyên hiện có? | **Có**, đúng MVP đã khoá |
| Tái hiện paper gốc cùng lab? | **Không nguyên bản**; chỉ tái sử dụng dữ liệu + ý tưởng; baseline nội bộ bằng evaluator + Juice probes |
| Khoảng trống lấp được? | Từ “classifier/WAF inline/SIEM thu log” sang **SOC log analyzer có rule đã eval và LLM không nằm critical path** |

Đề xuất **đủ khả thi để triển khai**. Bước kỹ thuật tiếp theo không phải đổi hướng nghiên cứu, mà hiện thực theo lộ trình: Compose/ingest → rule engine + seed từ `detection-rule-generator` → Alert/Incident/SSE → SOC UI + Ollama explain → đo FP/FN và chương NIST.
