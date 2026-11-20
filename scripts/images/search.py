#!/usr/bin/env python3
"""Step 1 of the image pipeline: find candidate photos and save small thumbnails for review.

Sources and licences allowed (see design/BRIEF and infra plan): Pexels (Pexels licence), Pixabay (Pixabay licence),
Wikimedia Commons filtered to CC0 or public domain. Nothing else.

    export PEXELS_API_KEY=...   PIXABAY_API_KEY=...      # in your own shell; never committed or printed
    python3 -I scripts/images/search.py

Writes (all git-ignored, under evidence-raw/images/):
    candidates.json        metadata of every candidate (no image bytes)
    thumbs/<ref>.jpg       a small preview per candidate, for the logo and face review
    sheets/sheet-NN.html   contact sheets, 30 numbered thumbnails each
    cache/                 API responses (Pixabay's terms require caching for 24 hours; reused if younger than that)

Limits respected: Pexels 200 requests/hour (this makes about 42), Pixabay 100 requests/minute (about 21, cached 24 h),
Wikimedia about 7. Only thumbnails are downloaded here; full images are fetched later, only for selected photos (fetch.py).
"""
import hashlib
import html
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "evidence-raw" / "images"
QUERIES = json.loads((ROOT / "content" / "catalogue" / "queries.json").read_text())
UA = "RyuStoreFront-catalogue-script/1.0 (demo shop; image search for a portfolio project)"
LICENCES = {
    "pexels": ("Pexels License", "https://www.pexels.com/license/"),
    "pixabay": ("Pixabay Content License", "https://pixabay.com/service/license-summary/"),
    "cc0": ("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/"),
    "pd": ("Public domain", "https://creativecommons.org/publicdomain/mark/1.0/"),
}
MIN_WIDTH = 1200


def get(url, headers=None, cache_key=None, max_age=24 * 3600):
    """GET JSON (or bytes when cache_key is None and the caller wants raw). Cached responses are reused if fresh."""
    if cache_key:
        cached = OUT / "cache" / f"{cache_key}.json"
        if cached.exists() and time.time() - cached.stat().st_mtime < max_age:
            return json.loads(cached.read_text())
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=30) as r:
        body = r.read()
        remaining = r.headers.get("X-Ratelimit-Remaining") or r.headers.get("X-RateLimit-Remaining")
    data = json.loads(body)
    if cache_key:
        (OUT / "cache").mkdir(parents=True, exist_ok=True)
        (OUT / "cache" / f"{cache_key}.json").write_text(json.dumps(data))
    if remaining is not None and int(remaining) < 10:
        print(f"  rate limit nearly used up ({remaining} left); stopping to stay inside it", file=sys.stderr)
        sys.exit(2)
    return data


def key_hash(*parts):
    return hashlib.sha256("|".join(parts).encode()).hexdigest()[:16]


def pexels(candidates):
    key = os.environ.get("PEXELS_API_KEY")
    if not key:
        print("PEXELS_API_KEY is not set: skipping Pexels", file=sys.stderr)
        return
    for cat, phrases in QUERIES["categories"].items():
        for q in phrases:
            for page in (1, 2):
                url = "https://api.pexels.com/v1/search?" + urllib.parse.urlencode({"query": q, "per_page": 80, "page": page})
                data = get(url, {"Authorization": key}, cache_key="pexels-" + key_hash(q, str(page)))
                for p in data.get("photos", []):
                    if p["width"] < MIN_WIDTH:
                        continue
                    add(candidates, {
                        "source": "pexels", "sourceId": str(p["id"]), "category": cat, "query": q,
                        "pageUrl": p["url"], "author": p["photographer"], "authorUrl": p["photographer_url"],
                        "licence": "pexels", "width": p["width"], "height": p["height"], "alt": p.get("alt") or "",
                        "thumbUrl": p["src"]["medium"], "downloadUrl": p["src"]["original"] + "?auto=compress&cs=tinysrgb&w=2000",
                    })
                if not data.get("next_page"):
                    break
            print(f"  pexels  {cat:16s} {q!r}")


def pixabay(candidates):
    key = os.environ.get("PIXABAY_API_KEY")
    if not key:
        print("PIXABAY_API_KEY is not set: skipping Pixabay", file=sys.stderr)
        return
    for cat, phrases in QUERIES["categories"].items():
        for q in phrases:
            params = {"key": key, "q": q, "image_type": "photo", "safesearch": "true", "per_page": 100, "min_width": MIN_WIDTH}
            # The key is only in the request URL; the cache name is a hash of the query, never of the key.
            data = get("https://pixabay.com/api/?" + urllib.parse.urlencode(params), cache_key="pixabay-" + key_hash(q))
            for h in data.get("hits", []):
                add(candidates, {
                    "source": "pixabay", "sourceId": str(h["id"]), "category": cat, "query": q,
                    "pageUrl": h["pageURL"], "author": h["user"], "authorUrl": f"https://pixabay.com/users/{h['user']}-{h['user_id']}/",
                    "licence": "pixabay", "width": h["imageWidth"], "height": h["imageHeight"], "alt": h.get("tags", ""),
                    "thumbUrl": h["webformatURL"], "downloadUrl": h["largeImageURL"],
                })
            print(f"  pixabay {cat:16s} {q!r}")
            time.sleep(0.7)


