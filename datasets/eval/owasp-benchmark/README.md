# OWASP Benchmark — external eval (Week 5)

Eval ngoài cho detector access-log. **Không** đưa payload Benchmark vào prompt sinh rule; **không** retune weight sau khi xem kết quả.

## Tải nguồn (không commit corpus)

```bash
cd datasets/eval/owasp-benchmark
curl -fsSL \
  'https://raw.githubusercontent.com/OWASP-Benchmark/BenchmarkJava/master/expectedresults-1.2.csv' \
  -o expectedresults-1.2.csv

mkdir -p _upstream && cd _upstream
curl -fsSL \
  'https://codeload.github.com/OWASP-Benchmark/BenchmarkJava/tar.gz/refs/heads/master' \
  -o bench.tgz
mkdir -p extract
tar -xzf bench.tgz -C extract --strip-components=1 \
  'BenchmarkJava-master/src/main/java/org/owasp/benchmark/testcode' \
  'BenchmarkJava-master/src/main/webapp'
```

`_upstream/` và `expectedresults-1.2.csv` nằm trong `.gitignore` (GPL-2.0 upstream).

## Chạy trên lab sạch

```bash
# từ root monorepo — xóa volume Postgres/Kafka/nginx-logs
make reset
make stack-ui          # đợi spring-security-backend healthy

# chỉ Benchmark (khuyến nghị lab trống)
python3 datasets/eval/owasp-benchmark/run_eval.py
```

Artifact: `owasp-benchmark.md`, `scored-cases.csv`.

## Phạm vi chấm

| Bucket | Xử lý |
|--------|--------|
| Category `sqli` / `xss` / `pathtraver` + vector query/param | Replay GET → TP/FP/FN/TN |
| Payload chỉ POST body | `body_invisible` — đếm, không kéo TPR |
| Cookie / header | Không có trên schema access log lab — đếm riêng |
| cmdi, ldapi, xpathi, crypto, hash, … | `out_of_scope` — không tính FN |

Score: `TPR − FPR` theo OWASP Benchmark.
