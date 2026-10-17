#!/usr/bin/env python3
"""Score Juice Shop browse + probe traffic against datasets/ground_truth.csv."""

from __future__ import annotations

import argparse
import csv
import sys
import time
from collections import Counter
from pathlib import Path
from urllib.parse import unquote

from common import api_get, confusion, rates, send_get, token, wait_healthy

ROOT = Path(__file__).resolve().parents[1]
GT = ROOT / "ground_truth.csv"
OUT = Path(__file__).resolve().parent / "juice-lab.md"


def load_ground_truth() -> list[dict[str, str]]:
    with GT.open(encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def norm_query(q: str | None) -> str:
    return unquote((q or "").replace("+", " ")).lower().strip()


def query_similar(a: str, b: str) -> bool:
    na, nb = norm_query(a), norm_query(b)
    if not na and not nb:
        return True
    if na == nb:
        return True
    # Nginx may partially decode; accept containment either way when distinctive
    if len(na) >= 6 and (na in nb or nb in na):
        return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--delay", type=float, default=0.12)
    parser.add_argument("--wait-ingest", type=float, default=15.0)
    args = parser.parse_args()

    wait_healthy()
    bearer = token()
    rows = load_ground_truth()
    before_ids = {int(e["id"]) for e in api_get("/api/events", bearer)}

    print(f"=== sending {len(rows)} labelled requests ===", flush=True)
    for row in rows:
        code = send_get(row.get("path") or "/", row.get("query") or "", host=row.get("host") or "juice.lab.local")
        print(f"{row['id']}\thttp={code}", flush=True)
        time.sleep(args.delay)

    print(f"waiting {args.wait_ingest:.0f}s for ingest…", flush=True)
    time.sleep(args.wait_ingest)

    events = [e for e in api_get("/api/events", bearer) if int(e["id"]) not in before_ids]
    alert_ids = {int(a["eventId"]) for a in api_get("/api/alerts", bearer) if a.get("eventId") is not None}
    used: set[int] = set()
    results: list[dict] = []

    for row in rows:
        host = row.get("host") or "juice.lab.local"
        path = row.get("path") or "/"
        query = row.get("query") or ""
        candidates = [
            e
            for e in events
            if int(e["id"]) not in used
            and (e.get("host") or "") == host
            and (e.get("path") or "") == path
            and query_similar(query, e.get("query") or "")
        ]
        # Prefer highest score among candidates (attack rows), else earliest id
        if candidates:
            if (row.get("expected") or "") == "alert":
                ev = max(candidates, key=lambda e: (int(e.get("riskScore") or 0), -int(e["id"])))
            else:
                ev = min(candidates, key=lambda e: int(e["id"]))
            used.add(int(ev["id"]))
        else:
            ev = None

        got = bool(ev and int(ev["id"]) in alert_ids)
        expected_attack = (row.get("expected") or "").strip().lower() == "alert"
        cell = confusion(expected_attack, got)
        results.append(
            {
                **row,
                "event_id": ev["id"] if ev else "",
                "score": ev.get("riskScore", "") if ev else "",
                "got_alert": got,
                "cell": cell,
            }
        )

    sets = {
        "A_browse": [r for r in results if r.get("source") == "browse"],
        "B_probe": [r for r in results if r.get("source") == "probe"],
        "all": results,
    }

    lines = [
        "# Juice Shop lab evaluation (tầng B)",
        "",
        "Đối chiếu `datasets/ground_truth.csv` với Alert runtime sau một lần gửi đủ request có nhãn.",
        "Không chỉnh rule sau khi xem kết quả.",
        "",
    ]
    summary_rows = []
    for name, subset in sets.items():
        c = Counter(r["cell"] for r in subset)
        tp, fp, fn, tn = c["TP"], c["FP"], c["FN"], c["TN"]
        m = rates(tp, fp, fn, tn)
        summary_rows.append((name, tp, fp, fn, tn, m))
        lines += [
            f"## {name}",
            "",
            "| | Có alert | Không alert |",
            "|---|---:|---:|",
            f"| Nhãn tấn công | TP={tp} | FN={fn} |",
            f"| Nhãn sạch | FP={fp} | TN={tn} |",
            "",
            f"Precision={m['precision']:.3f} · Recall/TPR={m['recall']:.3f} · "
            f"FPR={m['fpr']:.3f} · score(TPR−FPR)={m['score']:.3f}",
            "",
        ]

    lines += [
        "## Coverage 3 category (probe, expected=alert)",
        "",
        "| Category | TP | FN | Recall |",
        "|---|---:|---:|---:|",
    ]
    for cat in ("SQLI", "XSS", "PATH_TRAVERSAL"):
        sub = [r for r in sets["B_probe"] if r.get("category") == cat and r["expected"] == "alert"]
        tp = sum(1 for r in sub if r["cell"] == "TP")
        fn = sum(1 for r in sub if r["cell"] == "FN")
        recall = tp / (tp + fn) if (tp + fn) else 0.0
        lines.append(f"| {cat} | {tp} | {fn} | {recall:.3f} |")
    lines += [
        "",
        "## Chi tiết lệch / thiếu event",
        "",
        "| id | source | category | expected | cell | event_id | score | note |",
        "|---|---|---|---|---|---|---:|---|",
    ]
    for r in results:
        if r["cell"] in ("FN", "FP") or not r["event_id"]:
            lines.append(
                f"| {r['id']} | {r.get('source','')} | {r.get('category','')} | {r['expected']} | "
                f"{r['cell']} | {r['event_id']} | {r['score']} | {(r.get('note') or '')[:60]} |"
            )
    lines += [
        "",
        "## Hạn chế ghi nhận",
        "",
        "- Detector chỉ thấy access log (path/query/UA). Payload chỉ nằm trong POST body "
        "không có trên log → FN theo thiết kế, không phải rule hỏng.",
        "- Ngưỡng score mặc định 60: một rule nhẹ (<60) trên HTTP 200 chưa đủ tạo Alert.",
        "- Một số probe TIME đơn (`sleep` / `pg_sleep` / `benchmark`) có thể không đạt 60 "
        "nếu chuỗi trên log không còn khớp đầy đủ pattern sau decode/normalize.",
        "",
    ]
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT}", flush=True)
    for name, tp, fp, fn, tn, m in summary_rows:
        print(
            f"{name}: TP={tp} FP={fp} FN={fn} TN={tn} "
            f"P={m['precision']:.3f} R={m['recall']:.3f} FPR={m['fpr']:.3f}",
            flush=True,
        )
    return 0


if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    raise SystemExit(main())
