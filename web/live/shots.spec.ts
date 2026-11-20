import { mkdirSync } from "node:fs";
import { expect, test } from "@playwright/test";

// Screenshots of the pages with REAL API data, for design/REVIEW.md (desktop and phone).
const OUT = process.env.LIVE_SHOTS_DIR ?? "/workspace/design/screens-live";
mkdirSync(OUT, { recursive: true });

for (const [device, opts] of Object.entries({
  desktop: { viewport: { width: 1440, height: 900 } },
  mobile: { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true },
})) {
  test.describe(device, () => {
    test.use(opts);
    test("home, listing, filtered listing, product", async ({ page, request }) => {
      const first = (await (await request.get("/api/products?pageSize=1")).json()).items[0];
      const shots: [string, string][] = [
        ["home", "/"],
        ["listing", "/products"],
        ["listing-filtered", "/products?occasion=night&gender=women&page=2"],
        ["pdp", `/products/${first.slug}`],
      ];
      for (const [name, path] of shots) {
        await page.goto(path, { waitUntil: "networkidle" });
        await page.evaluate(() => document.fonts.ready);
        // Lazy images load only near the viewport (also sideways, in the scroll rows): bring each one into view and wait for it
        // to decode, so the screenshot shows them and a broken file fails the test.
        const broken = await page.evaluate(async () => {
          const bad: string[] = [];
          for (const img of Array.from(document.querySelectorAll<HTMLImageElement>("picture img"))) {
            img.scrollIntoView({ block: "center", inline: "center" });
            try {
              await img.decode();
            } catch {
              bad.push(img.currentSrc || img.src);
            }
          }
          window.scrollTo(0, 0);
          return bad;
        });
        expect(broken, `${name}: images that did not load`).toEqual([]);
        await page.screenshot({ path: `${OUT}/${name}-${device}.png`, fullPage: true });
      }
    });
  });
}
