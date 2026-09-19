# Rule evaluation report

Offline corpus metrics (held-out + HttpParams benign + adversarial).
AI mine offline ≠ LLM detect online.

- FPR gate: `0.02`
- Precision gate: `0.8`
- Passing: **14** (`PT-BACKSLASH-001, PT-DOTDOT-001, PT-DOUBLE-001, PT-ENCODED-001, SQLI-BOOLEAN-001, SQLI-BOOLEAN-002, SQLI-COMMENT-001, SQLI-STACKED-001, SQLI-TIME-001, SQLI-UNION-001, XSS-ENCODED-001, XSS-EVENT-001, XSS-JSURI-001, XSS-SCRIPT-001`)

## Per-rule

| code | category | precision | recall | FPR | held-out hits | adv hits | pass |
|---|---|---:|---:|---:|---:|---:|---|
| PT-BACKSLASH-001 | PATH_TRAVERSAL | 1.0000 | 0.1943 | 0.0000 | 34/175 | 184/808 | True |
| PT-DOTDOT-001 | PATH_TRAVERSAL | 1.0000 | 0.4800 | 0.0000 | 84/175 | 414/808 | True |
| PT-DOUBLE-001 | PATH_TRAVERSAL | 1.0000 | 0.0000 | 0.0000 | 0/175 | 2/808 | True |
| PT-ENCODED-001 | PATH_TRAVERSAL | 1.0000 | 0.0114 | 0.0000 | 2/175 | 165/808 | True |
| SQLI-BOOLEAN-001 | SQLI | 1.0000 | 0.0803 | 0.0000 | 530/6598 | 2123/32851 | True |
| SQLI-BOOLEAN-002 | SQLI | 1.0000 | 0.0388 | 0.0000 | 256/6598 | 1056/32851 | True |
| SQLI-COMMENT-001 | SQLI | 1.0000 | 0.0095 | 0.0000 | 63/6598 | 2381/32851 | True |
| SQLI-STACKED-001 | SQLI | 1.0000 | 0.0771 | 0.0000 | 509/6598 | 2514/32851 | True |
| SQLI-TIME-001 | SQLI | 1.0000 | 0.1666 | 0.0000 | 1099/6598 | 4986/32851 | True |
| SQLI-UNION-001 | SQLI | 1.0000 | 0.1737 | 0.0000 | 1146/6598 | 4609/32851 | True |
| XSS-ENCODED-001 | XSS | 1.0000 | 0.4323 | 0.0000 | 99/229 | 533/1111 | True |
| XSS-EVENT-001 | XSS | 1.0000 | 0.1266 | 0.0000 | 29/229 | 139/1111 | True |
| XSS-JSURI-001 | XSS | 1.0000 | 0.2882 | 0.0000 | 66/229 | 323/1111 | True |
| XSS-SCRIPT-001 | XSS | 1.0000 | 0.3100 | 0.0000 | 71/229 | 354/1111 | True |

## Ruleset coverage by category

- **PATH_TRAVERSAL**: held-out coverage `0.48`, adversarial `0.5136`, benign FPR `0.0`
- **SQLI**: held-out coverage `0.5052`, adversarial `0.4958`, benign FPR `0.0`
- **XSS**: held-out coverage `0.6812`, adversarial `0.6985`, benign FPR `0.0`