def strip_tags(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip()


def wikimedia(candidates):
    for q in QUERIES["wikimedia"]:
        params = {
            "action": "query", "format": "json", "generator": "search", "gsrnamespace": 6, "gsrlimit": 50,
            "gsrsearch": f"{q} filetype:bitmap", "prop": "imageinfo", "iiprop": "url|size|extmetadata|mime", "iiurlwidth": 400,
        }
        data = get("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params), cache_key="wikimedia-" + key_hash(q))
        for page in (data.get("query", {}).get("pages", {}) or {}).values():
            info = (page.get("imageinfo") or [{}])[0]
            meta = info.get("extmetadata", {})
            lic = (meta.get("License", {}).get("value") or "").lower()
            short = (meta.get("LicenseShortName", {}).get("value") or "").lower()
            kind = "cc0" if lic == "cc0" or "cc0" in short else "pd" if lic.startswith("pd") or "public domain" in short else None
            if kind is None or info.get("width", 0) < MIN_WIDTH or info.get("mime") not in ("image/jpeg", "image/png", "image/webp"):
                continue  # CC-BY, CC-BY-SA and everything else is rejected here
            add(candidates, {
                "source": "wikimedia", "sourceId": str(page["pageid"]), "category": None, "query": q,
                "pageUrl": info.get("descriptionurl"), "author": strip_tags(meta.get("Artist", {}).get("value")) or "Unknown",
                "authorUrl": None, "licence": kind, "width": info["width"], "height": info["height"],
                "alt": strip_tags(meta.get("ImageDescription", {}).get("value"))[:200], "thumbUrl": info.get("thumburl"),
                "downloadUrl": info.get("url"),
            })
        print(f"  wikimedia {q!r}")
        time.sleep(1)


def add(candidates, c):
    k = f"{c['source']}-{c['sourceId']}"
    if k not in candidates:
        c["licenceName"], c["licenceUrl"] = LICENCES[c["licence"]]
        candidates[k] = c


def thumbs_and_sheets(candidates):
    (OUT / "thumbs").mkdir(parents=True, exist_ok=True)
    (OUT / "sheets").mkdir(parents=True, exist_ok=True)
    items = sorted(candidates.values(), key=lambda c: (c["category"] or "zz", c["source"], int(c["sourceId"])))
    for n, c in enumerate(items, 1):
        c["ref"] = f"C{n:04d}"
        path = OUT / "thumbs" / f"{c['ref']}.jpg"
        if not path.exists() and c.get("thumbUrl"):
            try:
                req = urllib.request.Request(c["thumbUrl"], headers={"User-Agent": UA})
                path.write_bytes(urllib.request.urlopen(req, timeout=30).read())
            except Exception as e:  # a missing preview is not fatal; the candidate just cannot be reviewed
                print(f"  no thumbnail for {c['ref']}: {e}", file=sys.stderr)
            time.sleep(0.1)
    per = 30
    for s in range(0, len(items), per):
        cells = "".join(
            f'<figure><img src="../thumbs/{c["ref"]}.jpg" alt=""><figcaption>{c["ref"]} · {c["source"]} · {html.escape(c["category"] or c["query"])}</figcaption></figure>'
            for c in items[s:s + per]
        )
        (OUT / "sheets" / f"sheet-{s // per + 1:02d}.html").write_text(
            "<!doctype html><meta charset=utf-8><style>body{margin:0;background:#fff;font:12px/1.2 monospace}"
            "main{display:grid;grid-template-columns:repeat(6,1fr);gap:6px;padding:6px}figure{margin:0}"
            "img{width:100%;height:220px;object-fit:contain;background:#eee}figcaption{padding:2px}</style>"
            f"<main>{cells}</main>"
        )
    return items


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    candidates = {}
    pexels(candidates)
    pixabay(candidates)
    wikimedia(candidates)
    items = thumbs_and_sheets(candidates)
    (OUT / "candidates.json").write_text(json.dumps(items, indent=1))
    by = {}
    for c in items:
        by[c["source"]] = by.get(c["source"], 0) + 1
    print(f"{len(items)} candidates {by}; {len(list((OUT / 'sheets').glob('*.html')))} contact sheets in {OUT / 'sheets'}")


if __name__ == "__main__":
    main()
