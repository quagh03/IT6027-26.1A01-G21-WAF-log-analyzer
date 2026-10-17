# Juice Shop lab evaluation (tầng B)

Đối chiếu `datasets/ground_truth.csv` với Alert runtime sau một lần gửi đủ request có nhãn.
Không chỉnh rule sau khi xem kết quả.

## A_browse

| | Có alert | Không alert |
|---|---:|---:|
| Nhãn tấn công | TP=0 | FN=0 |
| Nhãn sạch | FP=0 | TN=50 |

Precision=0.000 · Recall/TPR=0.000 · FPR=0.000 · score(TPR−FPR)=0.000

## B_probe

| | Có alert | Không alert |
|---|---:|---:|
| Nhãn tấn công | TP=24 | FN=7 |
| Nhãn sạch | FP=0 | TN=6 |

Precision=1.000 · Recall/TPR=0.774 · FPR=0.000 · score(TPR−FPR)=0.774

## all

| | Có alert | Không alert |
|---|---:|---:|
| Nhãn tấn công | TP=24 | FN=7 |
| Nhãn sạch | FP=0 | TN=56 |

Precision=1.000 · Recall/TPR=0.774 · FPR=0.000 · score(TPR−FPR)=0.774

## Coverage 3 category (probe, expected=alert)

| Category | TP | FN | Recall |
|---|---:|---:|---:|
| SQLI | 5 | 6 | 0.455 |
| XSS | 9 | 0 | 1.000 |
| PATH_TRAVERSAL | 10 | 0 | 1.000 |

## Chi tiết lệch / thiếu event

| id | source | category | expected | cell | event_id | score | note |
|---|---|---|---|---|---|---:|---|
| sqli-time-sleep | probe | SQLI | alert | FN | 102 | 20 | TIME weight 60, MEDIUM |
| sqli-time-pgsleep | probe | SQLI | alert | FN | 103 | 20 | TIME weight 60, MEDIUM |
| sqli-time-benchmark | probe | SQLI | alert | FN | 104 | 20 | TIME weight 60, MEDIUM |
| sqli-stacked-sleep | probe | SQLI | alert | FN | 108 | 20 | STACKED 50 + TIME 60, CRITICAL |
| hard-sqli-mixed-case | probe | SQLI | alert | FN |  |  | mixed-case boolean + union separated by plus |
| hard-sqli-obrien | probe | SQLI | alert | FN |  |  | name-like quote-comment plus union |
| hard-multi | probe | MIXED | alert | FN |  |  | SQLi + XSS + traversal |

## Hạn chế ghi nhận

- Detector chỉ thấy access log (path/query/UA). Payload chỉ nằm trong POST body không có trên log → FN theo thiết kế, không phải rule hỏng.
- Ngưỡng score mặc định 60: một rule nhẹ (<60) trên HTTP 200 chưa đủ tạo Alert.
- Một số probe TIME đơn (`sleep` / `pg_sleep` / `benchmark`) có thể không đạt 60 nếu chuỗi trên log không còn khớp đầy đủ pattern sau decode/normalize.

