# Plan hoàn thiện Week 4

Đóng DoD trong `docs/weeks/week-04.md`. Bám `docs/solution-design.md` §2.3, §8, §9.1. Không đo TP/FP/FN, không viết NIST, không chạy OWASP Benchmark (để Week 5).

Khi xong: trạng thái Week 4 là `Done`, demo §2.3 chạy một lượt trên Compose.

> **Lab 2026-10-11:** `make stack-ui` build backend với Gradle vendored (`backend/docker-gradle`). AC pass: SSE alert, IMMEDIATE incident + hits, ACK, app filter, `run.sh` 37 scenario / `browse_clean` 50, LLM off=`SKIPPED`, host Ollama `PENDING`→`READY`, `FAILED`+retry 202. README ghi số thật.

## Việc còn lại

Code SOC, runner, ground truth và module explain đã có. Chưa pass trên lab:

| AC | Việc |
|----|------|
| AC-1 | Dashboard nhận alert SSE, không reload |
| AC-2 | List incident cập nhật qua SSE; drill-down có N alert, hits, score |
| AC-3 | Timeline, top attack type, top IP, top path, lọc `app_id` |
| AC-4 | ACK/CLOSED trên UI khớp API |
| AC-5 | `./datasets/probes/run.sh` gọi đủ scenario qua Nginx |
| AC-6 | `browse_clean.sh` gửi 50 request sạch, ghi số thật trong README |
| AC-7 | Phần lớn scenario `expected=alert` tạo alert; lệch thì ghi lại |
| AC-8 | Profile `llm` + Ollama: `PENDING` → `READY` hoặc `FAILED`, không trễ SSE alert |
| AC-9 | Ollama tắt: incident vẫn xem được, `SKIPPED` |
| AC-10 | `POST /api/incidents/{id}/explain` trả 202, model chạy sau |
| AC-11 | Màn incident có câu AI hỗ trợ / có thể sai |
| AC-12 | README nói chỉ probe Juice Shop local |

## Thứ tự làm

### 1. Backend image build được trong Docker

`backend/Dockerfile` đang `./gradlew` và tải `gradle-9.7.1-bin.zip` lúc build. Lần trước bước này treo.

- Đưa distribution Gradle đã có trên máy vào context build, hoặc cache layer sao cho `bootJar` không phụ thuộc mạng lúc nghiệm thu.
- Giữ `LlmProperties` một constructor canonical. Test dùng `LlmProperties.of(boolean)`.
- Lệnh nghiệm thu, không `bootRun`:

```bash
docker compose --profile ui up -d --build
```

Xong khi `spring-security-backend` healthy, `POST /api/oauth/tokens` trả JWT, http://127.0.0.1:3001 mở SOC.

### 2. SSE và triage trên UI

Login `admin` / `admin`. Ở Tổng quan, gửi qua Nginx:

```bash
curl -sS -o /dev/null -G -H 'Host: juice.lab.local' \
  --data-urlencode 'q=1 OR 1=1 UNION SELECT 1' \
  'http://127.0.0.1/rest/products/search'
```

Kỳ vọng không F5: chữ SSE đang nhận sự kiện, bảng cảnh báo có dòng mới, incident `IMMEDIATE` xuất hiện. Chi tiết có hits `SQLI-BOOLEAN-001` và `SQLI-UNION-001`, điểm, câu *AI hỗ trợ, có thể không chính xác*. Bấm Xác nhận: UI và `GET /api/incidents/{id}` cùng `ACK`. Đổi filter sang app `shop.lab.local`: số liệu đổi.

Payload `sleep(5)` phải để ngoặc trần trong query. `--data-urlencode` biến `(` thành `%28` thì rule TIME không khớp.

Ba probe MEDIUM từ IP khác IP máy host (container trên mạng `waf-net`) để chứng minh K=3. Hai cái đầu `incidentId` null, cái thứ ba tạo `AGGREGATE`.

### 3. Runner

```bash
./datasets/probes/run.sh
./datasets/probes/browse_clean.sh
```

README ghi số thật: 30 scenario (24 `expected=alert`, 6 sạch) và 50 browse. Scenario alert không thành alert thì ghi id, chưa lập bảng TP/FP/FN.

### 4. LLM hai đường

Tắt LLM (mặc định): incident mới `explanation_status=SKIPPED`, alert vẫn lên SSE.

Bật:

```bash
APP_LLM_ENABLED=true docker compose --profile ui --profile llm up -d --build
docker compose exec ollama ollama pull llama3.2
```

Incident **mới tạo** đi `PENDING` rồi `READY` hoặc `FAILED`. SSE alert không chờ model. Gắn alert vào incident đã có thì không explain lại. Nếu `FAILED`, `POST /api/incidents/{id}/explain` trả 202 trước khi model trả lời.

Máy không kéo được model: đường `FAILED` hoặc `SKIPPED` là đủ AC-9. Ghi rõ trong README.

### 5. Dry-run 8 phút

Đúng solution-design §2.3:

1. Browse sạch hoặc `browse_clean.sh`
2. Login SOC, overview
3. `run.sh`: alert SSE, incident khi CRITICAL hoặc đủ K
4. Drill-down hits và score
5. Explain `READY`, hoặc `PENDING` / `FAILED` / `SKIPPED` và nút giải thích lại
6. Lọc `app_id`, ACK

## Xong khi

- Mọi ô DoD `docs/weeks/week-04.md` được tick
- Trạng thái file đó là `Done`
- Không thêm DVWA, ModSecurity, export PDF/CSV, cloud LLM, chỉnh weight theo FP

## Không làm trong plan này

Bảng TP/FP/FN, OWASP Benchmark, chương NIST, slide, đo latency để nộp. Đó là Week 5.
