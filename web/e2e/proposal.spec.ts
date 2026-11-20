import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";

// Design proposal: the redesigned key pages at phone, tablet and desktop width, against the mock API.
// Run with: SPEC=e2e/proposal.spec.ts scripts/design-shots.sh   (SHOTS_OUT=design/build to write elsewhere)
const OUT = `/workspace/${process.env.SHOTS_OUT ?? "design/proposal"}`;
mkdirSync(OUT, { recursive: true });

const WIDTHS = [
  { name: "390", viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true },
  { name: "820", viewport: { width: 820, height: 1180 }, isMobile: true, hasTouch: true },
  { name: "1440", viewport: { width: 1440, height: 900 } },
] as const;

const PAGES: { name: string; path: string; act?: (p: Page) => Promise<void>; full?: boolean }[] = [
  { name: "home", path: "/" },
  { name: "listing", path: "/products" },
  { name: "product", path: "/products/aurel-court-sneaker-mk-2" },
  { name: "sign-in", path: "/sign-in" },
  { name: "register", path: "/register" },
  {
    name: "cart-drawer",
    path: "/products/aurel-court-sneaker",
    full: false,
    act: async (p) => {
      await p.getByRole("radio", { name: "Size 8", exact: true }).check({ force: true });
      await p.getByRole("button", { name: "Add to cart" }).click();
      await p.getByRole("dialog", { name: /1 pair/ }).waitFor();
      await p.waitForTimeout(400);
    },
  },
  {
    name: "search",
    path: "/",
    full: false,
    act: async (p) => {
      await p.keyboard.press("Control+k");
      await p.getByRole("dialog", { name: "Search the shop" }).getByRole("combobox").fill("boot");
      await p.getByRole("option", { name: /Boot/ }).first().waitFor();
      await p.waitForTimeout(400);
    },
  },
  { name: "checkout", path: "/checkout" },
];

for (const w of WIDTHS) {
  test.describe(w.name, () => {
    test.use({ viewport: w.viewport, ...("isMobile" in w ? { isMobile: w.isMobile, hasTouch: w.hasTouch } : {}) });
    for (const pg of PAGES) {
      test(pg.name, async ({ page }) => {
        await page.goto(pg.path, { waitUntil: "networkidle" });
        await page.evaluate(() => document.fonts.ready);
        if (pg.act) await pg.act(page);
        await page.screenshot({ path: `${OUT}/${pg.name}-${w.name}.png`, fullPage: pg.full !== false });
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
        const result = { overflowPx: overflow, axe: axe.violations.map((v) => ({ id: v.id, nodes: v.nodes.length, sample: v.nodes[0]?.target })) };
        writeFileSync(`${OUT}/check-${pg.name}-${w.name}.json`, JSON.stringify(result));
        expect.soft(overflow, `horizontal overflow on ${pg.name} at ${w.name}`).toBe(0);
        expect.soft(axe.violations.length, `axe violations on ${pg.name} at ${w.name}`).toBe(0);
      });
    }
  });
}
