import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

// Against the REAL API: these create customers (live-<time>-<n>@example.com) and guest carts in its database.
const PASSWORD = "correct horse battery";
let n = 0;
const email = () => `live-${Date.now()}-${n++}@example.com`;

/** An in-stock product with one available variant: its page URL, size and colour. */
async function buyable(request: APIRequestContext) {
  const list = (await (await request.get("/api/products?inStock=true&pageSize=24")).json()).items as { slug: string }[];
  for (const it of list) {
    const d = await (await request.get(`/api/products/${it.slug}`)).json();
    const v = d.variants.find((x: { available: boolean }) => x.available);
    if (v) return { slug: it.slug as string, name: d.name as string, sku: v.sku as string, size: v.sizeLabel as string, colour: v.colour as string, multi: new Set(d.variants.map((x: { colour: string }) => x.colour)).size > 1 };
  }
  throw new Error("no in-stock product found");
}

async function addToCart(page: Page, p: Awaited<ReturnType<typeof buyable>>) {
  await page.goto(`/products/${p.slug}`);
  if (p.multi) await page.getByRole("radio", { name: new RegExp(`^${p.colour}`, "i") }).check({ force: true });
  await page.getByRole("radio", { name: `Size ${p.size}`, exact: true }).check({ force: true });
  const done = page.waitForResponse((r) => r.url().includes("/api/cart/items/") && r.request().method() === "PUT");
  await page.getByRole("button", { name: "Add to cart" }).click();
  expect((await done).status()).toBe(200);
  await expect(page.getByRole("link", { name: /^Cart/ })).toContainText("1");
}

test("a guest's cart survives a reload (the CART cookie passes through the proxy)", async ({ page, request }) => {
  const p = await buyable(request);
  await addToCart(page, p);
  await page.reload();
  await expect(page.getByRole("link", { name: /^Cart/ })).toContainText("1");
  await page.goto("/cart");
  await expect(page.getByRole("heading", { name: "Summary" })).toBeVisible();
  await expect(page.getByText(p.name).first()).toBeVisible();
});

test("the cart page changes a quantity, updates the totals, and removes a line", async ({ page, request }) => {
  const p = await buyable(request);
  await addToCart(page, p);
  await page.goto("/cart");
  const before = await page.getByRole("complementary", { name: "Order summary" }).innerText();
  const put = page.waitForResponse((r) => r.url().includes("/api/cart/items/") && r.request().method() === "PUT");
  await page.getByRole("button", { name: "Increase quantity" }).click();
  const res = await put;
  if (res.status() === 200) {
    await expect(page.getByRole("link", { name: /^Cart/ })).toContainText("2");
    expect(await page.getByRole("complementary", { name: "Order summary" }).innerText()).not.toBe(before);
  } else {
    expect(res.status()).toBe(409); // only one pair in stock: the API refuses and the page says so
    await expect(page.getByRole("alert").filter({ hasText: /not enough pairs/i })).toBeVisible();
  }
  await page.getByRole("button", { name: /^Remove / }).click();
  await expect(page.getByRole("heading", { name: "Your cart is empty" })).toBeVisible();
});

test("registering signs in, merges the guest cart, and signing out returns to a guest", async ({ page, request }) => {
  const p = await buyable(request);
  await addToCart(page, p); // as a guest
  await page.goto("/register");
  await page.getByLabel("E-mail").fill(email());
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("button", { name: "Sign out" })).toHaveCount(0); // the menu is closed
  await expect(page.locator("details summary")).toContainText(/live-/);
  expect((await page.request.get("/api/auth/me")).status()).toBe(200);
  expect((await (await page.request.get("/session")).json()).customer.email).toMatch(/^live-/);
  await expect(page.getByRole("link", { name: /^Cart/ })).toContainText("1"); // merged into the customer's cart

  await page.locator("details summary").click();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("link", { name: "Sign in" })).toBeVisible();
  expect((await page.request.get("/api/auth/me")).status()).toBe(401);
  expect(await (await page.request.get("/session")).json()).toEqual({ customer: null });
});

test("a guest's page load has no console errors (the session check answers 200, not 401)", async ({ page }) => {
  const errors: string[] = [];
  page.on("console", (m) => m.type() === "error" && errors.push(m.text()));
  const session = page.waitForResponse((r) => new URL(r.url()).pathname === "/session");
  await page.goto("/");
  expect((await session).status()).toBe(200);
  await expect(page.getByRole("link", { name: "Sign in" }).first()).toBeVisible(); // the header shows the guest state
  expect(errors).toEqual([]);
});

test("signing in with a wrong password says so and stays on the page", async ({ page }) => {
  await page.goto("/sign-in");
  await page.getByLabel("E-mail").fill(email());
  await page.getByLabel("Password", { exact: true }).fill("not the password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "do not match" })).toBeVisible();
  await expect(page).toHaveURL(/sign-in/);
});

test("registering with a short password shows the field error from the API", async ({ page }) => {
  await page.goto("/register");
  await page.getByLabel("E-mail").fill(email());
  await page.getByLabel("Password", { exact: true }).fill("short");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByText("Use 10 to 72 characters.").first()).toBeVisible();
});

test("an existing e-mail offers to sign in instead, and a returning customer can sign in", async ({ page, browser }) => {
  const e = email();
  await page.goto("/register");
  await page.getByLabel("E-mail").fill(e);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.locator("details summary")).toBeVisible();

  const other = await (await browser.newContext()).newPage(); // a fresh visitor
  await other.goto("/register");
  await other.getByLabel("E-mail").fill(e);
  await other.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await other.getByRole("button", { name: "Create account" }).click();
  await expect(other.getByText("already exists")).toBeVisible();
  await other.getByRole("link", { name: "Sign in instead" }).click();
  // The link navigates on the client; without this wait the e-mail can be typed into the register form that is
  // about to unmount (seen against the cloud: the sign-in page rendered with an empty e-mail field).
  await other.waitForURL("**/sign-in");
  await other.getByLabel("E-mail").fill(e);
  await other.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await other.getByRole("button", { name: "Sign in" }).click();
  await expect(other.locator("details summary")).toContainText(/live-/);
});
