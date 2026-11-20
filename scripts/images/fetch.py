#!/usr/bin/env python3
"""Step 3 of the image pipeline: download the selected photos and write the manifest.

    python3 -I scripts/images/fetch.py                  # after review: selection.json + candidates.json -> downloads + manifest
    python3 -I scripts/images/fetch.py --from-manifest  # rebuild from zero: download exactly what manifest.json lists, verify SHA-256

No API key is needed (downloads use the public file URLs recorded at search time). Originals go to
evidence-raw/images/originals/ (git-ignored: image files never enter Git).

Writes (build mode):
    content/catalogue/manifest.json             one entry per image: source page, author, licence name and URL, date retrieved,
                                                measured width and height, bytes, SHA-256 (committed)
    src/main/resources/catalogue/catalogue.json the curated catalogue the `seed` profile loads (committed)
    web/src/data/image-credits.json             what the /credits page lists (committed)
"""
import datetime
import hashlib
import json
import re
import struct
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "evidence-raw" / "images"
ORIGINALS = RAW / "originals"
SELECTION = ROOT / "content" / "catalogue" / "selection.json"
MANIFEST = ROOT / "content" / "catalogue" / "manifest.json"
CATALOGUE = ROOT / "src" / "main" / "resources" / "catalogue" / "catalogue.json"
CREDITS = ROOT / "web" / "src" / "data" / "image-credits.json"
UA = "RyuStoreFront-catalogue-script/1.0 (demo shop; downloads selected, licensed photos)"
ALLOWED = {"Pexels License", "Pixabay Content License", "CC0 1.0", "Public domain"}
CATEGORIES = {"sneakers", "chunky-sneakers", "boots", "heels", "loafers", "running", "sandals", "oxfords"}
SLUG = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")


def dimensions(data: bytes):
    """Width and height from the file header (JPEG, PNG, WebP), so the manifest records what was actually downloaded."""
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return struct.unpack(">II", data[16:24])
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        chunk = data[12:16]
        if chunk == b"VP8X":
            return 1 + int.from_bytes(data[24:27], "little"), 1 + int.from_bytes(data[27:30], "little")
        if chunk == b"VP8 ":
            w, h = struct.unpack("<HH", data[26:30])
            return w & 0x3FFF, h & 0x3FFF
        if chunk == b"VP8L":
            b = int.from_bytes(data[21:25], "little")
            return (b & 0x3FFF) + 1, ((b >> 14) & 0x3FFF) + 1
    if data[:2] == b"\xff\xd8":
        i = 2
        while i < len(data):
            if data[i] != 0xFF:
                i += 1
                continue
            marker = data[i + 1]
            if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                h, w = struct.unpack(">HH", data[i + 5:i + 9])
                return w, h
            i += 2 + struct.unpack(">H", data[i + 2:i + 4])[0]
    raise ValueError("unknown image format")


def extension(data: bytes):
    return "png" if data[:4] == b"\x89PNG" else "webp" if data[8:12] == b"WEBP" else "jpg"


