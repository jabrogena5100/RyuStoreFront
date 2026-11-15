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
  if (q.colour === "white") out.push({ key: "colour", label: "White and off-white" });
  if (q.stock === "1") out.push({ key: "stock", label: "In stock" });
  return out;
}
