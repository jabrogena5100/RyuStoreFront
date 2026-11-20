# Image bytes: originals vs served variants

- Run 2026-11-20 by `scripts/images/variants.sh`: sharp 0.35.5, AVIF quality 50 (effort 4), WebP quality 72, metadata stripped.
- 188 images. Originals as downloaded (`evidence-raw/images/originals`): **50891.2 KB total, 270.7 KB per image**.
- The 960 px AVIF (what a product card or a phone gallery loads at 2× density) is a median **12.7 %** of its original's bytes.

| Variant | Total KB | Mean KB per image |
| --- | --- | --- |
| avif at 480 px | 1915.4 | 10.2 |
| webp at 480 px | 2969.1 | 15.8 |
| avif at 960 px | 6124.3 | 32.6 |
| webp at 960 px | 9862.7 | 52.5 |
| avif at 1440 px | 9652.5 | 51.3 |
| webp at 1440 px | 15780.2 | 83.9 |
