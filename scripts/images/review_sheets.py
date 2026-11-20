#!/usr/bin/env python3
"""Review contact sheets for step 2: footwear candidates grouped by category, then by photographer and upload order, so a
photographer's series sits together (one product = one series). Writes evidence-raw/images/review/<category>-NN.html.
Candidates whose tags never mention footwear are left out (Pixabay returns some off-topic results).

    python3 -I scripts/images/review_sheets.py [--per 40] [--refs C0001,C0002 --size large]
"""
import html
import json
import re
import sys
from pathlib import Path

RAW = Path(__file__).resolve().parents[2] / "evidence-raw" / "images"
FOOTWEAR = re.compile(r"shoe|boot|sneaker|trainer|heel|stiletto|pump|loafer|moccasin|sandal|oxford|brogue|derby|footwear|slipper|espadrille|clog|mule")
args = sys.argv[1:]
per = int(args[args.index("--per") + 1]) if "--per" in args else 40
refs = set(args[args.index("--refs") + 1].split(",")) if "--refs" in args else None
large = "--size" in args

cands = json.loads((RAW / "candidates.json").read_text())
if refs:
    cands = [c for c in cands if c["ref"] in refs]
else:
    cands = [c for c in cands if FOOTWEAR.search(c["alt"].lower()) or c["source"] == "wikimedia"]
order = lambda c: (c["category"] or "wikimedia", c["author"], int(c["sourceId"]))
cands.sort(key=order)
out = RAW / ("review-large" if large else "review")
out.mkdir(parents=True, exist_ok=True)
for f in out.glob("*.html"):
    f.unlink()
cols, h = (4, 420) if large else (8, 200)
groups = {}
for c in cands:
    groups.setdefault(c["category"] or "wikimedia", []).append(c)
n = 0
for cat, items in groups.items():
    for s in range(0, len(items), per):
        n += 1
        cells = "".join(
            f'<figure><img src="../thumbs/{c["ref"]}.jpg"><figcaption><b>{c["ref"]}</b> {html.escape(c["author"][:14])} {c["width"]}x{c["height"]}</figcaption></figure>'
            for c in items[s:s + per])
        (out / f"{cat}-{s // per + 1:02d}.html").write_text(
            "<!doctype html><meta charset=utf-8><style>body{margin:0;background:#fff;font:13px/1.1 sans-serif}"
            f"h1{{font-size:16px;margin:6px}}main{{display:grid;grid-template-columns:repeat({cols},1fr);gap:4px;padding:4px}}"
            f"figure{{margin:0}}img{{width:100%;height:{h}px;object-fit:contain;background:#ddd}}figcaption{{padding:1px 2px}}</style>"
            f"<h1>{cat} {s // per + 1}</h1><main>{cells}</main>")
print(f"{len(cands)} candidates on {n} sheets in {out}")
