# Tuần 3 — Dàn ý nghiên cứu chi tiết

| Trường | Giá trị |
|--------|---------|
| Môn | IT6027 · nhóm G21 · lớp 26.1A01 |
| Nguồn | `docs/solution-design.md` v1.6 · `docs/rule-creation.md` v1.1 · `docs/report/week/week-two.md` · `detection-rule-generator/evaluator/reports/metrics.md` |
| Mục tuần 3 | Chuyển đề xuất đã chốt ở tuần 1–2 thành câu hỏi nghiên cứu, cấu trúc thí nghiệm, và dàn ý nghiên cứu thử |
| Trạng thái số liệu | Coverage/FPR offline **đã đo**. Thí nghiệm stream ≥ 10.000 dòng và policy Incident **là thiết kế**, chưa phải kết quả chạy |

---

## 3. Lập dàn ý nghiên cứu chi tiết

Tuần 1 chốt đề tài **WAF Log Analyzer**: access log Nginx → Kafka → rule engine tự viết → điểm rủi ro 0–100 → Alert → Incident → SSE, LLM tách hai vai trò (mine rule offline / giải thích Incident online). Tuần 2 kết luận đề xuất **khả thi trong MVP** và không trùng đủ bốn hướng đã công bố (ML-WAF, sinh chữ ký WAF inline, SIEM thu log, LLM triage). Tuần 3 biến kết luận đó thành **kế hoạch nghiên cứu đo được**: ba câu hỏi, dữ liệu từng tầng, cách đưa phương pháp của các bài báo sang log thời gian thực, và một thí nghiệm thử có tiêu chí đạt/không đạt — kể cả mốc **15%**.

Một câu định vị thí nghiệm: đồ án không hỏi “regex có accuracy cao hơn SVM trên CSIC hay không”. Đồ án hỏi **phương pháp đã điều chỉnh** (rule đã qua cổng FPR, cộng chính sách Incident) còn đứng vững khi dữ liệu từ vài chục request lab thành luồng hàng chục nghìn dòng có mốc thời gian hay không, và tải triage có giảm thật hay không.

---

### 3.1 Mô tả: từ ý tưởng thành câu hỏi và cấu trúc

Ý tưởng vận hành gồm ba quyết định đã khoá, mỗi quyết định sinh một câu hỏi vì mỗi quyết định có thể sai theo một kiểu khác nhau.

| Quyết định đã khoá | Có thể sai kiểu gì | Câu hỏi phải trả lời bằng số |
|--------------------|--------------------|------------------------------|
| 14 rule regex, LLM không detect | Coverage trên log sống tụt so với CSV tĩnh | RQ1 |
| Alert khi `score ≥ 60`; Incident chỉ khi CRITICAL hoặc đủ K = 3 | Giảm incident nhưng bỏ sót tấn công nặng, hoặc không giảm đủ 15% | RQ2 |
| Pipeline Kafka, hạn request → alert &lt; 5 giây | Đúng trên ~80 request, vỡ khi replay lớn | RQ3 |

Cấu trúc nghiên cứu là **thực nghiệm có đối chứng trên lab**, hai tầng đo (corpus offline và luồng log), ba biến độc lập. Không có nhánh train classifier. Baseline nội bộ là chính sách “một alert một incident” và chính bộ rule đo trên held-out tĩnh — không phải ModSecurity, không phải WAFBooster chạy lại.

---

### 3.2 Ba câu hỏi nghiên cứu

**RQ1 — Coverage khi rời CSV tĩnh.**  
Trên đúng 14 rule đã pass cổng evaluator (precision ≥ 0,80 và FPR ≤ 0,02), coverage ruleset theo category trên access log có mốc thời gian có lệch quá **15 điểm phần trăm** so với held-out tĩnh đã đo hay không? Mốc held-out hiện có: **SQLI 50,52%** (6.598 mẫu), **XSS 68,12%** (229 mẫu), **PATH_TRAVERSAL 48,00%** (175 mẫu). FPR trên phần benign của luồng phải ≤ **2%**.

