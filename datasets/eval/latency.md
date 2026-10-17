# Latency probe → Alert

- Probe: `GET /rest/products/search?q=1 OR 1=1 UNION SELECT null&mark=LATENCY_MARK` Host `juice.lab.local`
- Đo trên lab sau `TRUNCATE` events/alerts (DB sạch runtime), poll `GET /api/alerts`
- **Request → Alert API: 11.5–15.1 s** (nhiều lần đo cùng lab)
- Mục tiêu solution-design §11.2: < 5 s → **FAIL**

## Giải thích (không im lặng)

- HTTP trả về trong ~0,02 s; phần lớn thời gian nằm ở **Filebeat harvest → Kafka → Spring consumer**, không phải rule engine.
- Filebeat mặc định quét file log theo chu kỳ (thường vài giây); thêm lúc Kafka/consumer idle sau truncate.
- SSE dùng cùng nguồn alert đã persist — số poll API là cận trên cho “thấy alert trên SOC”.
- Không chỉnh production rule / không hạ threshold để “ép” số < 5 s trong Week 5.

Reproduce: `make reset && make stack-ui` rồi `python3 datasets/eval/measure_latency.py`.
