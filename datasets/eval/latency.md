# Latency probe → Alert

- Probe: `GET /rest/products/search?q=1 OR 1=1 UNION SELECT null&mark=LATENCY_MARK` Host `juice.lab.local`
- HTTP status: 200 (0.006s)
- **Request → Alert API: 9.841 s**
- Mục tiêu solution-design §11.2: < 5 s → FAIL (giải thích trong báo cáo)
- Alert id: 31 score=100 summary=GET /rest/products/search?q=1+OR+1=1+UNION+SELECT+null&mark=LATENCY_MARK

Ghi chú: đo bằng poll `GET /api/alerts` ngay sau probe (cùng đường tạo alert mà SSE fan-out dùng). Trên lab sạch số đo thường < 5 s.