**RQ2 — Tải triage và mốc 15%.**  
Trên cùng một luồng log có nhãn, chính sách Incident (IMMEDIATE khi severity CRITICAL, tức score ≥ 85; AGGREGATE khi đủ **K = 3** alert MEDIUM/HIGH cùng khoá `(app_id, client_ip)` trong cửa sổ **W = 5 phút**) có giảm số incident phải triage **ít nhất 15%** so với baseline một alert một incident hay không, trong khi **mọi** alert CRITICAL vẫn được gắn vào một incident?

**RQ3 — Ràng buộc thời gian thực trên dữ liệu lớn.**  
Với ngưỡng alert mặc định **60**, phân vị 95 của độ trễ từ request tới Nginx đến lúc alert xuất hiện trên `GET /api/stream/alerts` có **&lt; 5 giây** khi replay **≥ 10.000** dòng (tối thiểu **20** sự kiện/giây) hay không?

Ba câu này là dạng kiểm định được: mỗi câu có mẫu số, có ngưỡng, có baseline. RQ2 là chỗ đặt câu hỏi “hơn 15%” vào đúng đại lượng mà chính sách của đồ án thực sự thay đổi (số incident), không gắn nhầm vào accuracy của WAFBooster.

---

### 3.3 Cấu trúc nghiên cứu: phương pháp và dữ liệu

#### Phương pháp

Thiết kế một nhân tố tại một thời điểm, trên cùng bộ rule 14 mã, để hiệu ứng không bị trộn.

| Nhân tố | Mức A (baseline) | Mức B (đề xuất) |
|---------|------------------|-----------------|
| Chính sách việc SOC | 1 alert = 1 incident | IMMEDIATE / AGGREGATE, K = 3, W = 5 phút, khoá `(app_id, client_ip)` |
| Quy mô | S: ~50 request sạch + ~30 probe ≈ **80** request lab sống | L: replay **≥ 10.000** dòng access log JSON |
| Nhịp | Bấm probe tay / script `datasets/probes` | ≥ **20** sự kiện/giây, kéo khoảng 10.000/20 = **500 giây** nếu đều |

Công thức điểm giữ nguyên solution-design §7.4, vì RQ1–RQ3 đo hệ đã chốt chứ không đổi trọng số giữa chừng:

```text
base        = min(100, tổng weight của hit)
freq_bonus  = 0 nếu < 2 hit cùng client_ip trong 5 phút
              ngược lại min(20, 2 × (số hit − 1))
status_bonus = 5 nếu status ∈ {403, 404, 500} và base > 0
score       = min(100, base + freq_bonus + status_bonus)
alert       ⟺ score ≥ risk_threshold (mặc định 60)
```

Severity: MEDIUM 40–69, HIGH 70–84, CRITICAL ≥ 85. Alert vẫn chỉ tạo khi score ≥ ngưỡng app, nên một hit yếu dưới 60 không thành alert và không vào mẫu số của RQ2.

Chỉ số thu:

| Chỉ số | Công thức vận hành | Câu hỏi |
|--------|--------------------|---------|
| Coverage category | Số mẫu tấn công khớp **ít nhất một** rule của category / số mẫu tấn công category | RQ1 |
| Precision, FPR | Theo cổng sẵn có: precision ≥ 0,80; FPR ≤ 0,02 | RQ1 |
| Số alert, số incident | Đếm bản ghi sau một lần chạy cố định | RQ2 |
| Mức giảm incident | `(I_baseline − I_đề_xuất) / I_baseline` | RQ2 |
| CRITICAL bỏ sót | Số alert CRITICAL có `incident_id` null sau promote | RQ2, kỳ vọng **0** |
| p95 độ trễ | `t_SSE − t_request` trên các request có alert | RQ3 |

LLM explain **không** nằm trong đồng hồ đo. Profile `llm` tắt, `explanation_status = SKIPPED`. Nếu trộn thời gian Ollama vào p95, RQ3 không còn đo pipeline phát hiện.

#### Dữ liệu cần thu và dữ liệu đã có

