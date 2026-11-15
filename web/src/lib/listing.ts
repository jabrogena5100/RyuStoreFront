import type { SearchParams } from "@/lib/api";
import { OCCASIONS, PRICE_BANDS, type ListingQuery } from "@/lib/catalog";

/** Build a /products URL from the current filters with some keys changed (undefined removes a key). */
export function listingHref(q: ListingQuery, patch: Partial<ListingQuery> = {}): string {
  const next: Record<string, string | undefined> = { ...q, ...patch };
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(next)) if (v) p.set(k, v);
  const s = p.toString();
  return s ? `/products?${s}` : "/products";
}

export interface ActiveFilter {
  key: keyof ListingQuery;
  label: string;
}

/** The filters currently applied, as pills (sort is not a filter). */
export function activeFilters(q: ListingQuery): ActiveFilter[] {
  const out: ActiveFilter[] = [];
  if (q.q) out.push({ key: "q", label: `Search: ${q.q}` });
  const o = OCCASIONS.find((x) => x.slug === q.occasion);
  if (o) out.push({ key: "occasion", label: o.label });
  if (q.gender) out.push({ key: "gender", label: q.gender === "women" ? "Women" : "Men" });
  const band = PRICE_BANDS.find((b) => b.value === q.price);
  if (band) out.push({ key: "price", label: band.label });
  if (q.size) out.push({ key: "size", label: `US ${q.size}` });
  if (q.stock === "1") out.push({ key: "stock", label: "In stock" });
  return out;
}

export const PAGE_SIZE = 24;

const SORTS = { newest: "newest", "price-asc": "price_asc", "price-desc": "price_desc", relevance: "relevance" } as const;

/** The sort the listing actually uses: what the URL asks for, else best match when searching, else newest. */
export function effectiveSort(q: ListingQuery): keyof typeof SORTS {
  if (q.sort && q.sort in SORTS && (q.sort !== "relevance" || q.q)) return q.sort as keyof typeof SORTS;
  return q.q ? "relevance" : "newest";
}

/** Listing URL parameters to API search parameters. `patch` applies one more option, to count what it would show. */
export function toSearch(q: ListingQuery, patch: Partial<ListingQuery> = {}): SearchParams {
  const n = { ...q, ...patch };
  const occasion = OCCASIONS.find((o) => o.slug === n.occasion);
  const band = PRICE_BANDS.find((b) => b.value === n.price);
  const page = Math.max(1, Number.parseInt(n.page ?? "1", 10) || 1);
  return {
    q: n.q,
    category: occasion ? occasion.categories.join(",") : undefined,
    categoryExact: occasion ? true : undefined,
    gender: n.gender === "women" || n.gender === "men" ? n.gender : undefined,
    minPrice: band && "min" in band ? band.min : undefined,
    maxPrice: band && "max" in band ? band.max : undefined,
    size: n.size,
    inStock: n.stock === "1",
    sort: SORTS[effectiveSort(n)],
    page: page - 1,
    pageSize: PAGE_SIZE,
  };
}
