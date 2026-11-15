import { mkdirSync } from "node:fs";
import { test } from "@playwright/test";

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
        await page.screenshot({ path: `${OUT}/${name}-${device}.png`, fullPage: true });
      }
    });
  });
}