| Tầng | Quy mô đã biết | Mốc thời gian | Vai trò |
|------|----------------|---------------|---------|
| HttpParams (Morzeux, 2015–2016) | **19.304** `norm` + **11.763** `anom` = **31.067** giá trị. Anom: SQLi 10.852, XSS 532, path-traversal 290, cmdi 89 | Không | Corpora mine + benign đo FPR. Cmdi **không** có rule trong 14 mã MVP |
| Held-out tấn công (đã đo) | SQLI **6.598**, XSS **229**, PT **175**. Tổng **7.002** | Không | Mốc coverage RQ1 |
| Adversarial (đã đo) | SQLI **32.851**, XSS **1.111**, PT **808**. Tổng **34.770** | Không | Đo biến thể encoding; không thay replay Kafka |
| CSIC 2010 | khoảng **36.000** thường + **25.000** bất thường ≈ **61.000** request | Không | Đối chiếu quy mô bài ML-WAF. Không đưa vào runtime MVP |
| Lab Juice (O8) | ~**50** sạch + ~**30** probe ≈ **80** | Có, log Nginx sống | Tầng S, ground truth tuần 4 |
| Replay lớn (cần sinh) | **≥ 10.000** dòng JSON access log | Có, gán khi sinh | Tầng L cho RQ1 và RQ3 |

Tỉ lệ quy mô so với lab ~80 request: HttpParams ≈ **388 lần** (31.067/80), adversarial SQLi đơn ≈ **1.095 lần** (32.851/30), replay 10.000 dòng ≈ **125 lần**. “Dữ liệu lớn” trong đồ án là mốc **10.000 dòng có timestamp**, không phải toàn bộ CSIC.

Cách sinh replay, vì HttpParams và CSIC không có `event_time` nên không tự chạy được cửa sổ 5 phút:

- Mỗi giá trị tham số bọc thành một dòng Nginx JSON: `host`, `path`, `query`, `status`, `user_agent`, `raw`.
- Gán `event_time` tăng dần và `client_ip` theo kịch bản (mục 3.6), để `freq_bonus` và khoá Incident có việc để làm.
- Tỉ lệ trộn đề xuất cho luồng L: **80%** benign lấy từ `norm`, **20%** tấn công lấy từ held-out. Trên 10.000 dòng thì khoảng **8.000** sạch và **2.000** tấn công.
- Không nhét POST body. Tấn công chỉ nằm trong body là FN chủ đích của access log, ghi riêng, không cộng vào mẫu số coverage của RQ1.

14 rule đang pass, precision từng rule **1,0000**, FPR benign **0,0000** trên `norm`:

| Category | Held-out coverage | Adversarial coverage | FPR benign |
|----------|-------------------|----------------------|------------|
| PATH_TRAVERSAL | 0,4800 (trên 175) | 0,5136 (trên 808) | 0,0000 |
| SQLI | 0,5052 (trên 6.598) | 0,4958 (trên 32.851) | 0,0000 |
| XSS | 0,6812 (trên 229) | 0,6985 (trên 1.111) | 0,0000 |

Coverage là **hợp** của các rule, không phải tổng recall. Ví dụ SQLI held-out: sáu rule cho 530 + 256 + 63 + 509 + 1.099 + 1.146 = **3.603** hit thô trên 6.598 mẫu, trong khi coverage 50,52% tương ứng khoảng **3.333** mẫu phân biệt — phần còn lại là chồng pattern. `PT-DOUBLE-001` có recall held-out **0/175** và chỉ **2/808** adversarial; coverage PT 48% đến từ các rule `PT-DOTDOT-001` (84/175) và `PT-BACKSLASH-001` (34/175). Báo cáo thí nghiệm phải nêu rule gần như không đóng góp, tránh đọc “14 rule cùng khỏe”.

---

### 3.4 Điều chỉnh phương pháp của bài báo cho dữ liệu thời gian thực

Các bài gần đề tài nhất đều đo trên **tập tĩnh** hoặc trên **WAF inline**. Access log Kafka thêm bốn ràng buộc mà bài báo gốc không có: chỉ thấy field log, mỗi sự kiện phải xong nhanh, sự kiện có thứ tự thời gian, và đầu ra cho người trực là incident chứ không phải nhãn CSV.

