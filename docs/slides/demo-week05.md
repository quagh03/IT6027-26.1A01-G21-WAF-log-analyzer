# Slide demo 8–10 phút — WAF Log Analyzer (G21)

> Trình bày theo flow solution-design §2.3. Benchmark để cuối, không chiếm demo live.

---

## 1. Mở đầu (≈45s)

- **WAF Log Analyzer** — giám sát tấn công web trên **access log**, không chặn inline
- Stack: Juice Shop → Nginx JSON → Filebeat → Kafka → Spring → SSE → React SOC
- LLM: explain async / **SKIPPED** khi tắt Ollama

---

## 2. Traffic sạch (≈60s)

- Mở `http://127.0.0.1:3001` — login `admin` / `admin`
- Browse Juice qua Nginx (`Host: juice.lab.local`) hoặc `./datasets/probes/browse_clean.sh`
- Kỳ vọng: **không** alert trên browse (tầng B: FP=0 / TN=50)

---

## 3. Runner probe (≈90s)

```bash
./datasets/probes/run.sh
```

- 3 category: SQLI / XSS / PATH_TRAVERSAL (+ vài clean)
- Payload nằm trên query/path (đúng tầm access log)

---

## 4. Alert SSE + Incident (≈120s)

- Dashboard: alert realtime (SSE)
- Incident: CRITICAL → IMMEDIATE; MEDIUM/HIGH → AGGREGATE (K=3, W=5p)
- Nhấn: **1 incident : N alerts**, không 1-1

---

## 5. Drill-down hits / score (≈90s)

- Mở 1 incident → danh sách alert → event → detection hits + evidence + weight
- Nhắc công thức: base + freq_bonus + status_bonus, ngưỡng 60

---

## 6. Explain async (≈60s)

- Đường chính demo: `explanation_status = SKIPPED` (LLM off)
- Plus (nếu có Ollama): `PENDING` → `READY` / `FAILED`
- UI: *AI hỗ trợ, có thể không chính xác*

---

## 7. Stats theo `app_id` (≈45s)

- Overview: juice vs shop scope
- Số alert / incident phiên demo

---

## 8. Đánh giá ngắn — cuối slide (≈90s, nếu còn giờ)

| Tầng | Kết quả khóa |
|------|----------------|
| A corpora | 14 rule pass; FPR benign 0 |
| B Juice | TP=24 FP=0 FN=7 TN=56; recall probe 0,77 |
| Benchmark* | score = 0 (TPR=1, FPR=1) — lệch nhãn vuln vs attempt |
| Latency | ≈11–15 s (trượt <5 s; Filebeat/Kafka) |

\*External eval, lab đã `make reset`; không retune rule.

---

## 9. Đóng (≈30s)

- NIST CSF: Detect (DE) + Respond (RS) map module
- Ethics: chỉ lab local
- Q&A

---

### Checklist dry-run

- [ ] `make stack-ui` healthy
- [ ] Login SOC
- [ ] Browse sạch
- [ ] `run.sh` → alert/incident
- [ ] Drill-down hits
- [ ] Explain SKIPPED
- [ ] Stats `app_id`