def download(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def download_url(c):
    url = c["downloadUrl"]
    # Wikimedia originals can be huge; its thumbnailer serves any width, and 2000 px is enough for 1440 px layouts.
    if c["source"] == "wikimedia" and c["width"] > 2000 and c.get("thumbUrl") and "/400px-" in c["thumbUrl"]:
        url = c["thumbUrl"].replace("/400px-", "/2000px-")
    return url


def fail(msg):
    print(f"error: {msg}", file=sys.stderr)
    sys.exit(1)


def build():
    if not SELECTION.exists():
        fail(f"{SELECTION.relative_to(ROOT)} does not exist yet (it is written after the contact-sheet review)")
    candidates = {c["ref"]: c for c in json.loads((RAW / "candidates.json").read_text())}
    selection = json.loads(SELECTION.read_text())
    ORIGINALS.mkdir(parents=True, exist_ok=True)
    # Earlier downloads, by product and position (keys contain a hash, so they change when the photo does).
    old = {(e["product"], e["position"]): e for e in json.loads(MANIFEST.read_text())["images"]} if MANIFEST.exists() else {}
    today = datetime.date.today().isoformat()
    manifest, products, credits, seen = [], [], [], set()

    for p in selection["products"]:
        if not SLUG.match(p["slug"]) or p["slug"] in seen:
            fail(f"bad or repeated slug {p['slug']!r}")
        if p["category"] not in CATEGORIES or p["gender"] not in ("men", "women", "unisex") or not p["colours"]:
            fail(f"bad category, gender or colours for {p['slug']}")
        seen.add(p["slug"])
        images = []
        for n, photo in enumerate(p["photos"], 1):
            c = candidates.get(photo["ref"]) or fail(f"{photo['ref']} is not in candidates.json")
            if c["licenceName"] not in ALLOWED:
                fail(f"{photo['ref']} has licence {c['licenceName']!r}, which is not allowed")
            prev = old.get((p["slug"], n))
            files = list(ORIGINALS.glob(f"{p['slug']}-{n}.*"))
            if files and prev and hashlib.sha256(files[0].read_bytes()).hexdigest() == prev["sha256"] and prev["ref"] == c["ref"]:
                data = files[0].read_bytes()
                retrieved = prev["retrieved"]
            else:
                data = download(download_url(c))
                retrieved = today
                time.sleep(0.5)
            w, h = dimensions(data)
            sha = hashlib.sha256(data).hexdigest()
            # The key changes when the photo does, so served files can be cached as immutable.
            key = f"products/{p['slug']}-{n}-{sha[:8]}"
            path = ORIGINALS / f"{p['slug']}-{n}.{extension(data)}"
            path.write_bytes(data)
            entry = {
                "key": key, "ref": c["ref"], "product": p["slug"], "position": n, "source": c["source"], "sourceId": c["sourceId"],
                "pageUrl": c["pageUrl"], "author": c["author"], "authorUrl": c.get("authorUrl"),
                "licenceName": c["licenceName"], "licenceUrl": c["licenceUrl"], "retrieved": retrieved,
                "downloadUrl": download_url(c), "file": path.name, "width": w, "height": h, "bytes": len(data),
                "sha256": sha,
            }
            manifest.append(entry)
            images.append({"key": key, "width": w, "height": h, "alt": photo["alt"]})
            credits.append({k: entry[k] for k in ("key", "product", "source", "pageUrl", "author", "authorUrl", "licenceName", "licenceUrl", "retrieved")}
                           | {"productName": p["name"]})
            print(f"  {key}  {w}x{h}  {len(data) / 1024:.0f} KB  {c['source']} / {c['author']}")
        products.append({k: p[k] for k in ("slug", "name", "brand", "category", "gender", "colours", "priceDollars", "description")}
                        | {"images": images})

    MANIFEST.write_text(json.dumps({"version": 1, "about": "One entry per product photo. Image files are not in Git: "
                                    "scripts/images/fetch.py --from-manifest downloads and verifies them.", "images": manifest}, indent=1) + "\n")
    CATALOGUE.parent.mkdir(parents=True, exist_ok=True)
    CATALOGUE.write_text(json.dumps({"version": 1, "products": products}, indent=1) + "\n")
    CREDITS.parent.mkdir(parents=True, exist_ok=True)
    CREDITS.write_text(json.dumps(credits, indent=1) + "\n")
    total = sum(e["bytes"] for e in manifest)
    print(f"{len(products)} products, {len(manifest)} images, {total / 1024 / 1024:.1f} MiB of originals")


def from_manifest():
    entries = json.loads(MANIFEST.read_text())["images"]
    ORIGINALS.mkdir(parents=True, exist_ok=True)
    bad = 0
    for e in entries:
        path = ORIGINALS / e["file"]
        data = path.read_bytes() if path.exists() else download(e["downloadUrl"])
        if hashlib.sha256(data).hexdigest() != e["sha256"]:
            print(f"  MISMATCH {e['key']}: the file at {e['downloadUrl']} changed since {e['retrieved']}", file=sys.stderr)
            bad += 1
            continue
        path.write_bytes(data)
    print(f"{len(entries) - bad} of {len(entries)} images verified")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    from_manifest() if "--from-manifest" in sys.argv else build()
