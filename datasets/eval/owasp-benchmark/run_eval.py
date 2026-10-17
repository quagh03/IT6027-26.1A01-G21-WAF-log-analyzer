#!/usr/bin/env python3
"""
External eval: OWASP Benchmark v1.2 expectedresults against the lab access-log detector.

- Does not seed or retune rules from Benchmark payloads.
- Only scores cases where the attack payload can appear on access-log query/path.
- Cookie / header / POST-body vectors are counted separately (not in TPR/FPR).
- Out-of-scope categories are counted but not scored as FN.
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO_EVAL = ROOT.parent
sys.path.insert(0, str(REPO_EVAL))
from common import api_get, confusion, rates, send_get, token, wait_healthy  # noqa: E402

EXPECTED = ROOT / "expectedresults-1.2.csv"
UPSTREAM = ROOT / "_upstream" / "extract"
JAVA_DIR = UPSTREAM / "src/main/java/org/owasp/benchmark/testcode"
WEBAPP = UPSTREAM / "src/main/webapp"
OUT_MD = ROOT / "owasp-benchmark.md"
OUT_CSV = ROOT / "scored-cases.csv"

IN_SCOPE = {"sqli": "SQLI", "xss": "XSS", "pathtraver": "PATH_TRAVERSAL"}

# Payloads chosen so a hit can reach default alert threshold 60 on this ruleset
# (same families as datasets/probes — not mined from Benchmark).
ATTACK_PAYLOAD = {
    "sqli": "1 OR 1=1 UNION SELECT null",
    "xss": "<script>javascript:alert(1)</script>",
    "pathtraver": "../../etc/passwd%2e%2e%2f",
}


def ensure_expected() -> None:
    if EXPECTED.exists():
        return
    import urllib.request

    url = "https://raw.githubusercontent.com/OWASP-Benchmark/BenchmarkJava/master/expectedresults-1.2.csv"
    EXPECTED.write_bytes(urllib.request.urlopen(url, timeout=60).read())


def classify_vector(java_text: str) -> str:
    if "getCookies" in java_text or "getCookie(" in java_text or "getTheCookie" in java_text:
        return "cookie"
    if "getHeader(" in java_text or "getHeaders(" in java_text:
        return "header"
    if re.search(r"getInputStream|getReader\(", java_text):
        return "body"
    if (
        "getParameter" in java_text
        or "getParameterMap" in java_text
        or "getQueryString" in java_text
        or "getTheParameter" in java_text
    ):
        return "query"
    return "unknown"


def load_cases() -> tuple[list[dict], Counter]:
    ensure_expected()
    if not JAVA_DIR.is_dir():
        raise SystemExit(
            "Missing Benchmark sources under datasets/eval/owasp-benchmark/_upstream/.\n"
            "See README.md in this directory for download commands."
        )

    stats: Counter = Counter()
    cases: list[dict] = []
    with EXPECTED.open(encoding="utf-8") as fh:
        for row in csv.reader(fh):
            if not row or row[0].startswith("#") or row[0] == "test name":
                continue
            name, cat, real = row[0], row[1], row[2].lower() == "true"
            if cat not in IN_SCOPE:
                stats["out_of_scope"] += 1
                continue
            jp = JAVA_DIR / f"{name}.java"
            text = jp.read_text(encoding="utf-8", errors="ignore")
            vector = classify_vector(text)
            if vector == "body":
                stats["body_invisible"] += 1
                continue
            if vector in ("cookie", "header"):
                stats["header_cookie_invisible"] += 1
                continue
            if vector != "query":
                stats["unknown_vector"] += 1
                continue
            stats["log_visible"] += 1
            m = re.search(r'@WebServlet\(value\s*=\s*"([^"]+)"', text)
            path = m.group(1) if m else f"/{cat}-00/{name}"
            cases.append(
                {
                    "name": name,
                    "category": cat,
                    "runtime_category": IN_SCOPE[cat],
                    "real_vuln": real,
                    "path": path,
                    "payload": ATTACK_PAYLOAD[cat],
                }
            )
    return cases, stats


def replay(cases: list[dict], delay: float) -> None:
    print(f"=== replaying {len(cases)} log-visible Benchmark GETs ===")
    for i, case in enumerate(cases, 1):
        query = f"{case['name']}={case['payload']}"
        code = send_get(case["path"], query)
        if i % 50 == 0 or i == len(cases):
            print(f"… {i}/{len(cases)} last_http={code}")
        time.sleep(delay)


def score(cases: list[dict], bearer: str) -> list[dict]:
    alerts = api_get("/api/alerts", bearer)
    alert_event_ids = {int(a["eventId"]) for a in alerts if a.get("eventId") is not None}
    events = api_get("/api/events", bearer)
    by_name: dict[str, list[dict]] = defaultdict(list)
    for ev in events:
        path = ev.get("path") or ""
        query = ev.get("query") or ""
        m = re.search(r"(BenchmarkTest\d+)", path + "?" + query)
        if m:
            by_name[m.group(1)].append(ev)

    used: set[int] = set()
    scored: list[dict] = []
    for case in cases:
        cands = by_name.get(case["name"]) or []
        ev = None
        for candidate in sorted(cands, key=lambda e: e.get("id", 0)):
            eid = int(candidate["id"])
            if eid not in used:
                used.add(eid)
                ev = candidate
                break
        got = bool(ev and int(ev["id"]) in alert_event_ids)
        cell = confusion(case["real_vuln"], got)
        scored.append(
            {
                **case,
                "event_id": ev["id"] if ev else "",
                "risk_score": ev.get("riskScore", "") if ev else "",
                "got_alert": got,
                "cell": cell,
            }
        )
    return scored


def write_report(scored: list[dict], stats: Counter) -> None:
    overall = Counter(r["cell"] for r in scored)
    tp, fp, fn, tn = overall["TP"], overall["FP"], overall["FN"], overall["TN"]
    m = rates(tp, fp, fn, tn)

    lines = [
        "# OWASP Benchmark v1.2 — external eval (access-log detector)",
        "",
        "Nguồn nhãn: `expectedresults-1.2.csv` (OWASP BenchmarkJava).",
        "Replay GET qua Nginx lab (`Host: juice.lab.local`). **Không** dùng payload Benchmark để sinh/retune rule.",
        "",
        "## Phạm vi chấm",
        "",
        "| Bucket | Số | Ghi chú |",
        "|---|---:|---|",
        f"| log_visible (query/path param) | {stats['log_visible']} | Vào mẫu số TP/FP/FN/TN |",
        f"| body_invisible | {stats['body_invisible']} | Không replay như thể log phải thấy |",
        f"| header/cookie (không trên access log schema) | {stats['header_cookie_invisible']} | Đếm riêng, không kéo TPR |",
        f"| unknown_vector | {stats['unknown_vector']} | Không chấm |",
        f"| out_of_scope (cmdi/ldapi/xpathi/crypto/…) | {stats['out_of_scope']} | Không tính FN |",
        "",
        "## Score gộp 3 category (log_visible)",
        "",
        "| | Có alert | Không alert |",
        "|---|---:|---:|",
        f"| Nhãn Benchmark `true` | TP={tp} | FN={fn} |",
        f"| Nhãn Benchmark `false` | FP={fp} | TN={tn} |",
        "",
        f"TPR = {m['tpr']:.4f} · FPR = {m['fpr']:.4f} · **score = TPR − FPR = {m['score']:.4f}**",
        "",
        "## Theo category",
        "",
        "| Category | TP | FP | FN | TN | TPR | FPR | score |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for cat in ("sqli", "xss", "pathtraver"):
        sub = [r for r in scored if r["category"] == cat]
        c = Counter(r["cell"] for r in sub)
        mm = rates(c["TP"], c["FP"], c["FN"], c["TN"])
        lines.append(
            f"| {IN_SCOPE[cat]} | {c['TP']} | {c['FP']} | {c['FN']} | {c['TN']} | "
            f"{mm['tpr']:.4f} | {mm['fpr']:.4f} | {mm['score']:.4f} |"
        )
    lines += [
        "",
        "## So sánh ngắn với tầng A (rule-generator)",
        "",
        "Tầng A (`detection-rule-generator/evaluator/reports/metrics.md`): 14 rule pass cổng "
        "precision≥0,80 / FPR≤0,02; coverage held-out SQLI≈50,5% · XSS≈68,1% · PATH_TRAVERSAL≈48,0%.",
        "Tầng Benchmark này là **external eval** trên runtime alert — **không** trộn một mẫu số với tầng A hay Juice probes.",
        "",
        "## Hạn chế",
        "",
        "- Nhãn Benchmark = *code có lỗ hổng thật?*; detector log = *request có pattern tấn công?*. "
        "Gửi payload tấn công vào case `false` dễ thành FP theo scoring Benchmark dù SOC vẫn muốn thấy attempt.",
        "- Payload cookie/header/body không xuất hiện trên Nginx JSON access log của lab → loại khỏi mẫu số.",
        "- Ngưỡng score 60: một signature nhẹ có thể hit nhưng chưa alert.",
        "",
        f"Chi tiết từng case: `{OUT_CSV.name}`.",
        "",
    ]
    OUT_MD.write_text("\n".join(lines), encoding="utf-8")

    with OUT_CSV.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(
            fh,
            fieldnames=[
                "name",
                "category",
                "runtime_category",
                "real_vuln",
                "path",
                "payload",
                "event_id",
                "risk_score",
                "got_alert",
                "cell",
            ],
        )
        w.writeheader()
        for row in scored:
            w.writerow(row)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-send", action="store_true")
    parser.add_argument("--delay", type=float, default=0.05)
    parser.add_argument("--wait-ingest", type=float, default=20.0)
    parser.add_argument("--limit", type=int, default=0, help="Optional cap for smoke runs")
    args = parser.parse_args()

    cases, stats = load_cases()
    if args.limit and args.limit > 0:
        cases = cases[: args.limit]
        stats["log_visible"] = len(cases)

    wait_healthy()
    bearer = token()
    if not args.skip_send:
        replay(cases, args.delay)
        print(f"waiting {args.wait_ingest:.0f}s for ingest…")
        time.sleep(args.wait_ingest)

    scored = score(cases, bearer)
    write_report(scored, stats)
    c = Counter(r["cell"] for r in scored)
    m = rates(c["TP"], c["FP"], c["FN"], c["TN"])
    print(f"wrote {OUT_MD}")
    print(
        f"TP={c['TP']} FP={c['FP']} FN={c['FN']} TN={c['TN']} "
        f"TPR={m['tpr']:.4f} FPR={m['fpr']:.4f} score={m['score']:.4f}"
    )
    print(
        f"excluded body={stats['body_invisible']} header_cookie={stats['header_cookie_invisible']} "
        f"oos={stats['out_of_scope']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
