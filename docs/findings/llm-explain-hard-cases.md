# Finding: giải thích incident trên 7 case khó

Prompt ở mục “Hướng sửa prompt” nằm ở `backend/src/main/resources/prompts/incident-explain.txt`. `buildPrompt` chỉ ghép evidence. Bảng kết quả bên dưới là lần chạy trước khi sửa.

Ngày chạy: 2026-10-10. Model: `qwen3.5:9b` trên Ollama máy host, `think: false`. Mỗi case một incident mới (đóng OPEN trước khi bắn probe) để explanation không bị dính alert cũ.

Prompt lúc chạy nằm ở `IncidentExplainStore.buildPrompt`: “chỉ dựa trên các hit”, “Không bịa payload. Không kết luận nếu không có hit.”, kết thúc bằng `CONFIDENCE:`. Input vẫn gửi nguyên query (cắt 240 ký tự) và `status`.

## Kết quả

| Case | Incident | Điểm | Tin cậy | Evidence | Lệch |
|------|----------|------|---------|----------|------|
| `1+oR+1%3d1+UnIoN+SeLeCt` | 6 | 97 | HIGH | `oR+1%3d1`, `UnIoN+SeLeCt` | Sát nhất. Trích đúng evidence, không giải `%3d`. |
| `O%27Brien%27--+UNION+SELECT` | 7 | 100 | HIGH | `%27--`, `UNION+SELECT` | Giải `%27` thành `'`. Viết “SQLi thành công”. |
| `1;waitfor%20delay%20%270:0:1%27` | 8 | 100 | HIGH | `;waitfor`, `waitfor%20delay` | Đúng time-based. Gọi hai hit của một request là “hai câu hỏi SQL”. Giải `%20` thành khoảng trắng. |
| `%253csvg%20onload%3dalert(1)%253e` + `javascript:` | 9 | 100 | HIGH | `%253csvg`, `%253csvg%20onload%3d`, `javascript:`, `%253csvg` | Gọi `%253csvg` là mã hóa của `<svg` (một lần decode ra `%3csvg`). Thêm `onload=alert(1)`. Viết “truy cập trái phép”. |
| `/ftp?file=%252e%252e%252f…etc/passwd` | 10 | 100 | HIGH | `%252e%252e%252f` (DOTDOT và DOUBLE) | Nhắc `/etc/passwd` vì chuỗi nằm trong query. Gọi path `/ftp` là “cổng FTP”. Viết “cho phép truy cập” file. |
| `/ftp?file=%2e%2e%5c..%5cwindows%5cwin.ini` | 11 | 100 | LOW | `%2e%2e%5c` | Giải đúng `%2e%2e%5c` → `..\`, nhắc `win.ini`. Case duy nhất tự hạ LOW. |
| SQLi + XSS + `../` trên một query | 12 | 100 | HIGH | BOOLEAN, UNION, ENCODED, JSURI, SCRIPT, DOTDOT | Đủ ba lớp. Gọi HTTP 200 là “truy vấn thành công”. |

Không có case nào bịa category không có trong hit.

## Vì sao prompt hiện tại không chặn được

Ba câu cấm không đụng đúng lỗi:

1. “Không kết luận nếu không có hit” chỉ cấm kết luận khi **không** có hit. Cả 7 case đều có hit, nên câu này không cấm “thành công”, “trái phép”, “cho phép đọc file”.
2. “Không bịa payload” không cấm giải mã. Query nằm trong prompt, model coi `'`, `<svg`, `../`, `win.ini` là đọc input chứ không phải bịa.
3. `status=200|500` được đưa vào không kèm nghĩa. Model map 500 → khai thác được, 200 → request thành công.

## Hướng sửa prompt

Giữ vai trò giải thích, không detect. Thay khối chỉ dẫn đầu `buildPrompt` theo hướng này:

```text
Bạn là trợ lý SOC. Viết một đoạn tiếng Việt: incident này được mở vì request khớp rule nào.
Chỉ được khẳng định những gì nằm trong evidence, rule, category, score, status bên dưới.

Cấm:
- Nói tấn công thành công, đã khai thác, truy cập được file, trái phép, hoặc app bị lỗi vì payload.
- status là mã HTTP của ứng dụng, không phải bằng chứng exploit.
- Giải mã %xx, %25xx, + thành ký tự khác. Trích evidence đúng nguyên văn.
- Đổi path URL thành cổng hoặc giao thức (ví dụ /ftp không phải cổng FTP).
- Đếm số hit thành số request. Một alert là một request, nhiều dòng hit là nhiều rule trên request đó.

CONFIDENCE: HIGH chỉ khi mọi câu là trích evidence hoặc field có sẵn.
Nếu phải diễn giải ý định hoặc kết quả tấn công, ghi CONFIDENCE: LOW.
Kết thúc đúng một dòng: CONFIDENCE: LOW hoặc CONFIDENCE: MEDIUM hoặc CONFIDENCE: HIGH.
```

