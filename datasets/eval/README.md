# Week 5 evaluation harness

| Script | Output | Khi nào |
|--------|--------|---------|
| `score_juice.py` | `juice-lab.md` | Lab up; gửi ground truth rồi chấm Alert |
| `owasp-benchmark/run_eval.py` | `owasp-benchmark/owasp-benchmark.md` | **Lab sạch** (`make reset`) |
| `measure_latency.py` | `latency.md` | Lab up; đo request → alert |

```bash
make reset && make stack-ui
make eval-juice          # hoặc python3 datasets/eval/score_juice.py
make reset && make stack-ui
make eval-benchmark      # cần tải upstream — xem owasp-benchmark/README.md
make eval-latency
```

Không sửa production rule sau khi xem số đo Benchmark / Juice FP.
