import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

// Against the REAL API. Creates customers, carts and pending orders (each holds a pair of stock for ~30 minutes) in its database.
const PASSWORD = "correct horse battery";
let n = 0;
const email = () => `live-co-${Date.now()}-${n++}@example.com`;

async function buyable(request: APIRequestContext) {
  const list = (await (await request.get("/api/products?inStock=true&pageSize=48")).json()).items as { slug: string }[];
  for (const it of list) {
    const d = await (await request.get(`/api/products/${it.slug}`)).json();
    const v = d.variants.find((x: { available: boolean }) => x.available);
    if (v) return { slug: it.slug as string, size: v.sizeLabel as string, colour: v.colour as string, multi: new Set(d.variants.map((x: { colour: string }) => x.colour)).size > 1 };
  }
  throw new Error("no in-stock product");
}

async function addToCart(page: Page, p: Awaited<ReturnType<typeof buyable>>) {
  await page.goto(`/products/${p.slug}`);
  if (p.multi) await page.getByRole("radio", { name: new RegExp(`^${p.colour}`, "i") }).check({ force: true });
  await page.getByRole("radio", { name: `Size ${p.size}`, exact: true }).check({ force: true });
  const done = page.waitForResponse((r) => r.url().includes("/api/cart/items/") && r.request().method() === "PUT");
  await page.getByRole("button", { name: "Add to cart" }).click();
  expect((await done).status()).toBe(200);
}

async function register(page: Page) {
  await page.goto("/register");
  await page.getByLabel("E-mail").fill(email());
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.locator("details summary")).toContainText(/live-/);
}

async function fillAddress(page: Page) {
  await page.getByLabel("Full name").fill("Ada Buyer");
  await page.getByLabel("Address", { exact: true }).fill("1 Main St");
  await page.getByLabel("City").fill("Portland");
  await page.getByLabel("State or region").fill("OR");
  await page.getByLabel("Postal code").fill("97201");
}

test("a guest who presses Place order is asked to sign in, and nothing is sent", async ({ page, request }) => {
  await addToCart(page, await buyable(request));
  await page.goto("/checkout");
  await fillAddress(page);
  let posted = false;
  page.on("request", (r) => r.url().includes("/api/checkout") && (posted = true));
  await page.getByRole("button", { name: "Place order" }).click();
  await expect(page.getByText("Sign in to place your order")).toBeVisible();
  expect(posted).toBe(false);
});

test("an empty form shows the API's field errors and creates no order", async ({ page, request }) => {
  await register(page);
  await addToCart(page, await buyable(request));
  await page.goto("/checkout");
  await page.getByRole("button", { name: "Place order" }).click();
  await expect(page.getByText("Enter the name for the delivery.")).toBeVisible();
  await expect(page.getByText("Enter the street address.")).toBeVisible();
  await expect(page.getByText("Enter the postal code.")).toBeVisible();
  await expect(page).toHaveURL(/\/checkout/);
});

test("placing an order creates a pending order, shows it on the pay step, and hides it from other customers", async ({ page, request, browser }) => {
  await register(page);
  await addToCart(page, await buyable(request));
  await page.goto("/checkout");
  await fillAddress(page);
  const created = page.waitForResponse((r) => r.url().endsWith("/api/checkout") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Place order" }).click();
  const res = await created;
  expect(res.status()).toBe(201);
  const order = await res.json();
  expect(order.status).toBe("pending");
  expect(order.shippingMinor).toBe(800);

  await expect(page).toHaveURL(new RegExp(`/orders/${order.id}/pay`));
  await expect(page.getByRole("heading", { name: "Pay for your order" })).toBeVisible();
  await expect(page.getByText(/is held until/)).toBeVisible();
  const read = await page.request.get(`/api/orders/${order.id}`);
  expect(read.status()).toBe(200);
  expect((await read.json()).items[0].productSlug).toBeTruthy();

  // the confirmation page for an unpaid order says it is still confirming (the webhook, not this page, marks it paid)
  await page.goto(`/orders/${order.id}/confirmation`);
  await expect(page.getByRole("heading", { name: "Confirming your payment" })).toBeVisible();

  // someone else cannot see it
  const other = await (await browser.newContext()).newPage();
  await register(other);
  await other.goto(`/orders/${order.id}`);
  await expect(other.getByText("We could not find that order")).toBeVisible();

  // a guest is asked to sign in
  const guest = await (await browser.newContext()).newPage();
  await guest.goto(`/orders/${order.id}`);
  await expect(guest.getByText("Sign in to see this order")).toBeVisible();
});

test("Pay now either leaves for Stripe or says payments are unavailable, and never charges", async ({ page, request }) => {
  await register(page);
  await addToCart(page, await buyable(request));
  await page.goto("/checkout");
  await fillAddress(page);
  await page.getByRole("button", { name: "Place order" }).click();
  await expect(page.getByRole("heading", { name: "Pay for your order" })).toBeVisible();

  const paid = page.waitForResponse((r) => /\/api\/orders\/[^/]+\/pay$/.test(r.url()) && r.request().method() === "POST");
  await page.getByRole("button", { name: "Pay now" }).click();
  const status = (await paid).status();
  test.info().annotations.push({ type: "pay-now status", description: String(status) });
  if (status === 200) {
    await page.waitForURL(/stripe\.com/, { timeout: 20_000 });
  } else {
    expect([502, 503]).toContain(status);
    await expect(page.getByRole("alert").filter({ hasText: /unavailable|did not accept/i })).toBeVisible();
  }
});
