#!/usr/bin/env python3
"""Randomly run existing probe scripts to simulate continuous SOC monitoring.

Ethics: local Juice Shop behind Nginx only (127.0.0.1). Ctrl+C / SIGTERM to stop.
"""

from __future__ import annotations

import argparse
import os
import random
import signal
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]


@dataclass(frozen=True)
class Job:
    name: str
    argv: list[str]
    weight: int
    kind: str  # clean | medium | high | critical | mixed


JOBS: list[Job] = [
    Job(
        name="browse_clean",
        argv=["bash", str(ROOT / "browse_clean.sh")],
        weight=40,
        kind="clean",
    ),
    Job(
        name="severity_medium",
        argv=["python3", str(ROOT / "run.py"), "--severity", "MEDIUM"],
        weight=20,
        kind="medium",
    ),
    Job(
        name="severity_high",
        argv=["python3", str(ROOT / "run.py"), "--severity", "HIGH"],
        weight=15,
        kind="high",
    ),
    Job(
        name="severity_critical",
        argv=["python3", str(ROOT / "run.py"), "--severity", "CRITICAL"],
        weight=15,
        kind="critical",
    ),
    Job(
        name="medium_high",
        argv=["bash", str(ROOT / "run_medium_high.sh")],
        weight=7,
        kind="mixed",
    ),
    Job(
        name="all_scenarios",
        argv=["bash", str(ROOT / "run.sh")],
        weight=3,
        kind="mixed",
    ),
]

STOP = False


def _stop(_signum: int, _frame: object) -> None:
    global STOP
    STOP = True
    print("\n[simulate] stop requested — finishing current job…", flush=True)


def pick_job(pool: list[Job]) -> Job:
    weights = [job.weight for job in pool]
    return random.choices(pool, weights=weights, k=1)[0]


def run_job(job: Job, round_no: int) -> int:
    print(
        f"\n[simulate] #{round_no} → {job.name} ({job.kind}) "
        f"{' '.join(job.argv)}",
        flush=True,
    )
    started = time.monotonic()
    try:
        completed = subprocess.run(
            job.argv,
            cwd=str(REPO),
            check=False,
            env=os.environ.copy(),
        )
        code = completed.returncode
    except FileNotFoundError as error:
        print(f"[simulate] ERROR missing command: {error}", flush=True)
        return 127
    elapsed = time.monotonic() - started
    status = "ok" if code == 0 else f"exit={code}"
    print(f"[simulate] #{round_no} ← {job.name} {status} in {elapsed:.1f}s", flush=True)
    return code


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Randomly chain existing probe scripts for monitoring demo",
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=8.0,
        help="Seconds to sleep between jobs (default: 8)",
    )
    parser.add_argument(
        "--jitter",
        type=float,
        default=4.0,
        help="Extra random sleep 0..jitter seconds (default: 4)",
    )
    parser.add_argument(
        "--rounds",
        type=int,
        default=0,
        help="Stop after N jobs (0 = run until Ctrl+C)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help="RNG seed for reproducible order",
    )
    parser.add_argument(
        "--only",
        default="",
        help="Comma kinds to allow: clean,medium,high,critical,mixed",
    )
    parser.add_argument(
        "--once-each",
        action="store_true",
        help="Shuffle available jobs and run each once, then exit",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.seed is not None:
        random.seed(args.seed)

    pool = list(JOBS)
    if args.only:
        wanted = {part.strip().lower() for part in args.only.split(",") if part.strip()}
        pool = [job for job in pool if job.kind in wanted]
    if not pool:
        print("no jobs after --only filter", file=sys.stderr)
        return 1

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)

    print("=== simulate monitoring traffic ===", flush=True)
    print("Ethics: local Nginx/Juice Shop only. Stop with Ctrl+C.", flush=True)
    print(
        f"jobs={[job.name for job in pool]} interval={args.interval}s "
        f"jitter=0..{args.jitter}s rounds={args.rounds or '∞'}",
        flush=True,
    )

    failures = 0
    round_no = 0

    if args.once_each:
        order = pool[:]
        random.shuffle(order)
        for job in order:
            if STOP:
                break
            round_no += 1
            if run_job(job, round_no) != 0:
                failures += 1
            if STOP:
                break
            pause = max(0.0, args.interval + random.uniform(0.0, max(0.0, args.jitter)))
            time.sleep(pause)
        print(f"=== done rounds={round_no} failures={failures} ===", flush=True)
        return 1 if failures else 0

    while not STOP:
        round_no += 1
        job = pick_job(pool)
        if run_job(job, round_no) != 0:
            failures += 1
        if STOP:
            break
        if args.rounds and round_no >= args.rounds:
            break
        pause = max(0.0, args.interval + random.uniform(0.0, max(0.0, args.jitter)))
        print(f"[simulate] sleep {pause:.1f}s …", flush=True)
        # Interruptible sleep
        end = time.monotonic() + pause
        while not STOP and time.monotonic() < end:
            time.sleep(min(0.25, end - time.monotonic()))

    print(f"=== stopped rounds={round_no} failures={failures} ===", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