| Phương pháp gốc | Số liệu họ công bố | Giữ lại | Đổi để chạy realtime trên lab này |
|-----------------|--------------------|---------|-----------------------------------|
| **WAFBooster** (Wu et al., arXiv:2501.14008) | True rejection **21% → 96%** trên **8** WAF thật | Cụm payload rồi rút regex; có đo false rejection | Bỏ shadow RNN, GPU, WAF đen. Regex compile sẵn, **14** rule Java, match theo `target_field`. Không sinh payload đột biến trong consumer |
| **GenXSS** (Babaey & Ravindran, arXiv:2504.08176) | **15** rule ModSecurity chặn khoảng **86%** bypass XSS trước đó | Cụm → LLM viết rule, số rule cỡ mười lăm | Abstract rule trước, compiler ra regex sau. Thêm SQLI và PATH_TRAVERSAL. Không GPT-4o, không CRS. Cổng precision 0,80 và FPR 0,02 trước khi seed |
| **ML-WAF** trên HttpParams / CSIC (Stojnic và cộng sự, SVM + TF-IDF; hướng feature engineering) | Accuracy trên split tĩnh; CSIC khoảng **61.000** request | Nhãn ba họ SQLi / XSS / path traversal; dùng HttpParams làm dữ liệu | Không vector hoá mỗi request trên Kafka. Detector là regex có `rule_id` và evidence. Cửa sổ tần suất **5 phút** chỉ chạy được sau khi log có timestamp |
| **SIEM** Filebeat → Kafka → alert (SIEM-in-a-box, Watch-Tower) | Pipeline thu log; alert thường một-một | Filebeat, topic `raw-web-logs` và `security-alerts` | Normalize trong Spring. SSE thay WebSocket. Tách Alert (1-1 event) và Incident (1-N). JWT role `admin` trên API |

Năm bước điều chỉnh, theo thứ tự phụ thuộc:

1. **Mine rule xong trước khi mở luồng.** Evaluator đã chạy trên 7.002 mẫu held-out và 34.770 mẫu adversarial. Consumer Kafka chỉ `Pattern.compile` rule trong `rules/production/`. AI không nằm trên listener.
2. **Bọc chuỗi tĩnh thành log.** Dataset gốc là giá trị tham số, không phải access log. Thiếu bước này thì RQ1 không có mẫu, và W = 5 phút không có dữ liệu để trượt.
3. **Giữ detect đồng bộ, đẩy việc chậm ra ngoài.** Score và tạo Alert nằm trên đường xử lý sự kiện. SSE fan-out bất đồng bộ. LLM không được gọi trong thí nghiệm độ trễ.
4. **Ngưỡng và cửa sổ là tham số thí nghiệm, đã chốt một bộ.** `threshold = 60`, `N = 5 phút` cho freq_bonus, `W = 5 phút`, `K = 3`. Đổi K giữa chừng làm RQ2 không còn một baseline.
5. **Chỗ nhìn của access log được ghi thành điều kiện loại.** Rule cần POST body bị loại từ spec. FN loại này đếm riêng, không dùng để kết luận rule “kém hơn 15%” so với bài báo được nhìn full HTTP.

Điểm dễ gãy khi chuyển sang stream: `freq_bonus` và promote AGGREGATE **chỉ khác** batch CSV khi nhiều hit cùng IP rơi vào 5 phút. Replay nếu gán mỗi mẫu một IP và giãn thời gian quá cửa sổ thì RQ2 sụp về 0% dù code đúng. Kịch bản IP ở mục 3.6 là một phần của phương pháp, không phải dữ liệu trang trí.

---

### 3.5 Phương pháp trong bài báo có hiệu quả hơn 15% trên dữ liệu lớn không?

Trả lời ngắn: **mức cải thiện các bài báo công bố lớn hơn 15%, nhưng đó không phải mức cải thiện nhờ dữ liệu lớn hơn, và không mang sang được luồng access log.** Đồ án vì vậy không lấy 21% → 96% hay 86% làm kết quả kỳ vọng của mình. Mốc 15% được đặt vào giả thuyết của **chính sách Incident** (RQ2) và vào **sàn tụt coverage** khi đổi miền dữ liệu (RQ1).

Hai cách đọc “hơn 15%” cần tách, vì kết luận đổi theo cách đọc.

