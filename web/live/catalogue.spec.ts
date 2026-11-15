import { expect, test } from "@playwright/test";

// Run against the real API with its demo data (the `seed` profile). Assertions are on what the real responses must produce,
// not on exact products: the seed is random-looking but deterministic, and these checks do not depend on specific names.

test("home shows occasion tiles with real counts and product rows", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Shop by occasion" })).toBeVisible();
  await expect(page.getByRole("list", { name: "New in" }).getByRole("listitem").first()).toBeVisible();
  const tile = page.getByRole("region", { name: "Shop by occasion" }).getByRole("link", { name: /^Boots/ });
  await expect(tile).toContainText(/\d+ styles/);
});

test("listing paginates, filters and counts through the /api proxy", async ({ page, request }) => {
  const direct = await (await request.get("/api/products?pageSize=1")).json();
  expect(direct.total).toBeGreaterThan(24); // the proxy reaches the API and the seed is loaded

  await page.goto("/products");
  await expect(page.getByText(`${direct.total} pairs`, { exact: true })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Pages" })).toBeVisible();

  await page.getByRole("link", { name: /^Boots/ }).first().click();
  await expect(page).toHaveURL(/occasion=boots/);
  const pairs = await page.getByRole("status").filter({ hasText: /pairs?$/ }).first().innerText();
  const boots = await (await request.get("/api/products?category=boots&pageSize=1")).json();
  expect(pairs).toContain(String(boots.total));
});

test("a product page shows real variants and sizes", async ({ page, request }) => {
  const first = (await (await request.get("/api/products?pageSize=1")).json()).items[0];
  await page.goto(`/products/${first.slug}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(first.name);
  await expect(page.getByRole("radio").first()).toBeAttached();
  await expect(page.getByRole("button", { name: /Add to cart|Sold out/ })).toBeVisible();
});

test("a multi-colour product shows one size grid per colour, with no repeated sizes", async ({ page, request }) => {
  const items = (await (await request.get("/api/products?pageSize=48")).json()).items as { slug: string }[];
  let multi: { slug: string; variants: { colour: string; sizeLabel: string }[] } | undefined;
  for (const it of items) {
    const d = await (await request.get(`/api/products/${it.slug}`)).json();
    if (new Set(d.variants.map((v: { colour: string }) => v.colour)).size > 1) {
      multi = d;
      break;
    }
  }
  test.skip(!multi, "no multi-colour product among the first 48 (the seed normally has them)");
  await page.goto(`/products/${multi!.slug}`);
  await expect(page.getByRole("group", { name: /^Colour/ })).toBeVisible();
  const sizes = await page.getByRole("group", { name: "Size (US)" }).getByRole("radio").evaluateAll((els) => els.map((e) => (e as HTMLInputElement).getAttribute("aria-label")));
  expect(new Set(sizes).size).toBe(sizes.length); // each size appears once for the chosen colour
});

test("an unknown product is a 404 page, not a crash", async ({ page }) => {
  const res = await page.goto("/products/no-such-shoe-anywhere");
  expect(res?.status()).toBe(404);
});

test("the quick finder asks the API for a live count", async ({ page }) => {
  await page.goto("/");
  const waitCount = page.waitForResponse((r) => r.url().includes("/api/products") && r.url().includes("gender=women"));
  await page.getByRole("button", { name: "Women" }).click();
  expect((await waitCount).ok()).toBe(true);
});
