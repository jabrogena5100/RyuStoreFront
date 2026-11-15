import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";

const AXE_DIR = process.env.AXE_DIR ?? "/workspace/web/test-results/axe";
mkdirSync(AXE_DIR, { recursive: true });

const OUT = process.env.SHOTS_DIR ?? "/workspace/design/screens";
mkdirSync(OUT, { recursive: true });

const DEVICES = {
  desktop: { viewport: { width: 1440, height: 900 } },
  mobile: { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true },
} as const;

const PAGES: { name: string; path: string; act?: (p: Page) => Promise<void> }[] = [
  { name: "home", path: "/" },
  { name: "listing", path: "/products" },
  { name: "listing-night", path: "/products?occasion=night&gender=women" },
  { name: "listing-loading", path: "/products?state=loading" },
  { name: "listing-empty", path: "/products?state=empty" },
  { name: "listing-error", path: "/products?state=error" },
  { name: "pdp", path: "/products/aurel-court-sneaker" },
  { name: "pdp-soldout", path: "/products/alder-slingback" },
  {
    name: "pdp-added",
    path: "/products/vale-carbon-racer",
    act: async (p) => {
      await p.getByRole("radio", { name: /^Size 9$/ }).check({ force: true });
      await p.getByRole("button", { name: /Add to cart/ }).click();
      await p.waitForTimeout(700);
    },
  },
  { name: "cart-filled", path: "/cart?state=filled" },
  { name: "cart-empty", path: "/cart" },
  { name: "cart-loading", path: "/cart?state=loading" },
  { name: "cart-error", path: "/cart?state=error" },
  { name: "design", path: "/design" },
  { name: "not-found", path: "/nope" },
];

const violations: Record<string, unknown> = {};

for (const [device, opts] of Object.entries(DEVICES)) {
  test.describe(device, () => {
    test.use(opts);
    for (const pg of PAGES) {
      test(`${pg.name}`, async ({ page }) => {
        await page.goto(pg.path, { waitUntil: "networkidle" });
        await page.evaluate(() => document.fonts.ready);
        // Let scroll-reveal sections play, so the shot shows the settled page.
        await page.evaluate(async () => {
          for (let y = 0; y < document.body.scrollHeight; y += 500) {
            window.scrollTo(0, y);
            await new Promise((r) => setTimeout(r, 60));
          }
          window.scrollTo(0, 0);
        });
        await page.waitForTimeout(700);
        if (pg.act) await pg.act(page);
        await page.screenshot({ path: `${OUT}/${pg.name}-${device}.png`, fullPage: pg.name !== "pdp-added" });
        const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
        violations[`${pg.name}-${device}`] = results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length, sample: v.nodes[0]?.target }));
        writeFileSync(`${AXE_DIR}/${device}-${pg.name}.json`, JSON.stringify(violations[`${pg.name}-${device}`], null, 1));
        expect.soft(results.violations.length, `axe violations on ${pg.name} (${device})`).toBe(0);
      });
    }
  });
}
