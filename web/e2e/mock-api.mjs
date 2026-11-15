// A stand-in for the Spring API's catalogue endpoints, for design screenshots only (scripts/design-shots.sh).
// It serves the design fixtures in the exact response shapes of GET /api/products, /api/products/{slug} and /api/categories
// (docs: CatalogController, ProductSummary, ProductDetail, PageResult). It is NOT the real API: filtering is a re-implementation.
import { createServer } from "node:http";
import { CATEGORY_NAMES, PRODUCTS } from "../src/lib/catalog.ts";

// 4 copies of the 19 fixture products so the listing paginates (24 per page) like the real 100-product seed.
const ALL = [];
for (let copy = 1; copy <= 4; copy++) {
  for (const p of PRODUCTS) {
    const id = p.id + (copy - 1) * PRODUCTS.length;
    // The second copy has two colours (size x colour variants, as in the real data); the real catalogue has many such products.
    const variants = copy === 2 ? [...p.variants, ...p.variants.map((v) => ({ ...v, sku: `${v.sku}-INK`, colour: "ink", available: !v.available || v.sizeLabel.length > 1 }))] : p.variants;
    ALL.push({ ...p, id, variants, slug: copy === 1 ? p.slug : `${p.slug}-mk-${copy}`, name: copy === 1 ? p.name : `${p.name} Mk ${copy}` });
  }
}

const PARENT = { "chunky-sneakers": "sneakers" };
const summary = (p) => ({
  id: p.id, slug: p.slug, name: p.name, brand: p.brand, gender: p.gender, categorySlug: p.categorySlug,
  categoryName: p.categoryName, minPriceMinor: p.minPriceMinor, currency: p.currency, inStock: p.inStock,
});
const detail = (p) => ({
  id: p.id, slug: p.slug, name: p.name, brand: p.brand, description: `${p.description} ${p.details.join(" ")}`, gender: p.gender,
  categorySlug: p.categorySlug, categoryName: p.categoryName, variants: p.variants,
});

function search(q) {
  const cats = (q.get("category") ?? "").split(",").map((s) => s.trim()).filter(Boolean);
  const text = q.get("q")?.toLowerCase().split(/\s+/).filter(Boolean);
  let list = ALL.filter((p) => {
    const exact = q.get("categoryExact") === "true";
    if (cats.length && !cats.some((c) => p.categorySlug === c || (!exact && PARENT[p.categorySlug] === c))) return false;
    const g = q.get("gender");
    if (g && p.gender !== g && p.gender !== "unisex") return false;
    if (q.get("minPrice") && p.minPriceMinor < Number(q.get("minPrice"))) return false;
    if (q.get("maxPrice") && p.minPriceMinor > Number(q.get("maxPrice"))) return false;
    const size = q.get("size");
    if (size && !p.variants.some((v) => v.sizeLabel === size && v.available)) return false;
    if (q.get("inStock") === "true" && !p.inStock) return false;
    if (text?.length) {
      const hay = `${p.name} ${p.brand} ${p.categoryName} ${p.colour} ${p.material}`.toLowerCase();
      if (!text.every((w) => hay.includes(w))) return false;
    }
    return true;
  });
  const sort = q.get("sort") ?? (text?.length ? "relevance" : "newest");
  if (sort === "price_asc") list = [...list].sort((a, b) => a.minPriceMinor - b.minPriceMinor || a.id - b.id);
  else if (sort === "price_desc") list = [...list].sort((a, b) => b.minPriceMinor - a.minPriceMinor || a.id - b.id);
  else list = [...list].sort((a, b) => b.id - a.id);
  const page = Number(q.get("page") ?? 0);
  const pageSize = Number(q.get("pageSize") ?? 24);
  return { items: list.slice(page * pageSize, (page + 1) * pageSize).map(summary), page, pageSize, total: list.length };
}

createServer((req, res) => {
  const url = new URL(req.url, "http://x");
  const send = (code, body) => {
    res.writeHead(code, { "content-type": "application/json" });
    res.end(JSON.stringify(body));
  };
  // Every page now asks who the visitor is and what is in the cart: a guest with an empty cart.
  if (url.pathname === "/api/auth/me") return send(401, { code: "unauthenticated", message: "Sign in required." });
  if (url.pathname === "/api/auth/csrf") return send(200, { headerName: "X-CSRF-TOKEN", token: "mock" });
  if (url.pathname === "/api/cart" && req.method === "GET") return send(200, { itemCount: 0, subtotalMinor: 0, currency: "USD", lines: [] });
  if (url.pathname === "/api/products") return send(200, search(url.searchParams));
  if (url.pathname === "/api/categories")
    return send(200, Object.entries(CATEGORY_NAMES).map(([slug, name]) => ({ slug, name, parentSlug: PARENT[slug] ?? null })));
  const m = url.pathname.match(/^\/api\/products\/([^/]+)$/);
  if (m) {
    const p = ALL.find((x) => x.slug === m[1]);
    return p ? send(200, detail(p)) : send(404, { code: "not_found", message: "No such product" });
  }
  send(404, { code: "not_found", message: "Not in the mock" });
}).listen(Number(process.env.MOCK_API_PORT ?? 8080), () => console.log(`mock api on ${process.env.MOCK_API_PORT ?? 8080}`));
