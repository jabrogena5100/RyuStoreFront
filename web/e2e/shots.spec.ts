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

// Fixture order ids (web/src/lib/orders.ts): one pending, one paid, one shipped.
const PENDING = "a2715f79-927b-4833-a8bc-73815e0ea58e";
const PAID = "e91b3c68-0d47-4f2a-8c15-6a7d2b9e0f34";
const SHIPPED = "5c0d7a14-3e21-4b6f-9a58-1f2c8e4d7b90";

const PAGES: { name: string; path: string; only?: "mobile" | "desktop"; act?: (p: Page) => Promise<void> }[] = [
  { name: "home", path: "/" },
  { name: "listing", path: "/products" },
  { name: "listing-night", path: "/products?occasion=night&gender=women" },
  { name: "listing-price", path: "/products?price=u150&stock=1&sort=price-asc" },
  {
    name: "listing-drawer",
    path: "/products?gender=women",
    only: "mobile",
    act: async (p) => {
      await p.getByRole("button", { name: /^Filters/ }).click();
      await p.waitForTimeout(200);
    },
  },
  { name: "listing-loading", path: "/products?state=loading" },
  { name: "listing-empty", path: "/products?state=empty" },
  { name: "listing-error", path: "/products?state=error" },
  { name: "pdp", path: "/products/aurel-court-sneaker" },
  { name: "pdp-colours", path: "/products/aurel-court-sneaker-mk-2" },
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
  { name: "sign-in", path: "/sign-in" },
  { name: "sign-in-invalid", path: "/sign-in?state=invalid&next=checkout" },
  { name: "register", path: "/register" },
  { name: "register-taken", path: "/register?state=taken" },
  { name: "register-submitting", path: "/register?state=submitting" },
  { name: "checkout", path: "/checkout?state=idle" },
  { name: "checkout-errors", path: "/checkout?state=errors" },
  { name: "checkout-submitting", path: "/checkout?state=submitting" },
  { name: "checkout-failed", path: "/checkout?state=failed" },
  { name: "checkout-changed", path: "/checkout?state=changed" },
  { name: "checkout-signedout", path: "/checkout?state=signedout" },
  { name: "checkout-empty", path: "/checkout?state=empty" },
  { name: "pay", path: `/orders/${PENDING}/pay` },
  { name: "pay-starting", path: `/orders/${PENDING}/pay?state=starting` },
  { name: "pay-provider-error", path: `/orders/${PENDING}/pay?state=provider` },
  { name: "pay-expired", path: `/orders/${PENDING}/pay?state=expired` },
  { name: "confirmation", path: `/orders/${PENDING}/confirmation?state=paid` },
  { name: "confirmation-confirming", path: `/orders/${PENDING}/confirmation?state=confirming` },
  { name: "confirmation-cancelled", path: `/orders/${PENDING}/confirmation?state=cancelled` },
  { name: "confirmation-refunded", path: `/orders/${PENDING}/confirmation?state=refunded` },
  { name: "orders", path: "/orders" },
  { name: "orders-empty", path: "/orders?state=empty" },
  { name: "orders-loading", path: "/orders?state=loading" },
  { name: "orders-error", path: "/orders?state=error" },
  { name: "orders-signedout", path: "/orders?state=signedout" },
  { name: "order-shipped", path: `/orders/${SHIPPED}` },
  { name: "order-pending", path: `/orders/${PENDING}` },
  { name: "admin-orders", path: "/admin/orders" },
  { name: "admin-orders-paid", path: "/admin/orders?status=paid" },
  { name: "admin-orders-empty", path: "/admin/orders?state=empty" },
  { name: "admin-orders-error", path: "/admin/orders?state=error" },
  { name: "admin-orders-loading", path: "/admin/orders?state=loading" },
  { name: "admin-orders-forbidden", path: "/admin/orders?state=forbidden" },
  { name: "admin-order-paid", path: `/admin/orders/${PAID}` },
  { name: "admin-order-shipped", path: `/admin/orders/${SHIPPED}` },
  { name: "admin-order-conflict", path: `/admin/orders/${PAID}?state=conflict` },
  {
    name: "admin-order-refund",
    path: `/admin/orders/${PAID}`,
    act: async (p) => {
      await p.getByRole("button", { name: "Refund order" }).click();
      await p.waitForTimeout(200);
    },
  },
  { name: "admin-products", path: "/admin/products" },
  { name: "admin-products-low", path: "/admin/products?stock=low" },
  { name: "admin-product", path: "/admin/products/aurel-court-sneaker" },
  { name: "admin-product-errors", path: "/admin/products/aurel-court-sneaker?state=errors" },
  { name: "admin-product-saved", path: "/admin/products/aurel-court-sneaker?state=saved" },
  { name: "design", path: "/design" },
  { name: "not-found", path: "/nope" },
];

const violations: Record<string, unknown> = {};

for (const [device, opts] of Object.entries(DEVICES)) {
  test.describe(device, () => {
    test.use(opts);
    for (const pg of PAGES.filter((x) => !x.only || x.only === device)) {
      test(`${pg.name}`, async ({ page }) => {
        await page.goto(pg.path, { waitUntil: "networkidle" });
        await page.evaluate(() => document.fonts.ready);
        await page.waitForTimeout(200);
        if (pg.act) await pg.act(page);
        await page.screenshot({ path: `${OUT}/${pg.name}-${device}.png`, fullPage: !["pdp-added", "listing-drawer", "admin-order-refund"].includes(pg.name) });
        const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
        violations[`${pg.name}-${device}`] = results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length, sample: v.nodes[0]?.target }));
        writeFileSync(`${AXE_DIR}/${device}-${pg.name}.json`, JSON.stringify(violations[`${pg.name}-${device}`], null, 1));
        expect.soft(results.violations.length, `axe violations on ${pg.name} (${device})`).toBe(0);
      });
    }
  });
}
