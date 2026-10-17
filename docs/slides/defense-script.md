# Script thuyết trình — ghi vào Notes từng slide PPT

Nguồn slide: `docs/slides/defense-presentation.html` (27 slide).  
Thời lượng mục tiêu: ~10–13 phút. Có thể rút các slide đánh dấu *lướt*.

Cách dùng: copy nguyên khối « … » vào **Notes** của slide tương ứng.

---

## Slide 1 — Bìa HUST (~10–15 s)

« Kính chào hội đồng. Em là Lê Quang Huy, nhóm G21, môn IT6027. Em xin trình bày đề tài WAF Log Analyzer. »

---

## Slide 2 — Tiêu đề đề tài (~30–40 s)

« Đề tài là WAF Log Analyzer: phân tích access log Nginx để phát hiện và hỗ trợ ứng phó tấn công web, tập trung SQLi, XSS và path traversal. Thời gian trình bày khoảng mười đến mười ba phút. »

---

## Slide 3 — Cấu trúc bài (~35–40 s)

« Em trình bày sáu phần. Một: bối cảnh và kiến trúc. Hai: phạm vi tấn công. Ba — trọng tâm — nguồn rule, điểm rủi ro và chính sách sự cố. Bốn: kết quả Juice Shop và OWASP Benchmark. Năm: minh họa lab nếu còn thời gian. Sáu: hạn chế và hướng phát triển.

Định vị ngắn: hệ thống phát hiện nỗ lực tấn công trên access log, không chặn inline như WAF CRS, cũng không quét mã nguồn để kết luận lỗ hổng. Hai số cần nhớ: ngưỡng Alert bằng 60; điểm Benchmark adaptation bằng 0 vì TPR bằng FPR bằng 1. »

---

## Slide 4 — Đặt vấn đề (~40–45 s)

« Bài toán: SQLi, XSS và path traversal để lại dấu vết trên access log, nhưng thiếu quy trình tái lập từ phát hiện tới cảnh báo, nhóm sự cố và điều tra. Hiện trạng thường thiếu chuỗi bằng chứng theo thời gian, cảnh báo rời rạc, khó gắn Detect và Respond theo NIST CSF.

Mục tiêu đề tài: mười bốn rule đã đánh giá offline, điểm rủi ro, Alert và Incident có chính sách rõ, bảng điều khiển SOC qua REST và SSE, và đo lường trên lab có nhãn. »

---

## Slide 5 — Kiến trúc Docker (~50–55 s)

« Kiến trúc lab trên mạng Docker waf-net. Client hoặc probe gửi HTTP tới Nginx; Nginx vừa proxy Juice Shop vừa ghi JSON access log. Filebeat đẩy log vào Kafka topic raw-web-logs. Spring Boot ingest, detect, score và correlate; lưu PostgreSQL, cache Redis. React SOC nhận REST và SSE.

Điểm quan trọng: phát hiện ngoài băng — không chặn HTTP tại Nginx. »

---

## Slide 6 — Thành phần kỹ thuật (~25–35 s) — *lướt nếu thiếu giờ*

« Phiên bản lab: Nginx 1.27, Juice Shop v20, Filebeat 8.17, Kafka 4.1, Java 25 với Spring Boot 4.1, Postgres 16, Redis 7.4, React 19. Cổng: 80 Nginx, 3001 SOC, 8080 API, JWT admin/admin. Khởi động bằng make stack-ui. »

---

## Slide 7 — Phạm vi tấn công (~40–45 s)

« Ba lớp trong phạm vi. SQLi trên query: boolean, UNION, TIME, stacked. XSS: thẻ script, onerror, javascript URI. Path traversal: chuỗi chấm chấm slash và biến thể mã hóa trên path hoặc query.

Hệ thống ghi nhận nỗ lực tấn công trên nhật ký. Việc vá lỗ hổng vẫn thuộc tầng ứng dụng. »

---

## Slide 8 — Luồng xử lý chính (~35–40 s)

« Luồng chính: HTTP tấn công tới Nginx, dòng JSON qua Filebeat vào Kafka, Spring tạo WebEvent và detection hit, rồi Alert hoặc Incident, SSE đẩy lên SOC.

