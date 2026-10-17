# OWASP Benchmark v1.2 — external eval (access-log detector)

Nguồn nhãn: `expectedresults-1.2.csv` (OWASP BenchmarkJava).
Replay GET qua Nginx lab (`Host: juice.lab.local`). **Không** dùng payload Benchmark để sinh/retune rule.

## Phạm vi chấm

| Bucket | Số | Ghi chú |
|---|---:|---|
| log_visible (query/path param) | 780 | Vào mẫu số TP/FP/FN/TN |
| body_invisible | 0 | Không replay như thể log phải thấy |
| header/cookie (không trên access log schema) | 329 | Đếm riêng, không kéo TPR |
| unknown_vector | 118 | Không chấm |
| out_of_scope (cmdi/ldapi/xpathi/crypto/…) | 1513 | Không tính FN |

## Score gộp 3 category (log_visible)

| | Có alert | Không alert |
|---|---:|---:|
| Nhãn Benchmark `true` | TP=467 | FN=0 |
| Nhãn Benchmark `false` | FP=313 | TN=0 |

TPR = 1.0000 · FPR = 1.0000 · **score = TPR − FPR = 0.0000**

## Theo category

| Category | TP | FP | FN | TN | TPR | FPR | score |
|---|---:|---:|---:|---:|---:|---:|---:|
| SQLI | 194 | 119 | 0 | 0 | 1.0000 | 1.0000 | 0.0000 |
| XSS | 190 | 131 | 0 | 0 | 1.0000 | 1.0000 | 0.0000 |
| PATH_TRAVERSAL | 83 | 63 | 0 | 0 | 1.0000 | 1.0000 | 0.0000 |

## So sánh ngắn với tầng A (rule-generator)

Tầng A (`detection-rule-generator/evaluator/reports/metrics.md`): 14 rule pass cổng precision≥0,80 / FPR≤0,02; coverage held-out SQLI≈50,5% · XSS≈68,1% · PATH_TRAVERSAL≈48,0%.
Tầng Benchmark này là **external eval** trên runtime alert — **không** trộn một mẫu số với tầng A hay Juice probes.

## Hạn chế

- Nhãn Benchmark = *code có lỗ hổng thật?*; detector log = *request có pattern tấn công?*. Gửi payload tấn công vào case `false` dễ thành FP theo scoring Benchmark dù SOC vẫn muốn thấy attempt.
- Payload cookie/header/body không xuất hiện trên Nginx JSON access log của lab → loại khỏi mẫu số.
- Ngưỡng score 60: một signature nhẹ có thể hit nhưng chưa alert.

Chi tiết từng case: `scored-cases.csv`.