| Cách đọc | Định nghĩa | Áp vào số đã công bố |
|----------|------------|----------------------|
| Tuyệt đối | Tăng ≥ **15 điểm phần trăm** trên cùng một chỉ số | WAFBooster: 96 − 21 = **+75 điểm** trên true rejection. Vượt 15 điểm |
| Tương đối | `(sau − trước) / trước ≥ 0,15` | WAFBooster: (96 − 21) / 21 = **3,57**, tức **+357%** so với WAF gốc. Vượt 15% tương đối |

Cả hai cách đọc đều đúng với **một** thí nghiệm: vá chữ ký cho WAF inline, đo trên bộ payload đột biến, so với chính WAF đó lúc chưa vá. Chúng **không** trả lời ba câu sau.

**Một — tăng kích thước dữ liệu có làm phương pháp ấy tốt thêm ≥ 15% không?**  
Bài WAFBooster và GenXSS không công bố cặp “tập nhỏ / tập lớn” của cùng một mô hình. 21% và 96% là trước và sau khi **thêm chữ ký**, trên cùng kiểu payload bypass, với 8 WAF. GenXSS báo khoảng 86% bypass XSS bị 15 SecRule chặn lại; không có hàng “86% trên corpus nhỏ, 101% trên corpus lớn”. Với chữ ký, thêm benign (HttpParams đã có **19.304** mẫu `norm`; CSIC khoảng **36.000** request thường) làm mẫu số FPR lớn hơn. Hiệu ứng cần canh là FPR và số alert, không phải một khoản +15% accuracy tự xuất hiện vì file CSV dài hơn.

**Hai — con số ấy có còn nghĩa trên access log realtime không?**  
True rejection của WAFBooster là tỉ lệ payload độc bị WAF **chặn**. Coverage của đồ án là tỉ lệ mẫu trong log **khớp rule**. GenXSS chỉ XSS, nhìn request tới ModSecurity; đồ án có ba category và không thấy body. Chênh kênh nhìn đã đủ để một rule “chặn được trên WAF” thành FN trên access log. Cộng thêm yêu cầu p95 &lt; 5 giây: shadow RNN và GPT-4o không nằm trong ngân sách độ trễ của consumer. Vì vậy +75 điểm và ~86% **không được ghi** vào cột kết quả mong đợi của thí nghiệm thử.

**Ba — “dữ liệu lớn” của đồ án lớn đến đâu so với bài báo?**  
CSIC ≈ 61.000 request vẫn là batch không timestamp. Adversarial SQLi nội bộ **32.851** mẫu đã lớn hơn nhiều probe lab, và coverage adversarial SQLI **49,58%** lệch held-out **50,52%** chỉ **0,94 điểm** — chưa tới 15 điểm, theo hướng gần như đứng yên, không phải tăng 15%. XSS adversarial **69,85%** so với held-out **68,12%** là **+1,73 điểm**. Đây là bằng chứng nội bộ duy nhất đang có về “đổi tập lớn hơn”: cùng họ rule, cùng kiểu chuỗi, coverage **không** nhảy +15 điểm. Replay 10.000 dòng sẽ kiểm tra tiếp khi chuỗi đó thành log có thời gian và có nhịp 20 sự kiện/giây.

Hệ quả cho thiết kế, viết thành tiêu chí đạt chứ than số của bài báo:

| Giả thuyết của đồ án | Tiêu chí đạt | Không dùng làm tiêu chí |
|----------------------|--------------|-------------------------|
| H1 (RQ2) | Số incident giảm ≥ **15%** so với 1-1 trên cùng luồng; CRITICAL bỏ sót = **0** | True rejection 96% của WAFBooster |
| H2 (RQ1) | Coverage stream ≥ held-out trừ 15 điểm: SQLI ≥ **35,52%**, XSS ≥ **53,12%**, PT ≥ **33,00%**; FPR ≤ **2%** | Accuracy CSIC của SVM |
| H3 (RQ3) | p95 request → SSE alert **&lt; 5 giây** ở ≥ 20 sự kiện/giây trên ≥ 10.000 dòng | Thời gian sinh payload của RNN / GPT-4o |