Ví dụ nhanh: BOOLEAN cộng UNION cho điểm 95, mức CRITICAL, mở Incident ngay theo nhánh IMMEDIATE. »

---

## Slide 9 — Sơ đồ hoạt động (~45–50 s)

« Sau khi parse log thành công, Spring khớp mười bốn rule, RiskScorer tính điểm. Dưới 60 thì không tạo Alert. Đủ ngưỡng thì tạo Alert. CRITICAL mở Incident ngay. MEDIUM hoặc HIGH cần đủ K bằng 3 cảnh báo trong cửa sổ W bằng 5 phút, cùng cặp ứng dụng và địa chỉ IP, rồi mới mở Incident. Kết quả đẩy SSE lên SOC. »

---

## Slide 10 — Sinh rule · tổng quan (~40–45 s) — *ưu tiên*

« Bộ mười bốn chữ ký đến từ lab offline detection-rule-generator, không nằm trên Kafka. Mô hình ngôn ngữ không viết regex sản xuất một phát.

Luồng: corpora, cluster card, abstract JSON, compile, evaluator, rồi seed Flyway với nguồn AI_MINED. Nguồn dữ liệu gồm HttpParams, FuzzDB đúng category, và SQLi khoảng ba mươi nghìn mẫu — chỉ giá trị nhìn thấy trên path, query, user-agent hoặc raw. Runtime Spring chỉ khớp regex đã nạp; mô hình không tham gia phát hiện lúc chạy. »

---

## Slide 11 — Sinh rule · từng bước (~50–55 s) — *ưu tiên*

« Python làm ingest, normalize, dedupe, split khoảng bảy mươi phần trăm generation và ba mươi phần trăm held-out — mô hình không thấy held-out — rồi cluster. Ví dụ thẻ SQLI-BOOLEAN_TAUTOLOGY khoảng sáu nghìn mẫu; chỉ đưa vài ví dụ dương và âm vào prompt.

Người gắn rule-generator.md với đúng một thẻ. Mô hình xuất abstract JSON: type BOOLEAN_TAUTOLOGY, điều kiện, weight gợi ý 40 — chưa có regex. Lưu vào ai/outputs. »

---

## Slide 12 — Biên dịch và cổng kiểm (~50–55 s) — *ưu tiên*

« Pipeline compile map type sang regex Java và kiểm cú pháp, ghi candidates với source AI_MINED. Nếu FPR cao thì reviewer hoặc optimizer sửa abstract rồi compile lại — không chỉnh weight runtime tùy tiện.

Evaluate trên held-out, benign và adversarial. Đạt cổng khi precision tối thiểu 0,80, FPR tối đa 0,02. Mười bốn trên mười bốn đạt, FPR lành bằng 0, rồi mới vào production và Flyway. Coverage held-out khoảng 50%, 68% và 48% theo ba nhóm. »

---

## Slide 13 — Ba tầng đánh giá rule (~45–50 s) — *ưu tiên*

« Benchmark không phải cổng đánh giá bộ rule. Ba tầng, ba câu hỏi.

Tầng A offline: regex có khớp payload trên corpus không — đây mới là bằng chứng chất lượng chữ ký trước khi seed. Tầng B Juice: request có nhãn có mở Alert từ ngưỡng 60 không — recall 0,774, FP bằng 0 — đánh giá cả pipeline, không thay cổng A. OWASP Benchmark adaptation hỏi mã có lỗ hổng không; hệ thống chỉ bắt mẫu tấn công trên request. Điểm 0 phản ánh lệch câu hỏi, không kết luận rule hỏng. »

---

## Slide 14 — Trọng số rule (~35–45 s)

« Mỗi lần khớp, hệ thống ghi detection_hit với trọng số cố định từ bảng rule Flyway. Điểm rủi ro là tổng các trọng số, cộng hệ số tần suất và mã trạng thái — không do mô hình gán lúc chạy.

Một rule đơn lẻ dưới 60 thường chưa đủ mở Alert nếu đứng riêng. »

---

## Slide 15 — Công thức điểm rủi ro (~50–55 s) — *ưu tiên*

