#!/usr/bin/env python3
"""Retrieval eval: does /search put a page that answers the question in its top k?

    python3 eval/run_eval.py                       # server at http://localhost:8080
    python3 eval/run_eval.py --url http://host:8080 --out eval/results.md

Questions: eval/questions.jsonl — the 30 real CMKL questions from Athena's Week 3 "LLM Wiki" test
(plus 6 Thai rewrites), each labelled with the page ids ("gold") that answer it. A question counts
as a hit@k if ANY gold page is among the first k results. MRR = mean of 1/rank of the first gold page.
Standard library only.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
KS = (1, 3, 5, 10)
MODES = ("lexical", "semantic", "hybrid")


def search(url: str, query: str, mode: str, programme: str | None, k: int) -> list[str]:
    params = {"q": query, "mode": mode, "k": str(k)}
    if programme:
        params["programme"] = programme
    with urllib.request.urlopen(f"{url}/search?{urllib.parse.urlencode(params)}", timeout=60) as r:
        return [p["sectionId"] for p in json.load(r)["results"]]


def score(questions, url, mode, use_programme):
    rows, latencies = [], []
    for q in questions:
        started = time.perf_counter()
        ids = search(url, q["question"], mode, q["programme"] if use_programme else None, max(KS))
        latencies.append(time.perf_counter() - started)
        rank = next((i + 1 for i, sid in enumerate(ids) if sid in q["gold"]), None)
        rows.append({"id": q["id"], "lang": q["lang"], "rank": rank, "top": ids[:3]})
    return rows, latencies


def summary(rows):
    n = len(rows)
    hits = {k: sum(1 for r in rows if r["rank"] and r["rank"] <= k) / n for k in KS}
    mrr = sum(1 / r["rank"] for r in rows if r["rank"]) / n
    return n, hits, mrr


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", default="http://localhost:8080")
    ap.add_argument("--questions", type=Path, default=HERE / "questions.jsonl")
    ap.add_argument("--out", type=Path, default=None, help="write a markdown report here")
    args = ap.parse_args()

    questions = [json.loads(line) for line in args.questions.read_text(encoding="utf-8").splitlines() if line.strip()]
    scored = [q for q in questions if q["gold"]]
    skipped = [q["id"] for q in questions if not q["gold"]]

    lines = [f"Questions: {len(scored)} scored ({sum(q['lang'] == 'en' for q in scored)} English, "
             f"{sum(q['lang'] == 'th' for q in scored)} Thai); skipped (answer not in the KB): {', '.join(skipped) or 'none'}", ""]
    table = ["| Mode | Programme filter | Subset | n | hit@1 | hit@3 | hit@5 | hit@10 | MRR@10 | p50 latency |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    misses = {}
    for use_programme in (False, True):
        for mode in MODES:
            rows, lat = score(scored, args.url, mode, use_programme)
            lat.sort()
            for subset, sub_rows in (("all", rows), ("en", [r for r in rows if r["lang"] == "en"]),
                                     ("th", [r for r in rows if r["lang"] == "th"])):
                n, hits, mrr = summary(sub_rows)
                table.append(f"| {mode} | {'yes' if use_programme else 'no'} | {subset} | {n} | "
                             + " | ".join(f"{hits[k]:.0%}" for k in KS)
                             + f" | {mrr:.2f} | {lat[len(lat) // 2] * 1000:.0f} ms |")
            if mode == "hybrid":
                misses[use_programme] = [r for r in rows if not r["rank"] or r["rank"] > 5]
    lines += table
    for use_programme, rows in misses.items():
        lines += ["", f"Hybrid misses at k=5 ({'with' if use_programme else 'without'} programme filter): "
                  + (", ".join(f"{r['id']} (rank {r['rank'] or '>10'})" for r in rows) or "none")]
    report = "\n".join(lines)
    print(report)
    if args.out:
        args.out.write_text(report + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
