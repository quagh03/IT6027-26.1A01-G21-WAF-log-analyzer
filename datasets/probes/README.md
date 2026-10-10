# Attack Scenario Runner

**Ethics:** these scripts only probe the **local** OWASP Juice Shop behind Nginx in this Compose lab (`http://127.0.0.1`, `Host: juice.lab.local` or `shop.lab.local`). Do not run them against remote or third-party sites.

`scenarios.yaml` has 30 labelled requests (8 SQLi, 8 XSS, 8 path traversal expected to alert, 6 clean). `browse_clean.sh` sends **50** benign GETs. Expected labels are copied into `datasets/ground_truth.csv`.

```bash
# Compose lab must be up (nginx on :80)
./datasets/probes/run.sh
./datasets/probes/browse_clean.sh
```

Queries are percent-encoded by `run.py`. Rules match the raw query string, including `%xx` encodings. A single rule under weight 60 does not alert; the attack scenarios combine rules (or use a TIME rule at weight 60) so the score can reach the default threshold.