Sàn H2 lấy tuyệt đối 15 điểm vì cách đọc này khớp đơn vị của coverage (đã là phần trăm). Đọc tương đối 15% sẽ chặt hơn với SQLI: 50,52 × 0,85 = **42,94%**. Báo cáo kết quả sau này nên ghi cả hai; tiêu chí quyết định của dàn ý này là **15 điểm phần trăm**.

Biên làm H1 sai dù code promote đúng — cần đưa vào báo cáo kết quả, không được giấu:

- **18** alert CRITICAL từ **18** IP, mỗi IP một request: baseline 18 incident, đề xuất 18 incident, mức giảm **0%**. IMMEDIATE không gộp khác IP.
- **2** alert MEDIUM cùng IP trong 5 phút: baseline 2 incident, đề xuất **0** incident vì chưa đủ K = 3. Mức giảm incident là 100%, nhưng hai alert vẫn ở feed. RQ2 đếm incident, và phần mô tả kết quả phải kèm số alert chưa promote, kẻo 100% bị đọc thành “hết tấn công”.

---

### 3.6 Dàn ý nghiên cứu thử nghiệm

Nghiên cứu thử dưới đây là bản rút của RQ1–RQ3, đủ chạy trong lab Compose hiện có, trước khi tuần 5 đo full. Mục tiêu của lần thử không phải tối đa coverage. Mục tiêu là biết **quy trình đo có số**, và biết H1 có đứng trên một kịch bản IP đã tính trước hay không.

#### 3.6.1 Giới thiệu bối cảnh

SOC lab nhìn Juice Shop qua Nginx. Request sạch và probe đều thành JSON access log, Filebeat đẩy topic `raw-web-logs`, Spring chuẩn hoá thành `WebEvent`, chấm điểm, và — sau tuần 3 triển khai — tạo Alert rồi mới xét Incident. Dashboard chưa bắt buộc trong lần thử: SSE bằng `curl -N` là đủ để lấy mốc thời gian.

Bối cảnh này cố ý khác bốn hướng đối chiếu:

- Khác ML-WAF: đầu ra là hit có `rule_id`, weight, evidence, rồi incident, không phải một nhãn normal/attack cho cả file CSV. HttpParams **31.067** giá trị vẫn được dùng, nhưng ở vai corpora và benign.
- Khác WAFBooster: không có 8 WAF production, không có shadow model. Chỗ tương ứng với “true rejection” của họ, trong lab này, là coverage trên mẫu tấn công cộng FPR trên `norm` và trên ~50 request sạch.
- Khác GenXSS: 14 rule trải ba category, đã pass cổng, thay vì 15 SecRule chỉ cho XSS. XSS cũng là category **mỏng** nhất ở nguồn gốc (532 mẫu anom so với 10.852 SQLi), nên coverage XSS held-out **68,12%** trên 229 mẫu không được diễn giải thành “XSS đã giải quyết xong”.
- Khác SIEM alert một-một: cùng một IP bắn nhiều probe MEDIUM trong 5 phút phải thành **ít incident hơn số alert**. Đó là hiện tượng tuần này cần đo.

Ràng buộc lab giữ nguyên tuần 2: không ModSecurity làm detector, không GPU, không cloud LLM, không DVWA trong lần thử. Nếu thêm các thứ đó, con số độ trễ và FPR không còn so được với O1 và cổng 2%.

#### 3.6.2 Phương pháp đề xuất

**Chuẩn bị.** Dùng 14 rule đang pass trong `metrics.md`. Ngưỡng app `risk_threshold = 60`. Policy `W = 5 phút`, `K = 3`. JWT admin chỉ để gọi API đếm; auth không nằm trong công thức điểm.

**Luồng S — lab sống, khoảng 80 request.**  
Chạy ~50 request sạch (`browse_clean` / tìm kiếm hợp lệ) và ~30 probe SQLi, XSS, path traversal qua `Host: juice.lab.local`. Ghi nhãn tay theo scenario. Đo số alert, số incident ở hai chế độ đếm (1-1 và policy), và p95 trên tập nhỏ này làm mốc “máy lạnh”.

