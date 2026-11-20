// Step 4 of the image pipeline: responsive variants of every image in content/catalogue/manifest.json.
// For each original: AVIF and WebP at 480, 960 and 1440 px wide (never wider than the original), metadata stripped.
// Output: evidence-raw/images/variants/<key>-<width>.<avif|webp> (git-ignored) and docs/evidence/images-bytes.md.
// Run through scripts/images/variants.sh (Docker, pinned Node and sharp).
import { mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import sharp from "sharp";

const ROOT = new URL("../../", import.meta.url).pathname;
const WIDTHS = [480, 960, 1440]; // keep in step with ImageView.WIDTHS and web/src/components/ProductImage.tsx
const FORMATS = { avif: { quality: 50, effort: 4 }, webp: { quality: 72 } };
const manifest = JSON.parse(readFileSync(join(ROOT, "content/catalogue/manifest.json"), "utf8"));
const outDir = join(ROOT, "evidence-raw/images/variants");

let originalBytes = 0;
const totals = {}; // `${format}-${width}` -> bytes
const per960 = []; // ratio of the 960 px AVIF to its original, per image
for (const e of manifest.images) {
  const input = join(ROOT, "evidence-raw/images/originals", e.file);
  const original = statSync(input).size;
  originalBytes += original;
  for (const w of WIDTHS) {
    const width = Math.min(w, e.width);
    for (const [fmt, opts] of Object.entries(FORMATS)) {
      const out = join(outDir, `${e.key}-${w}.${fmt}`);
      mkdirSync(dirname(out), { recursive: true });
      const info = await sharp(input).rotate().resize({ width, withoutEnlargement: true })[fmt](opts).toFile(out);
      totals[`${fmt}-${w}`] = (totals[`${fmt}-${w}`] ?? 0) + info.size;
      if (fmt === "avif" && w === 960) per960.push(info.size / original);
    }
  }
  process.stdout.write(".");
}

const kb = (b) => (b / 1024).toFixed(1);
const n = manifest.images.length;
const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2; };
const rows = Object.entries(totals).map(([k, b]) => `| ${k.replace("-", " at ")} px | ${kb(b)} | ${kb(b / n)} |`).join("\n");
const md = `# Image bytes: originals vs served variants

- Run ${new Date().toISOString().slice(0, 10)} by \`scripts/images/variants.sh\`: sharp 0.35.5, AVIF quality 50 (effort 4), WebP quality 72, metadata stripped.
- ${n} images. Originals as downloaded (\`evidence-raw/images/originals\`): **${kb(originalBytes)} KB total, ${kb(originalBytes / n)} KB per image**.
- The 960 px AVIF (what a product card or a phone gallery loads at 2× density) is a median **${(median(per960) * 100).toFixed(1)} %** of its original's bytes.

| Variant | Total KB | Mean KB per image |
| --- | --- | --- |
${rows}
`;
writeFileSync(join(ROOT, "docs/evidence/images-bytes.md"), md);
console.log(`\n${n} images: originals ${kb(originalBytes)} KB; 960 px AVIF median ${(median(per960) * 100).toFixed(1)} % of original`);