« Điểm rủi ro S của sự kiện e bằng min của 100 và tổng: tổng trọng số mọi hit trên e, cộng B tần suất, cộng B trạng thái.

B tần suất đếm hit cùng IP trong năm phút, tối đa cộng 20. B trạng thái cộng 5 nếu mã HTTP là 403, 404 hoặc 500 và tổng trọng số lớn hơn 0.

Ví dụ: OR 1=1 cộng UNION SELECT cho tổng trọng số 95, không bonus, S bằng 95 — CRITICAL. Chỉ TIME cho 60 — MEDIUM. BOOLEAN cộng COMMENT cho 75 — HIGH. »

---

## Slide 16 — Mức nghiêm trọng (~30–35 s)

« Điểm ánh xạ: dưới 40 là LOW — không mở Incident; 40 đến 69 MEDIUM; 70 đến 84 HIGH; từ 85 CRITICAL.

Lưu ý: ngưỡng mở Alert mặc định 60 khác với biên mức MEDIUM. Điểm 55 chỉ UNION không tạo Alert; điểm 60 từ TIME thì tạo Alert MEDIUM. »

---

## Slide 17 — Chính sách Alert (~45–50 s)

« AlertService lấy risk_score, đối chiếu ngưỡng ứng dụng mặc định 60. Dưới ngưỡng thì không mở Alert nhưng vẫn lưu sự kiện và hit. Mỗi event tối đa một Alert; sau đó promote Incident và phát SSE.

Ngưỡng 60 giảm nhiễu khi chỉ khớp một rule nhẹ. Ví dụ tiêu đề: CRITICAL 95, địa chỉ IP, đường dẫn tìm kiếm sản phẩm. »

---

## Slide 18 — Chính sách Incident (~55–65 s) — *ưu tiên*

« Incident gắn theo khóa ứng dụng và client IP, cửa sổ W năm phút, ngưỡng gom K bằng 3 với MEDIUM và HIGH.

Thứ tự: Alert đã gắn thì thôi; LOW không mở sự cố; đã có Incident OPEN trong cửa sổ thì gắn thêm; quá cửa sổ thì đóng tự động. CRITICAL mở ngay — IMMEDIATE. MEDIUM hoặc HIGH đủ ba Alert chưa gắn thì mở AGGREGATE và gắn cả nhóm.

Mục tiêu: giảm sự cố trùng — không phải mọi Alert thành một Incident riêng. »

---

## Slide 19 — Ví dụ xuyên suốt (~45–50 s)

« Ví dụ đầu cuối. Payload OR 1=1 UNION SELECT: hai hit 40 cộng 55, điểm 95, tạo Alert CRITICAL. Nhánh IMMEDIATE mở Incident OPEN. Giải thích SKIPPED nếu tắt mô hình. SSE đẩy lên giao diện; request cùng IP gắn tiếp vào Incident số 1.

Trường hợp gom: ba Alert HIGH điểm 75 cùng IP trong năm phút — Alert thứ ba kích hoạt AGGREGATE. »

---

## Slide 20 — Mô hình dữ liệu (~25–35 s) — *lướt*

« Thực thể chính: applications có ngưỡng; web_events có risk_score; detection_hits có evidence và weight; alerts gắn unique theo event; incidents có trạng thái và lý do promote theo cặp app và IP. »

---

## Slide 21 — Minh họa probe (~25–30 s) — *lướt / demo*

« Trên lab: make stack-ui, chạy probe MEDIUM HIGH hoặc run.sh. Chỉ tấn công Juice Shop tự host qua Nginx — ràng buộc đạo đức của nhóm. »

---

## Slide 22 — OWASP Benchmark trên Spring (~80–90 s) — *ưu tiên*

« Nguồn đánh giá ngoài là OWASP Benchmark Java phiên bản 1.2 — file expectedresults và nhãn true false. Đây là adaptation: không deploy WAR; chỉ ba nhóm sqli, xss, pathtraver nhìn thấy trên access log — bảy trăm tám mươi trên hai nghìn bảy trăm bốn mươi case; replay GET payload cố định; điểm chính thức TPR trừ FPR.

