# Attack Scenario Runner (skeleton — Week 1)

**Ethics:** only probe the **local** lab (OWASP Juice Shop behind Nginx in this Compose stack). Do not run against remote / third-party sites.

## Week 1

```bash
# Compose lab must be up (nginx on :80, backend on :8080)
./datasets/probes/run.sh
./datasets/probes/browse_clean.sh
curl -sS 'http://127.0.0.1:8080/api/events' | head
```

`scenarios.yaml` has 2–3 smoke requests (clean traffic + dual Host for app scope). Full SQLi/XSS/path-traversal scenarios + `ground_truth.csv` come in **Week 4**.
