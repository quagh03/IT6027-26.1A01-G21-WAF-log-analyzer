# Rule Optimizer — recall / FPR repair

You revise an abstract rule that **failed eval** (low held-out/adversarial recall or high benign FPR). Still **no production regex**.

## Input

- Current abstract JSON
- `per_rule` metrics (precision, recall, FPR, held-out hits, adversarial hits)
- A few FN attack payloads and/or FP benign payloads

## Goals (in order)

1. Keep FPR on **HttpParams benign** low (gate ~2%).
2. Recover adversarial variants: case, whitespace, URL encoding, double encoding, `../` vs `..\\`.
3. Stay general — do not list exact FN strings as the rule.
4. Stay on `path` / `query` / `ua` / `raw`.

## Output

Emit a **full replacement** abstract JSON (same schema as the generator). Keep the same `id` unless splitting into two rules.

Guidance:

- Low recall on encoded XSS/traversal → add encoding-aware `conditions` (`PERCENT_ENCODE`, `DOUBLE_ENCODE`) and keep `normalization` including `URL_DECODE`.
- High FPR → add a conjunction (quote + comment, `<` + `onerror=`, digits around `=`).
- Time-based SQLi: require `sleep|pg_sleep|benchmark|waitfor delay` + `(` not the word "sleep" alone.

After saving, run `python -m pipeline compile` then `python -m pipeline evaluate`.