Trọng tâm là hành vi Spring Boot. Mỗi case: log vào Kafka, mười bốn rule khớp, RiskScorer khoảng 97 đến 100, AlertService mở Alert CRITICAL vì vượt 60, IMMEDIATE gom bảy trăm tám mươi Alert thành một Incident. REST: 801 WebEvent, 780 Alert, một Incident.

Chấm trên 780 case: TP 467, FP 313, FN 0, TN 0 — TPR bằng FPR bằng 1, điểm bằng 0. Điểm 0 không phải Spring hỏng: Benchmark hỏi mã servlet có lỗ hổng không; Spring hỏi request có mẫu tấn công không. Case false vẫn nhận payload tấn công nên cũng Alert. Đúng việc detector: bảy trăm tám mươi trên bảy trăm tám mươi attempt đều ghi nhận. Không nói đạt hay trượt Benchmark chính thức; không gộp với Juice; không dùng làm cổng bộ rule. »

---

## Slide 23 — Tổng hợp ba tầng (~35–40 s) — *ưu tiên*

« Ba tầng, ba câu hỏi. Offline: mười bốn rule đạt cổng, FPR lành khoảng 0. Juice: recall 0,774, precision 1, FP bằng 0 — ma trận TP 24, FN 7, FP 0, TN 56. Benchmark: điểm 0 với TPR bằng FPR bằng 1.

Độ trễ yêu cầu tới Alert khoảng 11,5 đến 15,1 giây — chưa đạt mục tiêu dưới 5 giây. »

---

## Slide 24 — NIST / ISO (~20–25 s) — *lướt nếu thiếu giờ*

« Trong phạm vi lab: NIST Detect gắn log, rule, điểm, Alert; Respond gắn Incident và SSE. ISO: logging, monitoring và quản lý sự cố. »

---

## Slide 25 — Checklist demo (~20–25 s) — *bỏ nếu không demo*

« Nếu trình diễn: stack-ui, browse sạch, probe CRITICAL, quan sát SSE Alert và Incident — một Incident có thể gắn nhiều Alert. »

---

## Slide 26 — Hạn chế và hướng phát triển (~45–50 s) — *ưu tiên*

« Hạn chế: access log không thấy body, header, cookie; chữ ký không bao phủ zero-day và coverage SQLi còn thấp; độ trễ request tới Alert khoảng 11 đến 15 giây, chưa dưới 5 giây; điểm Benchmark là adaptation vì lệch ontology; hệ thống ngoài băng, không chặn HTTP; LLM không nằm trên đường phát hiện.

Hướng phát triển: instrument timestamp xuyên pipeline để tối ưu p95; telemetry body và header có masking; regression corpus và approval rollback rule; feedback false-positive có người duyệt; cân nhắc WAF DetectionOnly trước khi block. Mọi tự động hóa block hoặc chỉnh rule cần giữ con người trong vòng quyết định. »

---

## Slide 27 — Cảm ơn (~15–20 s)

« Em xin cảm ơn hội đồng và sẵn sàng nhận câu hỏi. »

*Nhắc nhanh nếu cần: Juice recall 0,774; Benchmark adaptation điểm 0.*

---

## Lộ trình rút gọn (≤ 12 phút)

**Giữ:** 2 → 3 → 4 → 5 → 7 → 8 → 9 → 10 → 11 → 12 → 13 → 15 → 17 → 18 → 19 → 22 → 23 → 26 → 27.

**Lướt hoặc bỏ:** 6, 14, 16, 20, 21, 24, 25.

## Câu dự phòng

**Mở 15 giây:** « Đề tài xây pipeline phát hiện nỗ lực tấn công web từ access log Nginx, có chấm điểm, cảnh báo, sự cố và đánh giá trên lab. »

**Kết 15 giây:** « Tóm lại: mười bốn rule qua cổng offline; Juice recall 0,774 với FP bằng 0; Benchmark adaptation điểm 0; hạn chế chính là tầm nhìn log và độ trễ — hướng tiếp theo là tối ưu latency và telemetry có kiểm soát. Em xin cảm ơn. »
