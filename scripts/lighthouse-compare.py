"""Compare two sets of Lighthouse JSON reports made interleaved by scripts/lighthouse-ab.sh.

usage: python3 -I scripts/lighthouse-compare.py <label> <before dir> <after dir> <before sha> <after desc> <machine> <api> <product>
Each dir holds <page>-<formfactor>-<run>.json. Prints Markdown: a side-by-side table of medians (min-max), the median of
the paired per-run differences (run i of after minus run i of before, same page and form factor), how many pairs moved
in each direction, and the console errors Lighthouse recorded. Page weight is transferred bytes (compressed).
"""
import glob
import json
import os
import statistics
import sys
from collections import defaultdict

label, before_dir, after_dir, before_sha, after_desc, machine, api, product = sys.argv[1:9]


def load(src):
    rows = defaultdict(dict)
    meta = None
    errors = defaultdict(set)
    for path in sorted(glob.glob(os.path.join(src, "*.json"))):
        page, ff, run = os.path.basename(path)[:-5].rsplit("-", 2)
        r = json.load(open(path))
        meta = meta or r
        a = r["audits"]
        reqs = a["network-requests"]["details"]["items"]
        size = lambda kind=None: sum(x.get("transferSize", 0) for x in reqs if kind is None or x.get("resourceType") == kind) / 1024
        for item in (a.get("errors-in-console", {}).get("details") or {}).get("items", []):
            errors[page].add((item.get("source", ""), (item.get("description") or "")[:140]))
        rows[(page, ff)][int(run)] = {
            "perf": r["categories"]["performance"]["score"] * 100,
            "a11y": r["categories"]["accessibility"]["score"] * 100,
            "bp": r["categories"]["best-practices"]["score"] * 100,
            "fcp": a["first-contentful-paint"]["numericValue"],
            "lcp": a["largest-contentful-paint"]["numericValue"],
            "tbt": a["total-blocking-time"]["numericValue"],
            "cls": a["cumulative-layout-shift"]["numericValue"],
            "kb": size(),
            "js": size("Script"),
            "img": size("Image"),
            "reqs": len(reqs),
        }
    return rows, meta, errors


before, meta, before_err = load(before_dir)
after, _, after_err = load(after_dir)

FIELDS = [("perf", "Perf", "{:.0f}"), ("a11y", "A11y", "{:.0f}"), ("bp", "BP", "{:.0f}"), ("fcp", "FCP ms", "{:.0f}"),
          ("lcp", "LCP ms", "{:.0f}"), ("tbt", "TBT ms", "{:.0f}"), ("cls", "CLS", "{:.3f}"), ("kb", "Total KB", "{:.1f}"),
          ("js", "JS KB", "{:.1f}"), ("img", "Img KB", "{:.1f}"), ("reqs", "Requests", "{:.0f}")]
# Lower is better for everything except the scores.
HIGHER_BETTER = {"perf", "a11y", "bp"}


def cell(values, fmt):
    med, lo, hi = statistics.median(values), min(values), max(values)
    return fmt.format(med) if fmt.format(lo) == fmt.format(hi) else f"{fmt.format(med)} ({fmt.format(lo)}–{fmt.format(hi)})"


print(f"# Lighthouse A/B: {label}\n")
if meta:
    ua = meta.get("environment", {}).get("hostUserAgent", "")
    chrome = next((p for p in ua.split() if p.startswith(("Chrome/", "HeadlessChrome/"))), "Chrome (version not reported)")
    print(f"- Lighthouse {meta['lighthouseVersion']}, {chrome}, run {meta['fetchTime'][:10]}")
print(f"- **Before:** commit `{before_sha}`. **After:** `{after_desc}`. Both production builds (`next build` + `next start`, ports 3001 and 3000), served at the same time.")
print(f"- Both against the same running API at `{api}` (the real Spring API with the seed profile, Postgres and SeaweedFS in Compose). Product page: `/products/{product}`.")
print(f"- Machine: {machine}. Throttling: Lighthouse defaults (mobile: simulated slow 4G and 4x CPU slowdown; desktop: `--preset=desktop`). Local, not field data.")
print("- Runs interleaved: for each run, page and form factor, before and after back to back, alternating which goes first. Page weight = transferred bytes (compressed).")
print(f"- Raw reports: `evidence-raw/lighthouse/{label}-before/` and `-after/` (git-ignored).\n")

print("## Medians (min–max)\n")
print("| Page | Form factor | Build | Runs | " + " | ".join(f[1] for f in FIELDS) + " |")
print("| --- | --- | --- | --- | " + " | ".join("---" for _ in FIELDS) + " |")
for key in sorted(set(before) | set(after)):
    for name, data in (("before", before), ("after", after)):
        runs = list(data.get(key, {}).values())
        if runs:
            print(f"| {key[0]} | {key[1]} | {name} | {len(runs)} | " + " | ".join(cell([r[k] for r in runs], fmt) for k, _, fmt in FIELDS) + " |")

print("\n## Paired differences (after − before, same run number)\n")
print("Median of the per-pair differences, then how many pairs got better / worse / stayed equal. A direction is only worth")
print("quoting when (nearly) all pairs agree; a split means the runs cannot tell the builds apart.\n")
keys = [("perf", "Perf", "{:+.0f}"), ("lcp", "LCP ms", "{:+.0f}"), ("tbt", "TBT ms", "{:+.0f}"), ("cls", "CLS", "{:+.3f}"),
        ("kb", "Total KB", "{:+.1f}"), ("img", "Img KB", "{:+.1f}"), ("bp", "BP", "{:+.0f}")]
print("| Page | Form factor | Pairs | " + " | ".join(k[1] for k in keys) + " |")
print("| --- | --- | --- | " + " | ".join("---" for _ in keys) + " |")
for key in sorted(set(before) & set(after)):
    runs = sorted(set(before[key]) & set(after[key]))
    out = []
    for k, _, fmt in keys:
        d = [after[key][i][k] - before[key][i][k] for i in runs]
        sign = 1 if k in HIGHER_BETTER else -1
        better = sum(1 for x in d if x * sign > 1e-9)
        worse = sum(1 for x in d if x * sign < -1e-9)
        out.append(f"{fmt.format(statistics.median(d))} ({better}↑ {worse}↓ {len(d) - better - worse}=)")
    print(f"| {key[0]} | {key[1]} | {len(runs)} | " + " | ".join(out) + " |")

print("\n## Console errors Lighthouse recorded (any run)\n")
for name, errs in (("before", before_err), ("after", after_err)):
    if not any(errs.values()):
        print(f"- **{name}:** none")
    for page in sorted(errs):
        for source, desc in sorted(errs[page]):
            print(f"- **{name}**, {page}: `{source}` {desc}")

print("\n## Per run\n")
print("| Page | Form factor | Run | Build | " + " | ".join(f[1] for f in FIELDS) + " |")
print("| --- | --- | --- | --- | " + " | ".join("---" for _ in FIELDS) + " |")
for key in sorted(set(before) | set(after)):
    for i in sorted(set(before.get(key, {})) | set(after.get(key, {}))):
        for name, data in (("before", before), ("after", after)):
            r = data.get(key, {}).get(i)
            if r:
                print(f"| {key[0]} | {key[1]} | {i} | {name} | " + " | ".join(fmt.format(r[k]) for k, _, fmt in FIELDS) + " |")
