"""Summarise Lighthouse JSON reports: per-run values, then median and range per page and form factor.

usage: python3 -I scripts/lighthouse-summary.py <dir of <page>-<formfactor>-<run>.json> <label> <machine>
Page weight is the sum of transferred bytes from the network-requests audit (what crossed the wire, compressed).
"""
import glob
import json
import os
import statistics
import sys
from collections import defaultdict

src, label, machine = sys.argv[1], sys.argv[2], sys.argv[3]
rows = defaultdict(list)
meta = None

for path in sorted(glob.glob(os.path.join(src, "*.json"))):
    name = os.path.basename(path)[:-5]
    page, ff, run = name.rsplit("-", 2)
    r = json.load(open(path))
    meta = meta or r
    a = r["audits"]
    reqs = a["network-requests"]["details"]["items"]
    total = sum(x.get("transferSize", 0) for x in reqs)
    js = sum(x.get("transferSize", 0) for x in reqs if x.get("resourceType") == "Script")
    img = sum(x.get("transferSize", 0) for x in reqs if x.get("resourceType") == "Image")
    rows[(page, ff)].append({
        "run": int(run),
        "perf": round(r["categories"]["performance"]["score"] * 100),
        "a11y": round(r["categories"]["accessibility"]["score"] * 100),
        "bp": round(r["categories"]["best-practices"]["score"] * 100),
        "seo": round(r["categories"]["seo"]["score"] * 100),
        "fcp": a["first-contentful-paint"]["numericValue"],
        "lcp": a["largest-contentful-paint"]["numericValue"],
        "tbt": a["total-blocking-time"]["numericValue"],
        "cls": a["cumulative-layout-shift"]["numericValue"],
        "kb": total / 1024,
        "js": js / 1024,
        "img": img / 1024,
        "reqs": len(reqs),
    })

FIELDS = [("perf", "Perf", "{:.0f}"), ("a11y", "A11y", "{:.0f}"), ("bp", "BP", "{:.0f}"), ("seo", "SEO", "{:.0f}"),
          ("fcp", "FCP ms", "{:.0f}"), ("lcp", "LCP ms", "{:.0f}"), ("tbt", "TBT ms", "{:.0f}"), ("cls", "CLS", "{:.3f}"),
          ("kb", "Total KB", "{:.1f}"), ("js", "JS KB", "{:.1f}"), ("img", "Img KB", "{:.1f}"), ("reqs", "Requests", "{:.0f}")]


def cell(values, fmt):
    med = statistics.median(values)
    lo, hi = min(values), max(values)
    return fmt.format(med) if lo == hi else f"{fmt.format(med)} ({fmt.format(lo)}–{fmt.format(hi)})"


print(f"# Lighthouse: {label}\n")
if meta:
    ua = meta.get("environment", {}).get("hostUserAgent", "")
    chrome = next((p for p in ua.split() if p.startswith(("Chrome/", "HeadlessChrome/"))), "Chrome (version not reported)")
    print(f"- Lighthouse {meta['lighthouseVersion']}, {chrome}, run {meta['fetchTime'][:10]}")
print(f"- Machine: {machine}. Production build (`next build` + `next start`) on localhost against the mock API (`web/e2e/mock-api.mjs`).")
print("- Throttling: Lighthouse defaults (mobile: simulated slow 4G and 4x CPU slowdown; desktop: `--preset=desktop`). Local, not field data.")
print("- Each cell: median (min–max) over the runs listed. Page weight = transferred bytes of all requests (compressed).")
print("- Raw reports: `evidence-raw/lighthouse/" + label + "/` (git-ignored, regenerate with `scripts/lighthouse.sh " + label + "`).\n")
print("| Page | Form factor | Runs | " + " | ".join(f[1] for f in FIELDS) + " |")
print("| --- | --- | --- | " + " | ".join("---" for _ in FIELDS) + " |")
for (page, ff), runs in sorted(rows.items()):
    print(f"| {page} | {ff} | {len(runs)} | " + " | ".join(cell([r[k] for r in runs], fmt) for k, _, fmt in FIELDS) + " |")

print("\n## Per run\n")
print("| Page | Form factor | Run | " + " | ".join(f[1] for f in FIELDS) + " |")
print("| --- | --- | --- | " + " | ".join("---" for _ in FIELDS) + " |")
for (page, ff), runs in sorted(rows.items()):
    for r in sorted(runs, key=lambda x: x["run"]):
        print(f"| {page} | {ff} | {r['run']} | " + " | ".join(fmt.format(r[k]) for k, _, fmt in FIELDS) + " |")
