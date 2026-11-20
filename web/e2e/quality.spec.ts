import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

// Quality gates for the redesign (phase 3), run against the production build and the mock API by scripts/design-shots.sh:
// axe with 0 violations and no horizontal overflow at 320 px, a visible keyboard focus ring, motion inside the 120-240 ms
// budget and removed under prefers-reduced-motion, and the cart drawer and search palette by keyboard.

const ROUTES = [
  "/",
  "/products",
  "/products?gender=women&occasion=night",
  "/products/aurel-court-sneaker-mk-2",
  "/cart",
  "/checkout",
  "/sign-in",
  "/register",
  "/orders",
  "/admin/orders",
  "/design",
  "/credits",
];

const AXE_TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

test.describe("320 px", () => {
  test.use({ viewport: { width: 320, height: 640 }, isMobile: true, hasTouch: true });
  for (const path of ROUTES) {
    test(`no horizontal overflow and no axe violations: ${path}`, async ({ page }) => {
      await page.goto(path, { waitUntil: "networkidle" });
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, "px of horizontal overflow").toBe(0);
      const axe = await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze();
      expect(axe.violations.map((v) => `${v.id} (${v.nodes.length})`)).toEqual([]);
    });
  }
});

/** Every element's longest transition or animation, in ms. */
async function longestMotionMs(page: Page) {
  return page.evaluate(() => {
    const ms = (s: string) => Math.max(...s.split(",").map((x) => (x.trim().endsWith("ms") ? parseFloat(x) : parseFloat(x) * 1000)));
    let max = 0;
    let where = "";
    for (const el of Array.from(document.querySelectorAll("*"))) {
      const cs = getComputedStyle(el);
      for (const d of [cs.transitionDuration, cs.animationDuration]) {
        const v = ms(d);
        if (v > max) ((max = v), (where = `${el.tagName.toLowerCase()}.${String(el.className).slice(0, 60)}`));
      }
    }
    return { max, where };
  });
}

test.describe("motion", () => {
  test.use({ viewport: { width: 1440, height: 900 } });
  for (const path of ["/", "/products", "/products/aurel-court-sneaker-mk-2", "/sign-in"]) {
    test(`stays within 240 ms, and is removed under reduced motion: ${path}`, async ({ page }) => {
      await page.goto(path, { waitUntil: "networkidle" });
      const normal = await longestMotionMs(page);
      expect(normal.max, `longest motion (${normal.where})`).toBeLessThanOrEqual(240);
      await page.emulateMedia({ reducedMotion: "reduce" });
      const reduced = await longestMotionMs(page);
      expect(reduced.max, `longest motion under reduced motion (${reduced.where})`).toBeLessThanOrEqual(1);
    });
  }
});

test.describe("keyboard", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test("every element reached by Tab on the home page shows a focus ring of at least 2 px", async ({ page }) => {
    await page.goto("/", { waitUntil: "networkidle" });
    const missing: string[] = [];
    for (let i = 0; i < 25; i++) {
      await page.keyboard.press("Tab");
      const f = await page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        if (!el || el === document.body) return null;
        const cs = getComputedStyle(el);
        return { name: (el.getAttribute("aria-label") ?? el.textContent ?? el.tagName).trim().slice(0, 40), style: cs.outlineStyle, width: parseFloat(cs.outlineWidth) };
      });
      if (f && (f.style === "none" || f.width < 2)) missing.push(f.name);
    }
    expect(missing, "focused elements without a visible outline").toEqual([]);
  });

  test("add to cart opens the cart drawer; focus moves in, Escape closes it and focus returns", async ({ page }) => {
    await page.goto("/products/aurel-court-sneaker", { waitUntil: "networkidle" });
    await page.getByRole("radio", { name: "Size 8", exact: true }).check({ force: true });
    const add = page.getByRole("button", { name: "Add to cart" });
    await add.click();
    const drawer = page.getByRole("dialog", { name: /1 pair/ });
    await expect(drawer).toBeVisible();
    await expect(drawer.getByText("Aurel Court Sneaker")).toBeVisible();
    expect(await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')))).toBe(true);
    // Tab stays inside the drawer.
    for (let i = 0; i < 6; i++) await page.keyboard.press("Tab");
    expect(await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')))).toBe(true);
    await page.keyboard.press("Escape");
    await expect(drawer).toBeHidden();
    await expect(page.getByRole("button", { name: /Add|Added/ })).toBeFocused();
  });

  test("Ctrl+K opens the search palette; typing narrows it, arrows move, Escape closes", async ({ page }) => {
    await page.goto("/", { waitUntil: "networkidle" });
    await page.keyboard.press("Control+k");
    const palette = page.getByRole("dialog", { name: "Search the shop" });
    await expect(palette).toBeVisible();
    const box = palette.getByRole("combobox");
    await expect(box).toBeFocused();
    await box.fill("boot");
    await expect(palette.getByRole("option", { name: /Boot/ }).first()).toBeVisible();
    await page.keyboard.press("ArrowDown");
    await expect(box).toHaveAttribute("aria-activedescendant", /.+/);
    const axe = await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze();
    expect(axe.violations.map((v) => `${v.id} (${v.nodes.length})`)).toEqual([]);
    await page.keyboard.press("Escape");
    await expect(palette).toBeHidden();
  });
});