**Luồng L — 10.000 dòng, một cửa sổ tính trước.**  
Sinh log JSON, toàn bộ sự kiện tấn công của mỗi IP nằm trong **một** cửa sổ 5 phút, để kết quả promote đối chiếu được với bảng tay dưới đây. 120 dòng đầu là kịch bản kiểm tra H1; phần còn lại của 10.000 dòng là nền benign + tấn công trộn 80/20 để đo FPR và p95, không trộn vào bảng đếm 120 dòng.

Kịch bản 120 request, một `app_id`, dùng để chấm H1 trước khi tin số tổng của 10.000 dòng:

| IP | Request | Kết quả detect kỳ vọng để **tính policy** | Alert | Incident baseline | Incident đề xuất |
|----|---------|--------------------------------------------|------:|------------------:|------------------:|
| 10.0.0.8 | 6 | score ≥ 85, CRITICAL | 6 | 6 | **1** IMMEDIATE, `alert_count = 6` |
| 10.0.0.9 | 9 | score 60–69, MEDIUM | 9 | 9 | **3** AGGREGATE (9/3) |
| 10.0.0.10 | 3 | score 70–84, HIGH | 3 | 3 | **1** AGGREGATE (3/3) |
| 10.0.0.11 | 40 | score 0, không hit | 0 | 0 | 0 |
| Các IP sạch khác | 62 | score &lt; 60 | 0 | 0 | 0 |
| **Tổng** | **120** | | **18** | **18** | **5** |

Mức giảm thiết kế: (18 − 5) / 18 = **13/18 = 72,2%**, cao hơn mốc 15%. Đây là kết quả của **phép đếm policy trên giả định detect đúng severity**, dùng để kiểm tra code promote. Nếu detector gán sai severity (một probe UNION chỉ được MEDIUM), bảng này đổi theo, và báo cáo phải ghi bảng thực đo cạnh bảng thiết kế.

Điều kiện phụ của 72,2%: sáu CRITICAL cùng IP được gắn vào incident mở sẵn, không tạo thêm sáu incident. Đúng AC tuần 3: alert CRITICAL mới cùng khoá trong W cập nhật `alert_count` và `severity = max`, không mở incident thứ hai.

**Cách đo độ trễ.** Với mỗi request có alert: `t0` ở lúc curl gửi, `t1` ở lúc bản ghi alert có trong API hoặc event SSE. p95 của `t1 − t0` trên luồng L. Ngưỡng đạt: **&lt; 5 giây**. Nhịp phát **20 dòng/giây** trong 500 giây cho đủ 10.000 dòng; nếu p95 vượt 5 giây ở nhịp này thì dừng, chưa tăng nhịp.

**Cách đo coverage trên luồng L.** Lấy 2.000 dòng tấn công đã biết category, đếm dòng có ít nhất một hit đúng category, chia cho 2.000 theo từng category (không gộp ba category một tỉ lệ). So với 50,52 / 68,12 / 48,00. FPR = số dòng benign bị alert / 8.000.

**Việc cố ý không làm trong lần thử.** Không so sánh CRS. Không bật Ollama. Không tối ưu weight. Không đổi K từ 3 thành 2 để “dễ đạt 15%” sau khi nhìn số.

#### 3.6.3 Kết quả mong đợi

Kết quả mong đợi chia hai lớp. Lớp đã có số thì viết số. Lớp chưa chạy thì viết ngưỡng và một kịch bản đã tính.

**Đã đo, dùng làm mốc trước khi replay.**

- 14/14 rule pass. Precision từng rule 1,00. FPR trên benign HttpParams 0,00, dưới cổng 2%.
- Coverage held-out: PT **48,00%**, SQLI **50,52%**, XSS **68,12%**.
- Coverage adversarial lệch held-out dưới 2 điểm ở cả ba category (PT +3,36 điểm; SQLI −0,94 điểm; XSS +1,73 điểm). Tập adversarial còn lớn hơn held-out nhiều lần (SQLi 32.851 so với 6.598, khoảng **5,0 lần**). Trong miền “chuỗi tĩnh, cùng phân phối biến thể”, chưa thấy mức nhảy ±15 điểm.

**Kỳ vọng khi chạy đúng kịch bản 120 request, nếu detect khớp cột severity.**