Đổi nhãn input cho khớp chỉ dẫn: dòng request ghi `raw_query=` (không gọi là payload), và một dòng `note: raw_query là chuỗi log, không giải mã`.

## Prompt mới có xử lý hết không

Xử lý được phần lớn lỗi chữ, không xử lý hết lỗi giải mã.

| Lỗi | Prompt mới | Mức kỳ vọng |
|-----|------------|-------------|
| “thành công”, “trái phép”, “cho phép truy cập file” | Cấm đúng cụm và cấm suy từ status | Giảm mạnh. Đây là lỗi làm theo chỉ dẫn, model đã tuân chỉ dẫn cũ ở phần category. |
| Hai hit thành “hai câu hỏi” | Cấm đếm hit thành request | Giảm mạnh. |
| `/ftp` thành “cổng FTP” | Cấm đổi path thành cổng, có ví dụ | Giảm mạnh. |
| Giải `%27`, `%20`, `%253c` | Cấm giải mã, bắt trích nguyên văn | Giảm, không hết. Model đã giải mã dù prompt cũ nói “không bịa”. Giải `%xx` là thói quen của model, không phải chỗ nó thấy mình đang bịa. |
| `%253csvg` thành `<svg`, thêm `alert(1)` | Cấm giải mã + query chỉ là `raw_query` | Giảm một phần. Query vẫn nằm trong prompt nên model vẫn thấy `alert(1)`. Muốn chặn hẳn thì bỏ query khỏi input, chỉ gửi evidence. Khi đó nó không còn nhắc `/etc/passwd` hay `win.ini` — đúng với “chỉ nói evidence”, nhưng đoạn văn cụt hơn. |
| Confidence gần như luôn HIGH | Rubric HIGH / LOW | Giảm, không ổn định. Case Windows đã tự hạ LOW, nên rubric có chỗ bám. Model local vẫn hay chọn HIGH. |

Prompt không đụng hai việc khác, và không nên trông chờ nó sửa:

- Explain chỉ chạy lúc incident mới tạo. Alert gắn sau không được kể. Đó là policy §9.1, không phải lỗi prompt.
- Rule `\b` không khớp keyword sau `%20`. Case mixed-case và O'Brien phải viết `+` mới alert. Sửa regex, không sửa prompt.

Nếu sau một lượt chạy lại mà model vẫn giải `%xx`, bước tiếp theo là kiểm tra sau khi model trả lời (đoạn văn chứa dạng đã giải mà evidence không có thì hạ confidence hoặc ghi FAILED), không phải viết thêm câu cấm.

## Lần chạy sau khi sửa prompt

Cùng 7 case, cùng model, backend đã nạp prompt mới. Incident 13–19.

| Case | Incident | Tin cậy | Còn lệch |
|------|----------|---------|----------|
| Mixed-case SQLi | 13 | HIGH | Không. Trích `oR+1%3d1` và `UnIoN+SeLeCt`. |
| O'Brien | 14 | HIGH | Không giải `%27`. Còn cụm “dẫn đến phản hồi status 500”. |
| waitfor | 15 | MEDIUM | Không giải `%20`. Nói “hai rule”, không nói “hai câu hỏi”. |
| Double-encoded SVG | 16 | HIGH | Không giải `%253c`, không thêm `alert(1)`. Gọi một request là “các request”. |
| Double-encoded `../` | 17 | MEDIUM | Không gọi `/ftp` là cổng, không nói đọc được `/etc/passwd`. |
| Windows `..\` | 18 | HIGH | Không giải `%2e%2e%5c`. Gọi đúng path `/ftp`. Nhắc status 200 như một field. |
| Ba lớp | 19 | LOW | Trích đủ 6 evidence, kể cả `../` và `%3Cscript`. Không nói “thành công”. LOW là thận trọng hơn mức rubric (đoạn văn không suy diễn kết quả). |

Không còn câu “thành công”, “trái phép”, “cho phép truy cập file”, và không còn lần giải `%xx` nào trong 7 đoạn. Bước kiểm tra sau câu trả lời chưa cần. Lệch còn lại là đếm số request (case SVG) và confidence chưa đều (case ba lớp bị hạ LOW dù bám evidence).
