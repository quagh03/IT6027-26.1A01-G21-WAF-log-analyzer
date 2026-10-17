# Attack Scenario Runner

**Ethics:** these scripts only probe the **local** OWASP Juice Shop behind Nginx in this Compose lab (`http://127.0.0.1`, `Host: juice.lab.local` or `shop.lab.local`). Do not run them against remote or third-party sites.

## Severity bands (runtime)

Alert opens when `score ≥` app threshold (**60**). Severity from score:

| Band | Score | How probes reach it |
|------|------:|---------------------|
| **MEDIUM** | 60–69 | One TIME rule (weight 60), or UNION (55) + HTTP 404 bonus (+5) |
| **HIGH** | 70–84 | BOOLEAN (40) + COMMENT (35) = 75; EVENT (45) + COMMENT (35) = 80 |
| **CRITICAL** | ≥85 | Typical multi-hit payloads (BOOLEAN+UNION, SCRIPT+JSURI, …) |

Most older Week-4 probes combine several rules → **CRITICAL**. That is expected, not a bug.

`run.py` keeps `()` unencoded so `sleep(5)` still matches `SQLI-TIME-001` on the raw access-log query.

## Commands

```bash
# Compose lab must be up (nginx on :80)
./datasets/probes/run.sh                      # all scenarios
./datasets/probes/run_medium_high.sh          # only MEDIUM + HIGH (demo)
python3 datasets/probes/run.py --severity MEDIUM
python3 datasets/probes/run.py --severity HIGH
python3 datasets/probes/run.py --severity CRITICAL
./datasets/probes/browse_clean.sh             # 50 benign GETs

# Continuous monitoring simulation (random job chain, Ctrl+C to stop)
./datasets/probes/simulate_monitoring.sh
./datasets/probes/simulate_monitoring.sh --rounds 8 --interval 5
./datasets/probes/simulate_monitoring.sh --once-each
./datasets/probes/simulate_monitoring.sh --only clean,medium,high
```

`simulate_monitoring` randomly picks among: `browse_clean`, `--severity MEDIUM|HIGH|CRITICAL`,
`run_medium_high`, and full `run.sh` (weighted toward clean traffic).

Current `scenarios.yaml`: **41** labelled probes (**35** `expected=alert`, **6** clean) plus browse ground truth in `datasets/ground_truth.csv`.

| expected_severity | Count (alert) |
|-------------------|--------------:|
| MEDIUM | 5 |
| HIGH | 3 |
| CRITICAL | 27 |