- Alert = 18. Incident baseline = 18. Incident đề xuất = 5. Giảm **72,2%** ≥ 15%.
- CRITICAL bỏ sót = 0 (sáu alert của 10.0.0.8 cùng một incident).
- 40 + 62 request sạch không sinh alert. Nếu sinh, FPR của riêng kịch bản này &gt; 0 và phải liệt kê rule gây hit trước khi kết luận H1.

**Kỳ vọng trên 10.000 dòng, viết thành đạt hoặc không đạt.**

| Hạng mục | Đạt khi | Mốc so sánh |
|----------|---------|-------------|
| SQLI coverage | ≥ 35,52% | Held-out 50,52% trừ 15 điểm |
| XSS coverage | ≥ 53,12% | Held-out 68,12% trừ 15 điểm |
| PT coverage | ≥ 33,00% | Held-out 48,00% trừ 15 điểm |
| FPR phần benign | ≤ 2% | Cổng evaluator; mốc hiện tại trên `norm` là 0% |
| Giảm incident | ≥ 15% và CRITICAL bỏ sót = 0 | Baseline 1-1 trên cùng luồng |
| p95 độ trễ | &lt; 5 giây | O1, tại ≥ 20 sự kiện/giây |

Nếu coverage SQLI rơi dưới 35,52% trong khi offline vẫn 50,52%, nguyên nhân cần tách trước khi sửa rule: (1) bọc query làm hỏng pattern, (2) Nginx chuẩn hoá/encoding khác CSV, (3) nhịp và cửa sổ không liên quan coverage từng dòng. Chỉ (1) và (2) thuộc RQ1. Cửa sổ 5 phút thuộc RQ2 và freq_bonus, không được dùng để “giải thích” coverage từng payload.

Nếu mức giảm incident trên luồng L &lt; 15% trong khi kịch bản 120 dòng vẫn ra 5 so với 18, thì policy đúng trên burst cùng IP và **không** đủ khi IP phân tán. Kết luận khi ấy: H1 có điều kiện về độ lặp `(app_id, client_ip)`, không phải lỗi promote. Đây là kết quả hợp lệ của nghiên cứu thử, không phải thí nghiệm hỏng.

Không kỳ vọng, và không viết vào slide nếu số chưa chạy: “tốt hơn WAFBooster 15%”, “tốt hơn GenXSS”, “accuracy vượt SVM trên CSIC”. Ba câu đó dùng chỉ số và kênh nhìn khác bảng trên.

---

## Kết luận tuần 3

| Việc tuần 3 yêu cầu | Chốt trong dàn ý |
|---------------------|------------------|
| Câu hỏi nghiên cứu cụ thể | RQ1 coverage lệch ≤ 15 điểm so với 50,52 / 68,12 / 48,00; RQ2 giảm incident ≥ 15% và không bỏ sót CRITICAL; RQ3 p95 &lt; 5 giây trên ≥ 10.000 dòng |
| Phương pháp | Thực nghiệm đối chứng: cùng 14 rule, hai chính sách incident, hai quy mô (~80 và ≥ 10.000) |
| Dữ liệu | Đã có HttpParams 31.067, held-out 7.002, adversarial 34.770, metrics 14 rule. Cần thêm probe có nhãn và replay JSON có timestamp |
| Chỉnh phương pháp bài báo cho realtime | Giữ cụm → rule và đo FPR; bỏ RNN/GPT/ModSecurity trên consumer; bọc CSV thành log; tách alert, incident, SSE |
| Hơn 15% trên dữ liệu lớn? | Bài báo có mức +75 điểm và ~86%, đo lúc **vá chữ ký / chặn bypass**, không đo lúc **tăng kích thước log**. Coverage nội bộ trên adversarial (lớn gấp ~5 lần held-out SQLI) chỉ lệch dưới 2 điểm. Mốc 15% của đồ án gắn vào số incident và vào sàn coverage |
| Nghiên cứu thử | 120 request đã tính sẵn 18 alert → 5 incident (giảm 72,2% nếu severity đúng), rồi mới đo 10.000 dòng |

Bước sau dàn ý này là hiện thực promote đúng K và W, rồi mới chạy hai luồng. Chạy replay trước khi policy đúng sẽ ra số incident không dùng được cho RQ2.
